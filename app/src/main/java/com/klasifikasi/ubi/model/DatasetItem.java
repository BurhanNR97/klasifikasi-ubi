package com.klasifikasi.ubi.model;

import com.google.gson.annotations.SerializedName;

public class DatasetItem {
    public int id;
    public String nama;
    public String tanggal;

    // nama field dari backend (misal "file")
    @SerializedName("file")
    private String file;

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
}