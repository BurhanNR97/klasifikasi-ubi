package com.klasifikasi.ubi.net;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final SessionManager session;
    public AuthInterceptor(SessionManager s) { this.session = s; }

    @Override public Response intercept(Chain chain) throws IOException {
        Request req = chain.request();
        String token = session.getToken();
        if (token != null && !token.isEmpty()) {
            req = req.newBuilder()
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Accept", "application/json")
                    .build();
        }
        return chain.proceed(req);
    }
}