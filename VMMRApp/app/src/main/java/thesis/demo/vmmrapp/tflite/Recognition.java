package thesis.demo.vmmrapp.tflite;

import android.graphics.RectF;

public class Recognition {

    private Integer id;
    private String title;
    private Float confidenceScore;
    private RectF location;

    public Recognition(Integer id, String title, Float confidenceScore, RectF location){

        this.id = id;
        this.title = title;
        this.confidenceScore = confidenceScore;
        this.location = location;

    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title){
        this.title = title;
    }

    public Float getConfidence() {
        return confidenceScore;
    }

    public RectF getLocation() {
        return location;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setConfidenceScore(Float confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public void setLocation(RectF location) {
        this.location = location;
    }

    @Override
    public String toString() {
        String resultString = "";
        if (this.id != null) {
            resultString += "[" + id + "] ";
        }

        if (this.title != null) {
            resultString += title + " ";
        }

        if (this.confidenceScore != null) {
            resultString += String.format("(%.1f%%) ", confidenceScore * 100.0f);
        }

        if (this.location != null) {
            resultString += this.location + " ";
        }

        return resultString.trim();
    }
}
