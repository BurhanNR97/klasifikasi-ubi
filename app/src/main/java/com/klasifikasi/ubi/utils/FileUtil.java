package com.klasifikasi.ubi.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtil {
    public static File from(Context ctx, Uri uri) {
        ContentResolver cr = ctx.getContentResolver();
        String name = getName(cr, uri);
        File out = new File(ctx.getCacheDir(), name);
        try (InputStream in = cr.openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
            os.flush();
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Gagal membaca file dari uri: " + e.getMessage(), e);
        }
    }

    private static String getName(ContentResolver cr, Uri uri) {
        String result = "upload_file";
        Cursor c = cr.query(uri, null, null, null, null);
        if (c != null) {
            int nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (c.moveToFirst() && nameIndex >= 0) result = c.getString(nameIndex);
            c.close();
        }
        return result;
    }
}
