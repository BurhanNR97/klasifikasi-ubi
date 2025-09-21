// com.klasifikasi.ubi.model.StartTrainRequest
package com.klasifikasi.ubi.model;

public class StartTrainRequest {
    public Integer kfold;    // 1=split, >1=kfold
    public String task;      // "split"|"kfold"
    public Integer epochs;

    // baru:
    public String stream;            // "color"|"texture"|"fused"
    public Boolean kfold_summary_only; // true = hanya bars per fold
    public Boolean cm_only;            // true = hanya CM (kalau split)

    public StartTrainRequest(int kfold) { this.kfold = kfold; }
    public StartTrainRequest(int kfold, String task, Integer epochs) {
        this.kfold = kfold; this.task = task; this.epochs = epochs;
    }
}
