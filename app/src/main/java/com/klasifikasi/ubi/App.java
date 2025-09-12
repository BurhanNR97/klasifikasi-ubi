package com.klasifikasi.ubi;

import android.app.Application;
import com.klasifikasi.ubi.net.RetrofitClient;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        RetrofitClient.init();
    }
}
