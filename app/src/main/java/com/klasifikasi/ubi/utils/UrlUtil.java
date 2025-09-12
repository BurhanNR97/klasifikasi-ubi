// com/klasifikasi/ubi/utils/UrlUtil.java
package com.klasifikasi.ubi.utils;

import com.klasifikasi.ubi.net.ApiConst;

public final class UrlUtil {
    private UrlUtil() {}

    /** Gabungkan BASE_FILE_URL + path relatif (mis. "storage/…") atau kembalikan jika sudah http(s). */
    public static String buildPublicUrl(String rel) {
        if (rel == null) return null;
        String f = rel.trim();
        if (f.isEmpty()) return null;

        // sudah absolute URL
        if (f.startsWith("http://") || f.startsWith("https://")) return f;

        String base = ApiConst.BASE_FILE_URL; // contoh: http://147.93.81.231/
        if (base == null || base.isEmpty()) return f;

        // hilangkan leading slash di path relatif
        if (f.startsWith("/")) f = f.substring(1);
        // pastikan base berakhiran '/'
        if (!base.endsWith("/")) base = base + "/";

        return base + f; // -> http://IP/storage/...
    }
}
