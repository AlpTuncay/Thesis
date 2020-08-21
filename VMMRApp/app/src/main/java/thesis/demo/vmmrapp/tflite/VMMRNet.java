package thesis.demo.vmmrapp.tflite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import thesis.demo.vmmrapp.utils.Logger;

import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.common.ops.NormalizeOp;


public class VMMRNet {
    private static final Logger LOGGER = new Logger();

    private String modelPath;

    private final int imageSizeX;

    private final int imageSizeY;

    private static final int MAX_RESULTS = 1;

    private GpuDelegate gpuDelegate;

    protected Interpreter tfliteInterpreter;

    private List<String> labels;

    private TensorImage inputImageBuffer;

    private final TensorBuffer outputProbabilityBuffer;

    public VMMRNet(Activity activity, Device device, Model model, int numThreads) throws IOException {

        switch(model) {
            case MOBILENET_FLOAT:
                this.setModelPath("vmmr_net_3.tflite");
                break;
            case SQUEEZE_NET:
                this.setModelPath("squeeze_net.tflite");
                break;
        }

        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());

        Interpreter.Options tfliteInterpreterOptions = new Interpreter.Options();

        switch(device) {
            case GPU:
                this.gpuDelegate = new GpuDelegate();
                tfliteInterpreterOptions.addDelegate(this.gpuDelegate);
                break;
            case CPU:
                tfliteInterpreterOptions.setNumThreads(numThreads);
                break;
        }

        this.tfliteInterpreter = new Interpreter(tfliteModel, tfliteInterpreterOptions);

        this.labels = FileUtil.loadLabels(activity, getLabelPath());

        int imageTensorIdx = 0;
        Tensor imageInputTensor = this.tfliteInterpreter.getInputTensor(imageTensorIdx);
        int[] imageShape = imageInputTensor.shape();
        this.imageSizeX = imageShape[1];
        this.imageSizeY = imageShape[2];
        DataType imgDataType = imageInputTensor.dataType();

        int probTensorIdx = 0;
        Tensor outputTensor = this.tfliteInterpreter.getOutputTensor(probTensorIdx);
        int[] probShape = outputTensor.shape();
        DataType probDataType = outputTensor.dataType();

        this.inputImageBuffer = new TensorImage(imgDataType);
        this.outputProbabilityBuffer = TensorBuffer.createFixedSize(probShape, probDataType);

        LOGGER.d("Created VMMRNet.");

    }

    public List<Recognition> runInferenceOnDetections(final Bitmap bitmap, Recognition detectedObject){

        long imgLoadStartTime = SystemClock.uptimeMillis();
        this.inputImageBuffer = loadImage(bitmap);
        long imgLoadEndTime = SystemClock.uptimeMillis();
        LOGGER.v("It took " + (imgLoadEndTime - imgLoadStartTime) + " to load the image.");

        long inferenceStartTime = SystemClock.uptimeMillis();
        this.tfliteInterpreter.run(this.inputImageBuffer.getBuffer().rewind(), this.outputProbabilityBuffer.getBuffer().rewind());
        long inferenceEndTime = SystemClock.uptimeMillis();
        LOGGER.v("Inference took " + (inferenceEndTime - inferenceStartTime));

        Map<String, Float> labelProbMap = new TensorLabel(this.labels,
                this.outputProbabilityBuffer).getMapWithFloatValue();

        return getTopKProb(labelProbMap, detectedObject);
    }

    private TensorImage loadImage(final Bitmap bitmap){

        this.inputImageBuffer.load(bitmap);

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new Rot90Op(270 / 90))
                .add(new ResizeOp(this.imageSizeY, this.imageSizeX, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .build();

        return imageProcessor.process(this.inputImageBuffer);

    }

    private List<Recognition> getTopKProb(Map<String, Float> labelProbMap, Recognition detectedObject){
        PriorityQueue<Recognition> pq = new PriorityQueue<>( MAX_RESULTS, (lhs, rhs) -> Float.compare(rhs.getConfidence(), lhs.getConfidence()));

        for (Map.Entry<String, Float> entry : labelProbMap.entrySet()) {
            Recognition detected = new Recognition(detectedObject.getId(), entry.getKey(), entry.getValue(), detectedObject.getLocation());
            pq.add(detected);
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }

    protected String getModelPath(){
        return this.modelPath;
    }

    private void setModelPath(String modelPath){
        this.modelPath = modelPath;
    }

    /** Gets the name of the label file stored in Assets. */
    protected String getLabelPath(){
        return "vmmr_labels.txt";
    }

    public int getImageSizeX() {
        return imageSizeX;
    }

    public int getImageSizeY() {
        return imageSizeY;
    }

    public void close(){

        this.tfliteInterpreter.close();
        if(this.gpuDelegate != null){
            this.gpuDelegate.close();
        }
    }
}
