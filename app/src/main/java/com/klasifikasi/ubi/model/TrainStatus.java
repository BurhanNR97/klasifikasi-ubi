// com/klasifikasi/ubi/model/TrainStatus.java
package com.klasifikasi.ubi.model;

import java.util.List;
import java.util.Map;

public class TrainStatus {
    public int dataset_id;
    public String status;         // running | success | failed | idle
    public int progress;          // 0..100
    public String message;

    // BACKCOMPAT (boleh null, kalau masih mau dipakai tempat lain)
    public String result_model;
    public String result_tflite;
    public String result_labels;

    // Baru: tiga set artefak
    public Artifacts artifacts;
    public Metrics metrics;

    public static class Artifacts {
        public String labels;
        public Kind color;
        public Kind texture;
        public Kind fused;
    }
    public static class Kind {
        public String keras;
        public String h5;
        public String tflite;
    }

    public static class Metrics {
        public double val_accuracy_last;
        public double accuracy_last;
        public int epochs;
        public List<String> classes;
    }
}