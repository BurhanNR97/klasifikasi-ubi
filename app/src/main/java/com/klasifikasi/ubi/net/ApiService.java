package com.klasifikasi.ubi.net;

import com.klasifikasi.ubi.model.DatasetItem;
import com.klasifikasi.ubi.model.Hasil;
import com.klasifikasi.ubi.model.JumlahResponse;
import com.klasifikasi.ubi.model.LatestTrainResponse;
import com.klasifikasi.ubi.model.LoginRequest;
import com.klasifikasi.ubi.model.LoginResponse;
import com.klasifikasi.ubi.model.Sampel;
import com.klasifikasi.ubi.model.TrainStatus;
import com.klasifikasi.ubi.model.StartTrainRequest;
import com.klasifikasi.ubi.model.ArtifactsResponse;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @Headers("Accept: application/json")
    @POST("login")
    Call<LoginResponse> login(@Body LoginRequest body);

    // ===== Sampel =====
    @Headers("Accept: application/json")
    @GET("sampel")
    Call<List<Sampel>> listSampel(@Query("search") String search);

    @Headers("Accept: application/json")
    @GET("sampel/{id}")
    Call<Sampel> getSampel(@Path("id") int id);

    @Headers("Accept: application/json")
    @GET("sampel/next-kode")
    Call<Sampel> getNextKode();

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("sampel")
    Call<Sampel> createSampelBase64(
            @Field("jenis") String jenis,
            @Field("foto") String fotoBase64
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("sampel/{id}")
    Call<Sampel> updateSampelBase64(
            @Path("id") int id,
            @Field("_method") String method,
            @Field("jenis") String jenis,
            @Field("foto") String fotoBase64
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("sampel/{id}")
    Call<Void> deleteSampelCompat(
            @Path("id") int id,
            @Field("_method") String method
    );

    // ===== Dashboard / jumlah =====
    @Headers("Accept: application/json")
    @GET("jumlah")
    Call<JumlahResponse> getJumlah();

    // ===== Dataset (upload ZIP & list) =====
    @Headers("Accept: application/json")
    @GET("dataset")
    Call<List<DatasetItem>> listDataset();

    @Headers("Accept: application/json")
    @Multipart
    @POST("dataset")
    Call<DatasetItem> uploadDataset(
            @Part("nama") RequestBody nama,
            @Part MultipartBody.Part file
    );

    // ===== Training (mulai & status) =====
    @Headers("Accept: application/json")
    @POST("dataset/{id}/train")
    Call<TrainStatus> startTrain(@Path("id") int id, @Body StartTrainRequest body);

    @Headers("Accept: application/json")
    @GET("dataset/{id}/train")
    Call<TrainStatus> getTrainStatus(@Path("id") int id);

    // Opsional: ambil daftar artifacts saja (models+images)
    @Headers("Accept: application/json")
    @GET("dataset/{id}/artifacts")
    Call<ArtifactsResponse> getArtifacts(@Path("id") int id);

    // ===== Ping (debug nginx/laravel) =====
    @Headers("Accept: application/json")
    @GET("ping")
    Call<Object> ping();

    @GET("train/latest")
    Call<LatestTrainResponse> getLatestSuccess();

    @Headers("Accept: application/json")
    @Multipart
    @POST("hasil")
    Call<Hasil> createHasil(
            @Part("warna") RequestBody warna,
            @Part("tekstur") RequestBody tekstur,
            @Part("gabungan") RequestBody gabungan,
            @Part("tanggal") RequestBody tanggal,
            @Part MultipartBody.Part image
    );

    @Headers("Accept: application/json")
    @GET("hasil")
    Call<List<Hasil>> listHasil(@Query("search") String search);

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("hasil/{id}")
    Call<Void> deleteHasilCompat(
            @Path("id") long id,
            @Field("_method") String method
    );
}
