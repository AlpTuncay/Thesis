package thesis.demo.vmmrapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.widget.AdapterView;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import thesis.demo.vmmrapp.customview.OverlayView;
import thesis.demo.vmmrapp.customview.OverlayView.DrawCallback;
import thesis.demo.vmmrapp.tflite.Detector;
import thesis.demo.vmmrapp.tflite.Model;
import thesis.demo.vmmrapp.tflite.Recognition;
import thesis.demo.vmmrapp.tflite.SSDMobilenetDetector;
import thesis.demo.vmmrapp.tflite.Device;
import thesis.demo.vmmrapp.tflite.VMMRNet;
import thesis.demo.vmmrapp.utils.BorderedText;
import thesis.demo.vmmrapp.tracker.MultiBoxTracker;
import thesis.demo.vmmrapp.utils.ImageUtils;
import thesis.demo.vmmrapp.utils.Logger;

public class ModelsActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static int DETECTOR_INPUT_SIZE;
    private static int RECOGNIZER_INPUT_SIZE;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(480, 640);
    private static final float TEXT_SIZE_DIP = 10;
    private static final int NUM_THREADS = 4;

    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private SSDMobilenetDetector carDetector;
    private VMMRNet makeAndModelRecognizer;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmapForDetection = null;
    private Bitmap croppedBitmapForVMMR = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmapForDetection);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Recognition> results = carDetector.runInferenceOnFrame(croppedBitmapForDetection);
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmapForDetection);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        final List<Recognition> mappedRecognitions = new LinkedList<>();

                        for (final Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

                                int x = Math.max((int) location.left, 0);
                                int y = Math.max((int) location.top, 0);
                                int width = Math.min((int) location.width(), Math.abs(rgbFrameBitmap.getWidth() - x));
                                int height = Math.min((int) location.height(), Math.abs(rgbFrameBitmap.getHeight() - y));
                                croppedBitmapForVMMR = Bitmap.createBitmap(rgbFrameBitmap, x, y, width, height);
                                try{
                                    List<Recognition> mmr = makeAndModelRecognizer.runInferenceOnDetections(croppedBitmapForVMMR, result);
                                    result.setTitle(mmr.get(0).getTitle());
                                    result.setConfidenceScore(mmr.get(0).getConfidence());
                                } catch(IllegalArgumentException e){
                                    LOGGER.e(e, "Error occurred during MMR.");
                                }

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "");
//                                        showFPS(1000.0 / frameTime + "");
                                    }
                                });
                    }
                });
    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        try {
            carDetector = new SSDMobilenetDetector(this, Device.CPU, NUM_THREADS);
            DETECTOR_INPUT_SIZE = carDetector.getImageSizeX();
            makeAndModelRecognizer = new VMMRNet(this, Device.CPU, Model.MOBILENET_FLOAT, NUM_THREADS);
            RECOGNIZER_INPUT_SIZE = makeAndModelRecognizer.getImageSizeX();
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing models!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Models could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        int cropSize = DETECTOR_INPUT_SIZE;

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmapForDetection = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_ic_camera_connection_fragment;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void onInferenceConfigurationChanged() {
        final Device device = getDevice();
        final Model model = getModel();
        final Detector detector = getDetector();
        final int numThreads = getNumThreads();

        runInBackground(() -> recreateModels(model, detector, device, numThreads));
    }

    private void recreateModels(Model model, Detector detector, Device device, int numThreads){
        if(this.carDetector != null){
            this.carDetector.close();
        }
        if(makeAndModelRecognizer != null){
            makeAndModelRecognizer.close();
        }

        try{
            LOGGER.d("Creating detector " + detector);
            if(detector == Detector.SSD_MOBILENET){
                this.carDetector = new SSDMobilenetDetector(this, device, numThreads);
            }
            LOGGER.d("Created detector " + detector);
            LOGGER.d("Creating brand and model classifier " + model);

            makeAndModelRecognizer = new VMMRNet(this, device, model, numThreads);

            LOGGER.d("Created brand and model classifier " + model);
        } catch(IOException e){
            LOGGER.e(e, "Failed to create model/s");
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}