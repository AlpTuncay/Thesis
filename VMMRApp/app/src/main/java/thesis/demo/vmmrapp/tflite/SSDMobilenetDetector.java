package thesis.demo.vmmrapp.tflite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import thesis.demo.vmmrapp.utils.Logger;

public class SSDMobilenetDetector {

    private static final Logger LOGGER = new Logger();

    private static final int NUM_DETECTIONS = 10;
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;

    private final int imageSizeX;
    private final int imageSizeY;

    private GpuDelegate gpuDelegate;
    private Interpreter tfliteInterpreter;

    private TensorImage inputImageBuffer;

    private final float[][][] boxOutput;

    private final float[][] scoresOutput;

    private final float[][] clsOutput;

    private final float[] numDetectionOutput;

    private Map<Integer, Object> outputMap = new HashMap<>();

    private List<String> labels;

    public SSDMobilenetDetector(Activity activity, Device device, int numThreads) throws IOException {

        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());

        Interpreter.Options tfliteInterpreterOptions = new Interpreter.Options();

        switch(device) {
            case GPU:
                this.gpuDelegate = new GpuDelegate();
                tfliteInterpreterOptions.addDelegate(this.gpuDelegate);
            case CPU:
                tfliteInterpreterOptions.setNumThreads(numThreads);
                break;
        }

        this.tfliteInterpreter = new Interpreter(tfliteModel, tfliteInterpreterOptions);

        this.labels = FileUtil.loadLabels(activity, getLabelPath());

        int inputTensorIdx = 0;
        Tensor imageInputTensor = this.tfliteInterpreter.getInputTensor(inputTensorIdx);
        int[] imageInputShape = imageInputTensor.shape();
        this.imageSizeX = imageInputShape[1];
        this.imageSizeY = imageInputShape[2];
        DataType imageInputDataType = imageInputTensor.dataType();

        int boxOutputTensorIdx = 0;
        Tensor boxOutputTensor = this.tfliteInterpreter.getOutputTensor(boxOutputTensorIdx);
        int[] boxOutputShape = boxOutputTensor.shape();

        int clsOutputTensorIdx = 1;
        Tensor clsOutputTensor = this.tfliteInterpreter.getOutputTensor(clsOutputTensorIdx);
        int[] clsOutputShape = clsOutputTensor.shape();

        int scoresOutputTensorIdx = 2;
        Tensor scoresOutputTensor = this.tfliteInterpreter.getOutputTensor(scoresOutputTensorIdx);
        int[] scoresOutputShape = scoresOutputTensor.shape();

        int numClsOutputTensorIdx = 3;
        Tensor numClsOutputTensor = this.tfliteInterpreter.getOutputTensor(numClsOutputTensorIdx);
        int[] numClsOutputShape = numClsOutputTensor.shape();

        this.inputImageBuffer = new TensorImage(imageInputDataType);
        this.boxOutput = new float[boxOutputShape[0]][boxOutputShape[1]][boxOutputShape[2]];
        this.clsOutput = new float[clsOutputShape[0]][clsOutputShape[1]];
        this.scoresOutput = new float[scoresOutputShape[0]][scoresOutputShape[1]];
        this.numDetectionOutput = new float[numClsOutputShape[0]];

        this.outputMap.put(0, this.boxOutput);
        this.outputMap.put(1, this.clsOutput);
        this.outputMap.put(2, this.scoresOutput);
        this.outputMap.put(3, this.numDetectionOutput);
    }

    public List<Recognition> runInferenceOnFrame(Bitmap bitmap){

        this.inputImageBuffer = this.loadImage(bitmap);

        Object[] inputTensor = {this.inputImageBuffer.getBuffer().rewind()};
        long inferenceStartTime = SystemClock.uptimeMillis();
        this.tfliteInterpreter.runForMultipleInputsOutputs(inputTensor, this.outputMap);
        long inferenceEndTime = SystemClock.uptimeMillis();
        LOGGER.v("Inference took " + (inferenceEndTime - inferenceStartTime));

        int numDetectionsOutput = Math.min(NUM_DETECTIONS, (int) numDetectionOutput[0]);

        final ArrayList<Recognition> detections = new ArrayList<>(numDetectionsOutput);

        for(int i = 0; i < numDetectionsOutput; i++){

            final RectF detection = new RectF(
                this.boxOutput[0][i][1] * imageSizeX,
                this.boxOutput[0][i][0] * imageSizeY,
                this.boxOutput[0][i][3] * imageSizeX,
                this.boxOutput[0][i][2] * imageSizeY
            );

            detections.add(
                    new Recognition(i, this.labels.get((int) this.clsOutput[0][i]), scoresOutput[0][i], detection)
            );
        }

        return detections;
    }

    private TensorImage loadImage(final Bitmap bitmap){

        this.inputImageBuffer.load(bitmap);

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(this.imageSizeY, this.imageSizeX, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD)).build();

        return imageProcessor.process(this.inputImageBuffer);
    }

    protected String getModelPath() {
        return "ssd_model_final.tflite";
    }

    protected String getLabelPath() {
        return "car_detection_labels.txt";
    }

    public int getImageSizeX() {
        return imageSizeX;
    }

    public int getImageSizeY() {
        return imageSizeY;
    }

    public void close(){
        if(this.tfliteInterpreter != null){
            this.tfliteInterpreter.close();
        }
        if(this.gpuDelegate != null){
            this.gpuDelegate.close();
        }
    }
}
