package com.klasifikasi.ubi.model;

import java.util.List;
import java.util.Map;

public class TrainStatus {
    public int dataset_id;
    public String status;     // running | success | failed | idle
    public int progress;      // 0..100
    public String message;

    // BACKCOMPAT (boleh null; untuk kode lama yang masih pakai ini)
    public String result_model;
    public String result_tflite;
    public String result_labels;

    /** Artefak baru: models (color/texture/fused), images (confmat/kfold), dll. */
    public Artifacts artifacts;

    /** Metrics bisa bervariasi: sediakan versi typed + fallback map */
    public Metrics metrics;                // typed (nullable)
    public Map<String, Object> metricsRaw; // jika backend kirim struktur berbeda

    // ================== Nested ==================

    public static class Artifacts {
        /** Label file (top-level) */
        public String labels;

        /** Model default (nama standar) */
        public Kind color;
        public Kind texture;
        public Kind fused;

        /** Gambar-gambar output */
        public Images images;

        /** Model dari fold terakhir (opsional) */
        public KfoldModels kfold_models_maybe;
    }

    /** Varian path model */
    public static class Kind {
        public String keras;
        public String h5;
        public String tflite;
    }

    /** Gambar-gambar hasil training */
    public static class Images {
        // Split (validation 30%)
        public String confusion_matrix_val;
        public String precision_val;
        public String recall_val;
        public String f1_val;
        public String accuracy_val;

        // K-Fold
        public String kfold_acc;
        public String confusion_overall;
        public String precision_overall;
        public String recall_overall;
        public String f1_overall;
        public String accuracy_overall;

        public java.util.List<String> confusion_per_fold;
    }


    /** Model hasil fold terakhir (jika disimpan) */
    public static class KfoldModels {
        public Kind color_last_fold;
        public Kind texture_last_fold;
        public Kind fused_last_fold;
    }

    /** Metrics typed (semua nullable supaya aman di berbagai bentuk response) */
    public static class Metrics {
        public Integer epochs;
        public List<String> classes;

        // Mode split
        public Double val_accuracy_last;  // beberapa backend taruh di root
        public Double accuracy_last;      // cadangan nama lain

        // Detail per-head (jika backend kirim bersarang)
        public HeadMetric color;
        public HeadMetric texture;

        // Fused (mode split) atau ringkasan kfold
        public FusedMetric fused;

        // Mode kfold
        public Integer kfold;               // jumlah fold
        public List<Double> acc_per_fold;   // akurasi tiap fold
        public Double acc_mean;             // rata-rata akurasi
    }

    public static class HeadMetric {
        public Double val_accuracy_last;
        public Double accuracy_last;
    }

    public static class FusedMetric {
        public Double val_accuracy_est;  // untuk val split (perkiraan akurasi fused)
        public Double acc_mean;          // kalau backend taruh mean di sini
    }

    // ================== Helper ==================

    /** URL relatif confusion matrix “utama” yang bisa dipakai UI */
    public String getPrimaryConfMatRel() {
        if (artifacts != null && artifacts.images != null) {
            if (artifacts.images.confusion_overall != null) return artifacts.images.confusion_overall; // prioritas K-Fold overall
            return artifacts.images.confusion_matrix_val; // fallback val split
        }
        return null;
    }

    /** URL relatif chart akurasi K-Fold (jika ada) */
    public String getKfoldChartRel() {
        if (artifacts != null && artifacts.images != null) {
            return artifacts.images.kfold_acc;
        }
        return null;
    }

    /** URL relatif TFLite fused (utama) dengan fallback ke field lama */
    public String getFusedTfliteRel() {
        if (artifacts != null && artifacts.fused != null && artifacts.fused.tflite != null) {
            return artifacts.fused.tflite;
        }
        return result_tflite;
    }

    /** URL relatif labels (utama) dengan fallback ke field lama */
    public String getLabelsRel() {
        if (artifacts != null && artifacts.labels != null) {
            return artifacts.labels;
        }
        return result_labels;
    }
}
