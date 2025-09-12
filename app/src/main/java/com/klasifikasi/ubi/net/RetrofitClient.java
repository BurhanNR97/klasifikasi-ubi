package com.klasifikasi.ubi.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {
    private static ApiService api;

    public static void init() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient http = new OkHttpClient.Builder()
                // pastikan setiap request kirim Accept json
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .header("User-Agent",
                                        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                                                + "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .build()))
                .addInterceptor(log)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit r = new Retrofit.Builder()
                .baseUrl(ApiConst.BASE_API_URL)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = r.create(ApiService.class);
    }

    public static ApiService api() {
        if (api == null) throw new IllegalStateException("RetrofitClient.init() belum dipanggil");
        return api;
    }

    private RetrofitClient() {}
}
