package com.klasifikasi.ubi.model;

import java.util.Map;

public class LatestTrainResponse {
    public Integer id;
    public Integer dataset_id;   // yang kita butuhkan
    public String status;        // "success"
    public Integer progress;
    public Map<String, Object> metrics;
    public Map<String, Object> artifacts;
}
