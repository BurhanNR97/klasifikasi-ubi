package com.klasifikasi.ubi.UI;

import static com.klasifikasi.ubi.R.id.toolbar_klasifikasi;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;

import com.bumptech.glide.Glide;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.net.ApiConst;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Klasifikasi: Warna, Tekstur, Gabungan (+export CSV & simpan hasil ke server)
 */
public class KlasifikasiActivity extends AppCompatActivity {

    private static final String TAG = "Klasifikasi";
    private static final int W = 160, H = 160, C = 3;

    // UI
    private android.widget.ImageView imgPreview;
    private android.widget.ProgressBar progress;
    private android.widget.TextView tvStatus;
    private android.widget.Button btnKlasifikasi;

    private MaterialCardView cardWarna, cardTekstur, cardFused, cardManual;
    private android.widget.TextView tWTitle, tWPred, tWConf;
    private android.widget.TextView tTTitle, tTPred, tTConf;
    private android.widget.TextView tFTitle, tFPred, tFConf;

    private android.widget.TextView tvManual;
    private android.widget.Button btnLihatManual;

    private Uri pickedUri = null;

    // Files & Interpreters
    private File fLabels, fColor, fTexture, fFused, fSingle;
    private Interpreter itColor, itTexture, itFused, itSingle;
    private volatile boolean modelColorReady = false;
    private volatile boolean modelTexReady   = false;
    private volatile boolean modelFusedReady = false;
    private volatile boolean modelSingleReady= false;

    private String[] labels = new String[0];

    // dataset id (0 = auto/latest dari API)
    private int datasetId = 0;

    // id user (untuk simpan hasil)
    private String userId = "0";

    // data untuk “perhitungan manual”
    private Bitmap lastSrc, lastColorBmp, lastTextureBmp;
    private float[][][][] lastInColor, lastInTex;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_klasifikasi);

        // Insets
        final View root = findViewById(R.id.mainKlasifikasi);
        final int pL = root.getPaddingLeft(), pT = root.getPaddingTop(), pR = root.getPaddingRight(), pB = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pL + sb.left, pT + sb.top, pR + sb.right, pB + sb.bottom);
            return insets;
        });

        // Toolbar
        MaterialToolbar toolbar = findViewById(toolbar_klasifikasi);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(KlasifikasiActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            });
        }

        // Ambil dataset_id dari intent (0 = auto)
        datasetId = getIntent().getIntExtra("dataset_id", 0);

        // Ambil id_user (support 2 nama key)
        String id1 = getIntent().getStringExtra("id");
        String id2 = getIntent().getStringExtra("user_id");
        if (id1 != null && !id1.isEmpty()) userId = id1;
        else if (id2 != null && !id2.isEmpty()) userId = id2;

        // UI refs utama
        imgPreview = findViewById(R.id.imgPreview);
        progress   = findViewById(R.id.progress);
        tvStatus   = findViewById(R.id.tvStatus);
        btnKlasifikasi = findViewById(R.id.btnKlasifikasi);
        btnKlasifikasi.setEnabled(false);
        btnKlasifikasi.setOnClickListener(v -> onClassify());
        updateBtnKlasifikasiState();

        // Kartu hasil + perhitungan manual
        cardWarna   = findViewById(R.id.cardWarna);
        cardTekstur = findViewById(R.id.cardTekstur);
        cardFused   = findViewById(R.id.cardFused);
        cardManual  = findViewById(R.id.cardManual);

        tWTitle = cardWarna.findViewById(R.id.tvTitleW);
        tWPred  = cardWarna.findViewById(R.id.tvPredW);
        tWConf  = cardWarna.findViewById(R.id.tvConfW);

        tTTitle = cardTekstur.findViewById(R.id.tvTitleT);
        tTPred  = cardTekstur.findViewById(R.id.tvPredT);
        tTConf  = cardTekstur.findViewById(R.id.tvConfT);

        tFTitle = cardFused.findViewById(R.id.tvTitleF);
        tFPred  = cardFused.findViewById(R.id.tvPredF);
        tFConf  = cardFused.findViewById(R.id.tvConfF);

        tWTitle.setText("Pengujian Warna");
        tTTitle.setText("Pengujian Tekstur");
        tFTitle.setText("Pengujian Warna + Tekstur");

        tvManual = cardManual.findViewById(R.id.tvManual);
        btnLihatManual = cardManual.findViewById(R.id.btnLihatManual);
        btnLihatManual.setOnClickListener(v -> showManualSheet());

        // picker
        findViewById(R.id.btnGallery).setOnClickListener(v ->
                ImagePicker.with(this).galleryOnly().cropSquare().compress(1024).maxResultSize(1080,1080).start()
        );
        findViewById(R.id.btnCamera).setOnClickListener(v ->
                ImagePicker.with(this).cameraOnly().cropSquare().compress(1024).maxResultSize(1080,1080).start()
        );

        // Siapkan model (async)
        new Thread(() -> {
            runOnUiThread(() -> setBusy(true, "Menyiapkan model..."));
            boolean ok = prepareModelsAuto();
            runOnUiThread(() -> {
                setBusy(false, ok ? "Model siap." : "Gagal menyiapkan model");
                // tampilkan kartu sesuai model
                cardWarna.setVisibility(modelColorReady || modelSingleReady ? View.VISIBLE : View.GONE);
                cardTekstur.setVisibility(modelTexReady ? View.VISIBLE : View.GONE);
                cardFused.setVisibility((modelFusedReady || (modelColorReady && modelTexReady)) ? View.VISIBLE : View.GONE);
                updateBtnKlasifikasiState();
                if (!ok) Toast.makeText(this, "Tidak ada model yang bisa dipakai. Cek URL artefak & training.", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void updateBtnKlasifikasiState() {
        boolean modelReady = (modelColorReady || modelTexReady || modelFusedReady || modelSingleReady);
        boolean imageReady = (pickedUri != null);
        btnKlasifikasi.setEnabled(modelReady && imageReady);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (itColor  != null) itColor.close();  } catch (Exception ignored) {}
        try { if (itTexture!= null) itTexture.close();} catch (Exception ignored) {}
        try { if (itFused  != null) itFused.close();  } catch (Exception ignored) {}
        try { if (itSingle != null) itSingle.close(); } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int rc, int resultCode, @Nullable Intent data) {
        super.onActivityResult(rc, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                pickedUri = uri;
                Glide.with(this).load(pickedUri).into(imgPreview);
            }
        } else if (resultCode == com.github.dhaval2404.imagepicker.ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, com.github.dhaval2404.imagepicker.ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        }
        updateBtnKlasifikasiState();
    }

    private void setBusy(boolean busy, String status) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        tvStatus.setText(status);
    }

    /* ===============================
       Ambil dataset_id & artefak
       =============================== */
    private boolean prepareModelsAuto() {
        try {
            if (datasetId <= 0) {
                JSONObject latest = httpGetJson(ApiConst.BASE_API_URL + "train/latest");
                int ds = latest.optInt("dataset_id", 0);
                if (ds > 0) datasetId = ds;
            }
            if (datasetId <= 0) throw new IOException("dataset_id tidak ditemukan");

            File dir = new File(getFilesDir(), "models/dataset_" + datasetId);
            if (!dir.exists()) dir.mkdirs();
            fLabels  = new File(dir, "labels.json");
            fColor   = new File(dir, "model_final_color.tflite");
            fTexture = new File(dir, "model_final_texture.tflite");
            fFused   = new File(dir, "model_final_fused.tflite");
            fSingle  = new File(dir, "model_single.tflite");

            boolean ok = ensureModelsReadyViaStatus(dir);
            if (!ok) ok = ensureModelsReadyFallback(dir);
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "prepareModelsAuto: " + e.getMessage(), e);
            runOnUiThread(() -> Toast.makeText(this, "Model error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return false;
        }
    }

    private boolean ensureModelsReadyViaStatus(File dir) {
        try {
            JSONObject st = httpGetJson(ApiConst.BASE_API_URL + "dataset/" + datasetId + "/train");
            JSONObject artifacts = st.optJSONObject("artifacts");

            // ===== Labels =====
            String labelsUrl = (artifacts != null) ? artifacts.optString("labels", "") : "";
            if (labelsUrl == null || labelsUrl.isEmpty()) {
                labelsUrl = "storage/models/dataset_" + datasetId + "/labels.json";
            }
            downloadIfNeeded(fileUrl(labelsUrl), fLabels);
            labels = readLabels(fLabels);

            boolean any = false;
            if (artifacts == null) return false;

            // helper ambil path dari objek kind
            java.util.function.Function<JSONObject, String> pickTflite = (o) -> {
                if (o == null) return null;
                String t = o.optString("tflite", "");
                if (t != null && !t.isEmpty()) return t;
                // fallback flex
                String tf = o.optString("tflite_flex", "");
                if (tf != null && !tf.isEmpty()) return tf;
                return null;
            };

            // ======== 1) MODE UMUM ========
            String uColor = pickTflite.apply(artifacts.optJSONObject("color"));
            String uTex   = pickTflite.apply(artifacts.optJSONObject("texture"));
            String uFused = pickTflite.apply(artifacts.optJSONObject("fused"));

            // ======== 2) MODE K-FOLD (last fold) ========
            JSONObject kfoldMaybe = artifacts.optJSONObject("kfold_models_maybe");
            if (uColor == null && kfoldMaybe != null) {
                uColor = pickTflite.apply(kfoldMaybe.optJSONObject("color_last_fold"));
            }
            if (uTex == null && kfoldMaybe != null) {
                uTex = pickTflite.apply(kfoldMaybe.optJSONObject("texture_last_fold"));
            }
            if (uFused == null && kfoldMaybe != null) {
                uFused = pickTflite.apply(kfoldMaybe.optJSONObject("fused_last_fold"));
            }

            // ===== Unduh & buka interpreter =====
            if (uColor != null && uColor.endsWith(".tflite")) {
                try {
                    downloadIfNeeded(fileUrl(uColor), fColor);
                    itColor = openInterpreter(fColor);
                    modelColorReady = true; any = true;
                } catch (Exception e) {
                    String alt = uColor.replace(".tflite", "_flex.tflite");
                    try { downloadIfNeeded(fileUrl(alt), fColor); itColor = openInterpreter(fColor); modelColorReady = true; any = true; }
                    catch (Exception ex) { reportModelError("color", e, fColor); }
                }
            }

            if (uTex != null && uTex.endsWith(".tflite")) {
                try {
                    downloadIfNeeded(fileUrl(uTex), fTexture);
                    itTexture = openInterpreter(fTexture);
                    modelTexReady = true; any = true;
                } catch (Exception e) {
                    String alt = uTex.replace(".tflite", "_flex.tflite");
                    try { downloadIfNeeded(fileUrl(alt), fTexture); itTexture = openInterpreter(fTexture); modelTexReady = true; any = true; }
                    catch (Exception ex) { reportModelError("texture", e, fTexture); }
                }
            }

            if (uFused != null && uFused.endsWith(".tflite")) {
                try {
                    downloadIfNeeded(fileUrl(uFused), fFused);
                    itFused = openInterpreter(fFused);
                    modelFusedReady = true; any = true;
                } catch (Exception e) {
                    String alt = uFused.replace(".tflite", "_flex.tflite");
                    try { downloadIfNeeded(fileUrl(alt), fFused); itFused = openInterpreter(fFused); modelFusedReady = true; any = true; }
                    catch (Exception ex) { reportModelError("fused", e, fFused); }
                }
            }

            return any;
        } catch (Exception e) {
            Log.e(TAG, "ensureModelsReadyViaStatus: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean ensureModelsReadyFallback(File dir) {
        try {
            String root = "storage/models/dataset_" + datasetId + "/";

            downloadIfNeeded(fileUrl(root + "labels.json"), fLabels);
            labels = readLabels(fLabels);

            boolean any = false;

            try { downloadIfNeeded(fileUrl(root + "model_final_color_fold5.tflite"), fColor);
                itColor = openInterpreter(fColor); modelColorReady = true; any = true; }
            catch (Exception e) { reportModelError("color", e, fColor); }

            try { downloadIfNeeded(fileUrl(root + "model_final_texture_fold5.tflite"), fTexture);
                itTexture = openInterpreter(fTexture); modelTexReady = true; any = true; }
            catch (Exception e) { reportModelError("texture", e, fTexture); }

            try { downloadIfNeeded(fileUrl(root + "model_final_fused_fold5.tflite"), fFused);
                itFused = openInterpreter(fFused); modelFusedReady = true; any = true; }
            catch (Exception e1) {
                try { downloadIfNeeded(fileUrl(root + "model_final_fused_flex_fold5.tflite"), fFused);
                    itFused = openInterpreter(fFused); modelFusedReady = true; any = true; }
                catch (Exception e2) { reportModelError("fused", e2, fFused); }
            }

            if (!(modelColorReady || modelTexReady || modelFusedReady)) {
                String[] names = { "model_final.tflite", "model.tflite", "final.tflite" };
                for (String n : names) {
                    try { downloadIfNeeded(fileUrl(root + n), fSingle);
                        itSingle = openInterpreter(fSingle); modelSingleReady = true; any = true; break; }
                    catch (Exception ignored) {}
                }
            }
            return any;
        } catch (Exception e) {
            Log.e(TAG, "ensureModelsReadyFallback: " + e.getMessage(), e);
            return false;
        }
    }

    private String fileUrl(String relativeOrAbs) {
        if (relativeOrAbs == null) return null;
        if (relativeOrAbs.startsWith("http://") || relativeOrAbs.startsWith("https://")) return relativeOrAbs;
        String base = ApiConst.BASE_FILE_URL;
        if (!base.endsWith("/")) base += "/";
        if (relativeOrAbs.startsWith("/")) relativeOrAbs = relativeOrAbs.substring(1);
        return base + relativeOrAbs;
    }

    private JSONObject httpGetJson(String url) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = readAll(is);
        c.disconnect();
        if (code != 200) throw new IOException("GET " + url + " -> " + code + " • " + body);
        return new JSONObject(body);
    }

    private void downloadIfNeeded(String url, File out) throws IOException {
        if (url == null) return;
        if (out.exists() && out.length() > 0) return;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("User-Agent", "Android");
        int code = c.getResponseCode();
        if (code != 200) {
            String err = readAll(c.getErrorStream());
            c.disconnect();
            throw new IOException("HTTP " + code + " GET " + url + " • " + err);
        }
        try (InputStream is = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r; long total = 0;
            while ((r = is.read(buf)) != -1) { fos.write(buf, 0, r); total += r; }
            if (total == 0) throw new IOException("Downloaded 0 bytes: " + url);
        } finally {
            c.disconnect();
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int r;
        while ((r = is.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toString("UTF-8");
    }

    private byte[] readBytes(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    private void reportModelError(String which, Exception e, File f) {
        Log.e(TAG, "Model " + which + " error: " + e.getMessage() +
                (f != null ? (" • file=" + f.getAbsolutePath() + " (" + f.length() + " B)") : ""), e);
    }

    private Interpreter openInterpreter(File f) throws Exception {
        if (f == null || !f.exists() || f.length() < 1024) {
            throw new FileNotFoundException("Model missing/too small: " + (f==null?"null":f.getAbsolutePath()));
        }
        Interpreter.Options opts = new Interpreter.Options();
        opts.setNumThreads(2);
        return new Interpreter(loadModel(f), opts);
    }

    private MappedByteBuffer loadModel(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f); FileChannel fc = fis.getChannel()) {
            return fc.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        }
    }

    /* ===============================
       Klasifikasi
       =============================== */
    private void onClassify() {
        if (pickedUri == null) {
            Toast.makeText(this, "Pilih/capture gambar dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!(modelColorReady || modelTexReady || modelFusedReady || modelSingleReady)) {
            Toast.makeText(this, "Model belum siap / tidak ditemukan", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            runOnUiThread(() -> setBusy(true, "Memproses gambar..."));
            try {
                Bitmap src = loadBitmap(pickedUri, W, H);
                Bitmap colorBmp   = toColorOnly(src);
                Bitmap textureBmp = toTextureOnly(src);

                // 0..255
                float[][][][] inColor = bitmapToInput(colorBmp);
                float[][][][] inTex   = bitmapToInput(textureBmp);

                float[] probsColor = null, probsTex = null, probsFused = null, probsSingle = null;

                if (modelColorReady && itColor != null) {
                    float[][] outC = runSingle(itColor, inColor);
                    probsColor = (outC != null && outC.length>0) ? outC[0] : null;
                }
                if (modelTexReady && itTexture != null) {
                    float[][] outT = runSingle(itTexture, inTex);
                    probsTex = (outT != null && outT.length>0) ? outT[0] : null;
                }
                if (modelFusedReady && itFused != null) {
                    float[][] outF = runFused(itFused, inColor, inTex);
                    probsFused = (outF != null && outF.length>0) ? outF[0] : null;
                } else if (probsColor != null && probsTex != null) {
                    int n = Math.max(labels.length, Math.max(probsColor.length, probsTex.length));
                    probsFused = new float[n];
                    for (int i=0;i<n;i++) {
                        float c = (i < probsColor.length) ? probsColor[i] : 0f;
                        float t = (i < probsTex.length)   ? probsTex[i]   : 0f;
                        probsFused[i] = 0.5f*(c + t);
                    }
                }
                if (modelSingleReady && itSingle != null && probsColor == null && probsTex == null) {
                    float[][] outS = runSingle(itSingle, inColor);
                    probsSingle = (outS != null && outS.length>0) ? outS[0] : null;
                }

                Result rColor  = (probsColor  != null) ? argmax1D(probsColor)  : new Result(-1,0);
                Result rTex    = (probsTex    != null) ? argmax1D(probsTex)    : new Result(-1,0);
                Result rFused  = (probsFused  != null) ? argmax1D(probsFused)  : new Result(-1,0);
                Result rSingle = (probsSingle != null) ? argmax1D(probsSingle) : new Result(-1,0);

                // simpan untuk manual
                lastSrc = src;
                lastColorBmp = colorBmp;
                lastTextureBmp = textureBmp;
                lastInColor = inColor;
                lastInTex = inTex;

                int[][] R = tensorChannelToInt2D(lastInColor, 0);
                String snippetR = matrixSnippet(R, 10, 10);

                // siapkan string hasil untuk dikirim ke server
                String warnaStr, teksturStr, gabunganStr;
                if (modelSingleReady && !modelColorReady && !modelTexReady) {
                    warnaStr = formatPred(rSingle);
                    teksturStr = "-";
                    gabunganStr = "-";
                } else {
                    warnaStr = formatPred(rColor);
                    teksturStr = formatPred(rTex);
                    gabunganStr = formatPred(rFused);
                }

                runOnUiThread(() -> {
                    setBusy(false, "Selesai.");

                    tvManual.setText(
                            "Langkah:\n" +
                                    "1) Resize ke " + W + "x" + H + "\n" +
                                    "2) Warna-only (smoothing kuat)\n" +
                                    "3) Tekstur-only (Sobel magnitude)\n" +
                                    "4) Bentuk tensor [1," + H + "," + W + "," + C + "] 0..255\n\n" +
                                    "Cuplikan kanal R (10x10):\n" + snippetR
                    );
                    cardManual.setVisibility(View.VISIBLE);

                    if (modelSingleReady && !modelColorReady && !modelTexReady) {
                        showOrHide(cardWarna, tWPred, tWConf, rSingle);
                        cardTekstur.setVisibility(View.GONE);
                        cardFused.setVisibility(View.GONE);
                    } else {
                        showOrHide(cardWarna, tWPred, tWConf, rColor);
                        showOrHide(cardTekstur, tTPred, tTConf, rTex);
                        showOrHide(cardFused, tFPred, tFConf, rFused);
                    }

                    if (rColor.index < 0 && rTex.index < 0 && rFused.index < 0 && rSingle.index < 0) {
                        Toast.makeText(this, "Tidak ada hasil prediksi.", Toast.LENGTH_LONG).show();
                    }
                });

                // === SIMPAN HASIL KE SERVER (jalankan di thread ini) ===
                saveHasilToServer(userId, pickedUri, warnaStr, teksturStr, gabunganStr);

            } catch (Exception e) {
                Log.e(TAG, "onClassify: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    setBusy(false, "Gagal: " + e.getMessage());
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String formatPred(Result r){
        if (r.index < 0) return "-";
        String name = (r.index < labels.length && r.index >= 0) ? labels[r.index] : ("#" + r.index);
        return String.format(Locale.US, "%.1f%% %s", r.conf * 100f, name);
    }

    private void showOrHide(View card, android.widget.TextView pred, android.widget.TextView conf, Result r) {
        if (r.index < 0) { card.setVisibility(View.GONE); return; }
        card.setVisibility(View.VISIBLE);
        String name = (r.index >= 0 && r.index < labels.length) ? labels[r.index] : ("#" + r.index);
        pred.setText("Prediksi: " + name);
        conf.setText(String.format(Locale.US, "Confidence: %.1f%%", r.conf * 100f));
    }

    private static class Result { int index; float conf; Result(int i, float c){ index=i; conf=c; } }
    private Result argmax1D(float[] probs) {
        if (probs == null || probs.length == 0) return new Result(-1, 0f);
        int best = 0; float max = probs[0];
        for (int i = 1; i < probs.length; i++) if (probs[i] > max) { max = probs[i]; best = i; }
        return new Result(best, Math.max(0f, max));
    }

    private float[][] runSingle(Interpreter it, float[][][][] input) {
        float[][] out = new float[1][Math.max(labels.length, 3)];
        it.run(input, out);
        return out;
    }

    private float[][] runFused(Interpreter it, float[][][][] inColor, float[][][][] inTex) {
        float[][] out = new float[1][Math.max(labels.length, 3)];
        Object[] inputs = new Object[]{ inColor, inTex };
        Map<Integer,Object> outputs = new HashMap<>();
        outputs.put(0, out);
        it.runForMultipleInputsOutputs(inputs, outputs);
        return out;
    }

    /* ===============================
       Preprocess
       =============================== */
    private Bitmap loadBitmap(Uri uri, int w, int h) throws IOException {
        Bitmap src = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        return Bitmap.createScaledBitmap(src, w, h, true);
    }

    private Bitmap toColorOnly(Bitmap b) {
        Bitmap small = Bitmap.createScaledBitmap(b, 32, 32, true);
        return Bitmap.createScaledBitmap(small, W, H, true);
    }

    private Bitmap toTextureOnly(Bitmap b) {
        Bitmap g = toGray(b);
        int w = g.getWidth(), h = g.getHeight();
        int[] in = new int[w*h];
        g.getPixels(in, 0, w, 0, 0, w, h);
        float[] mag = new float[w*h];

        int[] gxK = {-1,0,1,-2,0,2,-1,0,1};
        int[] gyK = {-1,-2,-1,0,0,0,1,2,1};

        float[] gray = new float[w*h];
        for (int i=0;i<w*h;i++){
            int p = in[i];
            int r = (p>>16)&0xFF, gch = (p>>8)&0xFF, bch = p & 0xFF;
            gray[i] = 0.299f*r + 0.587f*gch + 0.114f*bch;
        }

        for (int y=1; y<h-1; y++) {
            for (int x=1; x<w-1; x++) {
                float sx=0, sy=0; int k=0;
                for (int j=-1;j<=1;j++){
                    for (int i=-1;i<=1;i++){
                        float v = gray[(y+j)*w + (x+i)];
                        sx += gxK[k]*v; sy += gyK[k]*v; k++;
                    }
                }
                float m = (float)Math.sqrt(sx*sx + sy*sy);
                mag[y*w+x] = m;
            }
        }
        float max = 1f;
        for (float v: mag) if (v>max) max=v;
        int[] out = new int[w*h];
        for (int i=0;i<w*h;i++){
            int v = (int)(mag[i]/max * 255f);
            out[i] = Color.rgb(v,v,v);
        }
        Bitmap tex = Bitmap.createBitmap(w,h, Bitmap.Config.ARGB_8888);
        tex.setPixels(out, 0, w, 0, 0, w, h);
        return Bitmap.createScaledBitmap(tex, W, H, true);
    }

    private Bitmap toGray(Bitmap b) {
        Bitmap r = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(r);
        ColorMatrix cm = new ColorMatrix(); cm.setSaturation(0f);
        android.graphics.Paint p = new android.graphics.Paint(); p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(b, 0, 0, p);
        return Bitmap.createScaledBitmap(r, W, H, true);
    }

    /** Kirim 0..255 (bukan [-1,1]) karena di model ada preprocess_input */
    private float[][][][] bitmapToInput(Bitmap b) {
        float[][][][] out = new float[1][H][W][C];
        int[] px = new int[W*H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        int idx=0;
        for (int y=0;y<H;y++){
            for (int x=0;x<W;x++){
                int p = px[idx++];
                out[0][y][x][0] = (p>>16)&0xFF;
                out[0][y][x][1] = (p>>8)&0xFF;
                out[0][y][x][2] = (p)&0xFF;
            }
        }
        return out;
    }

    // Loader labels: {"0":"A","1":"B"} atau ["A","B"] atau newline
    private String[] readLabels(File json) throws Exception {
        if (json == null || !json.exists() || json.length() == 0) return new String[0];
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(json))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        String s = sb.toString().trim();
        if (s.isEmpty()) return new String[0];

        if (s.startsWith("{")) {
            JSONObject obj = new JSONObject(s);
            java.util.ArrayList<String> list = new java.util.ArrayList<>();
            int i = 0;
            while (obj.has(String.valueOf(i))) { list.add(obj.getString(String.valueOf(i))); i++; }
            if (list.isEmpty()) {
                java.util.ArrayList<Integer> idxs = new java.util.ArrayList<>();
                java.util.Iterator<String> it = obj.keys();
                while (it.hasNext()) { String k = it.next(); try { idxs.add(Integer.parseInt(k)); } catch (Exception ignore) {} }
                java.util.Collections.sort(idxs);
                for (int id : idxs) list.add(obj.getString(String.valueOf(id)));
            }
            return list.toArray(new String[0]);
        } else if (s.startsWith("[")) {
            JSONArray arr = new JSONArray(s);
            String[] out = new String[arr.length()];
            for (int i=0;i<arr.length();i++) out[i] = arr.getString(i);
            return out;
        } else {
            String[] lines = s.split("\\r?\\n");
            java.util.ArrayList<String> list = new java.util.ArrayList<>();
            for (String line : lines) { String t = line.trim(); if (!t.isEmpty()) list.add(t); }
            return list.toArray(new String[0]);
        }
    }

    /* ===============================
       Manual Sheet + Export CSV
       =============================== */
    private void showManualSheet() {
        if (lastInColor == null || lastColorBmp == null || lastTextureBmp == null) {
            Toast.makeText(this, "Belum ada data manual.", Toast.LENGTH_SHORT).show();
            return;
        }
        com.google.android.material.bottomsheet.BottomSheetDialog d =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // ✅ pakai TextAppearance Material 3 (bukan Typeface)
        android.widget.TextView t1 = new android.widget.TextView(this);
        TextViewCompat.setTextAppearance(t1, android.graphics.Typeface.BOLD);
        t1.setText("Langkah Gambar");
        root.addView(t1);

        root.addView(labeledImage("A. Input ("+W+"x"+H+")", lastSrc));
        root.addView(space());
        root.addView(labeledImage("B. Warna-only (smoothing kuat)", lastColorBmp));
        root.addView(space());
        root.addView(labeledImage("C. Tekstur-only (Sobel, heatmap)", heatmap(lastTextureBmp)));

        root.addView(spaceBig());
        android.widget.TextView t2 = new android.widget.TextView(this);
        TextViewCompat.setTextAppearance(t2, android.graphics.Typeface.BOLD);
        t2.setText("Cuplikan Matriks 0..255 (16x16)");
        root.addView(t2);

        int[][] R = tensorChannelToInt2D(lastInColor, 0);
        int[][] G = tensorChannelToInt2D(lastInColor, 1);
        int[][] B = tensorChannelToInt2D(lastInColor, 2);
        root.addView(labeledText("Kanal R", matrixSnippet(R, 16, 16)));
        root.addView(space());
        root.addView(labeledText("Kanal G", matrixSnippet(G, 16, 16)));
        root.addView(space());
        root.addView(labeledText("Kanal B", matrixSnippet(B, 16, 16)));

        root.addView(spaceBig());
        android.widget.Button btnExport = new android.widget.Button(this);
        btnExport.setText("Ekspor CSV (R,G,B & Tekstur)");
        btnExport.setOnClickListener(v -> {
            try {
                // Folder publik: Downloads/Ekspor Hasil
                File base;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                } else {
                    base = Environment.getExternalStorageDirectory();
                }
                File dir = new File(base, "Ekspor Hasil");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Gagal membuat folder: " + dir.getAbsolutePath());

                File fR = new File(dir, "tensor_R.csv");
                File fG = new File(dir, "tensor_G.csv");
                File fB = new File(dir, "tensor_B.csv");
                File fT = new File(dir, "texture.csv");

                writeCsv2D(R, fR);
                writeCsv2D(G, fG);
                writeCsv2D(B, fB);

                int[][] T = bitmapToGray2D(lastTextureBmp);
                writeCsv2D(T, fT);

                Toast.makeText(this,
                        "Tersimpan di:\n"+dir.getAbsolutePath(),
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Gagal ekspor: "+e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        root.addView(btnExport);

        sc.addView(root);
        d.setContentView(sc);
        d.show();
    }

    private View space() {
        View v = new View(this);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(1, dp(8)));
        return v;
    }
    private View spaceBig() {
        View v = new View(this);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(1, dp(16)));
        return v;
    }
    private View labeledText(String title, String body) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView tv = new android.widget.TextView(this);
        TextViewCompat.setTextAppearance(tv, android.graphics.Typeface.BOLD);
        tv.setText(title);
        android.widget.TextView pre = new android.widget.TextView(this);
        pre.setTypeface(android.graphics.Typeface.MONOSPACE);
        pre.setText(body);
        pre.setTextSize(12);
        pre.setPadding(0, dp(4), 0, 0);
        box.addView(tv); box.addView(pre);
        return box;
    }
    private View labeledImage(String title, Bitmap bmp) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView tv = new android.widget.TextView(this);
        TextViewCompat.setTextAppearance(tv, android.graphics.Typeface.BOLD);
        tv.setText(title);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(bmp);
        iv.setPadding(0, dp(6), 0, 0);
        box.addView(tv); box.addView(iv);
        return box;
    }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density); }

    private int[][] tensorChannelToInt2D(float[][][][] t, int ch){
        int h = t[0].length, w = t[0][0].length;
        int[][] out = new int[h][w];
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                float v = t[0][y][x][ch];
                if (v < 0) v = 0; if (v > 255) v = 255;
                out[y][x] = Math.round(v);
            }
        }
        return out;
    }

    private int[][] bitmapToGray2D(Bitmap b){
        int w=b.getWidth(), h=b.getHeight();
        int[] px = new int[w*h];
        b.getPixels(px, 0, W, 0, 0, W, H);
        int[][] out = new int[h][w];
        int i=0;
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                int p = px[i++]; out[y][x] = p & 0xFF;
            }
        }
        return out;
    }

    private String matrixSnippet(int[][] m, int rows, int cols){
        StringBuilder sb = new StringBuilder();
        rows = Math.min(rows, m.length);
        cols = Math.min(cols, m[0].length);
        for(int y=0;y<rows;y++){
            for(int x=0;x<cols;x++){
                sb.append(String.format(Locale.US, "%3d", m[y][x]));
                if (x < cols-1) sb.append(' ');
            }
            if (y < rows-1) sb.append('\n');
        }
        return sb.toString();
    }

    private void writeCsv2D(int[][] m, File f) throws IOException {
        try (java.io.BufferedWriter w = new java.io.BufferedWriter(new java.io.FileWriter(f))) {
            for (int y=0;y<m.length;y++){
                for (int x=0;x<m[0].length;x++){
                    if (x>0) w.write(',');
                    w.write(Integer.toString(m[y][x]));
                }
                w.write('\n');
            }
        }
    }

    /** Ubah grayscale → heatmap warna (untuk visualisasi tekstur) */
    private Bitmap heatmap(Bitmap gray) {
        int w=gray.getWidth(), h=gray.getHeight();
        int[] in = new int[w*h];
        gray.getPixels(in, 0, w, 0, 0, w, h);
        int[] out = new int[w*h];
        for(int i=0;i<in.length;i++){
            int v = in[i] & 0xFF;
            float t = v/255f;
            int r = (int)(255*Math.max(0f, Math.min(1f, 1.5f*(t-0.33f))));
            int g = (int)(255*Math.max(0f, Math.min(1f, 1.5f*(t))));
            int b = (int)(255*Math.max(0f, Math.min(1f, 1.5f*(0.66f-t))));
            out[i] = Color.rgb(r,g,b);
        }
        Bitmap hm = Bitmap.createBitmap(w,h, Bitmap.Config.ARGB_8888);
        hm.setPixels(out, 0, w, 0, 0, w, h);
        return Bitmap.createScaledBitmap(hm, w*2, h*2, false);
    }

    /* ===============================
       SIMPAN HASIL KE SERVER
       =============================== */
    private void saveHasilToServer(String idUser, Uri imageUri,
                                   String warna, String tekstur, String gabungan) {
        try {
            // format waktu sekarang: 2025-01-01 08:00:00
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            String url = ApiConst.BASE_API_URL + "hasil";
            String boundary = "----AndroidBoundary" + System.currentTimeMillis();

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                // field text
                writeFormField(os, boundary, "id_user",  (idUser == null || idUser.isEmpty()) ? "0" : idUser);
                writeFormField(os, boundary, "warna",    (warna == null)    ? "-" : warna);
                writeFormField(os, boundary, "tekstur",  (tekstur == null)  ? "-" : tekstur);
                writeFormField(os, boundary, "gabungan", (gabungan == null) ? "-" : gabungan);
                writeFormField(os, boundary, "tanggal",  now); // ✅ kirim jam sekarang

                // field file
                InputStream is = getContentResolver().openInputStream(imageUri);
                byte[] img = readBytes(is);
                String mime = getContentResolver().getType(imageUri);
                if (mime == null || mime.trim().isEmpty()) mime = "image/jpeg";
                writeFileField(os, boundary, "image", "input.jpg", mime, img);

                // akhir multipart
                os.writeBytes("--" + boundary + "--\r\n");
                os.flush();
            }

            int code = conn.getResponseCode();
            String resp = readAll((code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();

            runOnUiThread(() -> {
                if (code >= 200 && code < 300) {
                    Toast.makeText(this, "Hasil tersimpan di server.", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Gagal simpan (" + code + "): " + resp);
                    Toast.makeText(this, "Gagal simpan (" + code + "): " + resp, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            runOnUiThread(() ->
                    Toast.makeText(this, "Gagal simpan: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

    private void writeFormField(DataOutputStream os, String boundary, String name, String value) throws IOException {
        os.writeBytes("--" + boundary + "\r\n");
        os.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        os.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        os.write(value.getBytes("UTF-8"));
        os.writeBytes("\r\n");
    }

    private void writeFileField(DataOutputStream os, String boundary, String name,
                                String filename, String mime, byte[] data) throws IOException {
        os.writeBytes("--" + boundary + "\r\n");
        os.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
        os.writeBytes("Content-Type: " + mime + "\r\n\r\n");
        os.write(data);
        os.writeBytes("\r\n");
    }
}
