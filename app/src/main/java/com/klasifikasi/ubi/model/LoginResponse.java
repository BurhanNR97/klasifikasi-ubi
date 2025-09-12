package com.klasifikasi.ubi.model;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName(value = "user", alternate = {"data"})
    public UserDto user;

    @SerializedName("token")
    public String token;

    @SerializedName(value = "success", alternate = {"status", "ok"})
    public Boolean success;

    public String message;
}