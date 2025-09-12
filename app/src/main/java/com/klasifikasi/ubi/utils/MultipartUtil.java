package com.klasifikasi.ubi.utils;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class MultipartUtil {
    public static RequestBody text(String v) {
        return RequestBody.create(v == null ? "" : v, MediaType.parse("text/plain"));
    }
    public static MultipartBody.Part file(String partName, File file) {
        if (file == null) return null;
        RequestBody rb = RequestBody.create(file, MediaType.parse("image/*"));
        return MultipartBody.Part.createFormData(partName, file.getName(), rb);
    }
}

