package com.klasifikasi.ubi.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Hasil implements Parcelable {

    @SerializedName("id")
    private Long id;

    @SerializedName("url")
    private String url;

    @SerializedName("warna")
    private String warna;

    @SerializedName("tekstur")
    private String tekstur;

    @SerializedName("gabungan")
    private String gabungan;

    // format: "yyyy-MM-dd HH:mm:ss" (server Laravel)
    @SerializedName("tanggal")
    private String tanggal;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

    // ====== ctor kosong (wajib Gson) ======
    public Hasil() {}

    // ====== getter/setter ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getWarna() { return warna; }
    public void setWarna(String warna) { this.warna = warna; }

    public String getTekstur() { return tekstur; }
    public void setTekstur(String tekstur) { this.tekstur = tekstur; }

    public String getGabungan() { return gabungan; }
    public void setGabungan(String gabungan) { this.gabungan = gabungan; }

    public String getTanggal() { return tanggal; }
    public void setTanggal(String tanggal) { this.tanggal = tanggal; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    // ====== helper: parse tanggal ke Date ======
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public Date getTanggalAsDate() {
        if (tanggal == null || tanggal.trim().isEmpty()) return null;
        try { return SDF.parse(tanggal); } catch (ParseException e) { return null; }
    }

    public Date getCreatedAtAsDate() {
        if (createdAt == null || createdAt.trim().isEmpty()) return null;
        try { return SDF.parse(createdAt); } catch (ParseException e) { return null; }
    }

    public Date getUpdatedAtAsDate() {
        if (updatedAt == null || updatedAt.trim().isEmpty()) return null;
        try { return SDF.parse(updatedAt); } catch (ParseException e) { return null; }
    }

    // ====== Parcelable ======
    protected Hasil(Parcel in) {
        if (in.readByte() == 0) { id = null; } else { id = in.readLong(); }
        url = in.readString();
        warna = in.readString();
        tekstur = in.readString();
        gabungan = in.readString();
        tanggal = in.readString();
        createdAt = in.readString();
        updatedAt = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (id == null) { dest.writeByte((byte)0); } else { dest.writeByte((byte)1); dest.writeLong(id); }
        dest.writeString(url);
        dest.writeString(warna);
        dest.writeString(tekstur);
        dest.writeString(gabungan);
        dest.writeString(tanggal);
        dest.writeString(createdAt);
        dest.writeString(updatedAt);
    }

    @Override public int describeContents() { return 0; }

    public static final Creator<Hasil> CREATOR = new Creator<Hasil>() {
        @Override public Hasil createFromParcel(Parcel in) { return new Hasil(in); }
        @Override public Hasil[] newArray(int size) { return new Hasil[size]; }
    };

    @Override
    public String toString() {
        return "Hasil{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", warna='" + warna + '\'' +
                ", tekstur='" + tekstur + '\'' +
                ", gabungan='" + gabungan + '\'' +
                ", tanggal='" + tanggal + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt +
                '}';
    }
}
