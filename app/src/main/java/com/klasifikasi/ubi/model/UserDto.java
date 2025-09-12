package com.klasifikasi.ubi.model;

import com.google.gson.annotations.SerializedName;

public class UserDto {
    public int id;

    @SerializedName(value = "name", alternate = {"nama"})
    public String nama;

    public String email;

    @SerializedName(value = "level", alternate = {"role"})
    public String level;
}

