package com.klasifikasi.ubi.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class ProgressRequestBody extends RequestBody {

    public interface Listener { void onProgress(long written, long total); }

    private final File file;
    private final String contentType;
    private final Listener listener;

    public ProgressRequestBody(File file, String contentType, Listener l) {
        this.file = file; this.contentType = contentType; this.listener = l;
    }

    @Override public MediaType contentType() { return MediaType.parse(contentType); }
    @Override public long contentLength() { return file.length(); }

    @Override public void writeTo(BufferedSink sink) throws IOException {
        long length = contentLength();
        byte[] buffer = new byte[8192];
        try (FileInputStream in = new FileInputStream(file)) {
            long written = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                sink.write(buffer, 0, read);
                written += read;
                if (listener != null) listener.onProgress(written, length);
            }
        }
    }
}