package com.klasifikasi.ubi.net;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ApiPage<T> {
    @SerializedName("data")
    public List<T> data;

    @SerializedName("current_page")
    public int currentPage;

    @SerializedName("last_page")
    public int lastPage;

    @SerializedName("per_page")
    public int perPage;

    @SerializedName("total")
    public int total;

    // field lain dari Laravel boleh ditambah jika perlu
}
