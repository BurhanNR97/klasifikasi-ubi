package com.klasifikasi.ubi.UI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.adapter.DatasetAdapter;
import com.klasifikasi.ubi.model.DatasetItem;
import com.klasifikasi.ubi.model.Sampel;
import com.klasifikasi.ubi.model.StartTrainRequest;
import com.klasifikasi.ubi.model.TrainStatus;
import com.klasifikasi.ubi.net.ApiService;
import com.klasifikasi.ubi.net.ProgressRequestBody;
import com.klasifikasi.ubi.net.RetrofitClient;
import com.klasifikasi.ubi.utils.UrlUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DatasetList extends AppCompatActivity {

    private DatasetAdapter adapter;
    private ProgressDialog progress;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollTask;

    // 1 = split 70/30, >1 = k-fold (default 5)
    private int selectedKFold = 5;

    @SuppressLint("MissingInflatedId")
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dataset_list);

        View root = findViewById(R.id.mainDatasetList);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0, 0);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar_dataset);
        if (toolbar == null) toolbar = findViewById(R.id.toolbar_sampel);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(DatasetList.this, AdminDashboard.class);
                intent.putExtra("nama", getIntent().getStringExtra("nama"));
                intent.putExtra("id", getIntent().getStringExtra("id"));
                startActivity(intent);
                finish();
            });
        }

        RecyclerView rv = findViewById(R.id.rvDataset);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DatasetAdapter(d -> {
            if (d == null) { Toast.makeText(this, "Dataset kosong", Toast.LENGTH_SHORT).show(); return; }
            String rel = d.getFile();
            if (rel == null || rel.trim().isEmpty()) {
                Toast.makeText(this, "Tidak ada file untuk diunduh", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = UrlUtil.buildPublicUrl(rel);
            if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            else Toast.makeText(this, "URL tidak valid", Toast.LENGTH_SHORT).show();
        });
        rv.setAdapter(adapter);

        findViewById(R.id.fabAddDataset).setOnClickListener(v -> onAddDataset());
        loadList();
    }

    private void loadList() {
        RetrofitClient.api().listDataset().enqueue(new Callback<List<DatasetItem>>() {
            @Override public void onResponse(Call<List<DatasetItem>> call, Response<List<DatasetItem>> resp) {
                if (resp.isSuccessful() && resp.body()!=null) adapter.setData(resp.body());
                else Toast.makeText(DatasetList.this, "Gagal load dataset " + resp.code(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<List<DatasetItem>> call, Throwable t) {
                Toast.makeText(DatasetList.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================== Buat dataset dari sampel ==================

    private void onAddDataset() {
        RetrofitClient.api().listSampel(null).enqueue(new Callback<List<Sampel>>() {
            @Override public void onResponse(Call<List<Sampel>> call, Response<List<Sampel>> resp) {
                if (!resp.isSuccessful() || resp.body()==null) {
                    Toast.makeText(DatasetList.this, "Gagal ambil sampel ("+resp.code()+")", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Sampel> items = resp.body();
                Map<String,Integer> count = countByClass(items);

                String pesan = "Apakah Anda ingin membuat dataset?\n\n" +
                        "Ubi Ungu : " + count.getOrDefault("Ubi Ungu", 0) + "\n" +
                        "Ubi Putih: " + count.getOrDefault("Ubi Putih", 0) + "\n" +
                        "Ubi Jingga: " + count.getOrDefault("Ubi Jingga", 0);

                final String[] kChoices = {"1 (Split 70/30)", "5-Fold"};
                final int[] kValues = {1, 5};
                selectedKFold = 5;

                new AlertDialog.Builder(DatasetList.this)
                        .setTitle("Pilih Metode Training")
                        .setMessage(pesan)
                        .setSingleChoiceItems(kChoices, 1, (d,w) -> selectedKFold = kValues[w])
                        .setPositiveButton("Mulai", (d,w) -> startBuildAndUpload(items, count))
                        .setNegativeButton("Batal", null)
                        .show();
            }
            @Override public void onFailure(Call<List<Sampel>> call, Throwable t) {
                Toast.makeText(DatasetList.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String norm(String jenis) {
        if (jenis == null) return "";
        String j = jenis.trim().toLowerCase(Locale.ROOT);
        if (j.contains("ungu") || j.contains("ungi")) return "Ubi Ungu";
        if (j.contains("putih")) return "Ubi Putih";
        if (j.contains("jingga") || j.contains("orange")) return "Ubi Jingga";
        return jenis.trim();
    }

    private Map<String,Integer> countByClass(List<Sampel> items){
        Map<String,Integer> c = new HashMap<>();
        for (Sampel s: items) {
            String k = norm(s.jenis);
            c.put(k, c.getOrDefault(k, 0) + 1);
        }
        return c;
    }

    private void startBuildAndUpload(List<Sampel> items, Map<String,Integer> count) {
        progress = new ProgressDialog(this);
        progress.setTitle("Membuat dataset");
        progress.setMessage("Menyiapkan...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                File outDir = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ? getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        : getExternalFilesDir(null);
                if (outDir != null && !outDir.exists()) outDir.mkdirs();

                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File zipFile = new File(outDir, "dataset_" + stamp + ".zip");

                makeZip(items, zipFile, p -> updateProgress((int)(p * 0.7)));

                String nama = "Ungu: " + count.getOrDefault("Ubi Ungu", 0)
                        + " • Putih: " + count.getOrDefault("Ubi Putih", 0)
                        + " • Jingga: " + count.getOrDefault("Ubi Jingga", 0);

                uploadZip(zipFile, nama);
            } catch (Exception e) {
                main.post(() -> {
                    if (progress != null) progress.dismiss();
                    Toast.makeText(DatasetList.this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private interface IntCallback { void call(int percent); }

    private void makeZip(List<Sampel> items, File outZip, IntCallback cb) throws Exception {
        OkHttpClient http = new OkHttpClient();
        int n = items.size(), i = 0;

        try (FileOutputStream fos = new FileOutputStream(outZip);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            for (Sampel s : items) {
                i++;
                if (s.foto == null || s.foto.trim().isEmpty()) continue;

                String url = UrlUtil.buildPublicUrl(s.foto);
                if (url == null) continue;

                okhttp3.Response resp = http.newCall(new Request.Builder().url(url).build()).execute();
                if (!resp.isSuccessful() || resp.body() == null) { if (resp.body()!=null) resp.close(); continue; }
                byte[] bytes = resp.body().bytes();
                resp.close();

                String entry = norm(s.jenis).replace(" ", "_") + "/" + s.id + ".jpg";
                zos.putNextEntry(new ZipEntry(entry));
                zos.write(bytes);
                zos.closeEntry();

                int pct = Math.min(100, (int)Math.round(i * 100.0 / Math.max(1,n)));
                cb.call(pct);
            }
            zos.finish();
        }
    }

    private void updateProgress(int pct){ main.post(() -> { if (progress != null) progress.setProgress(pct); }); }

    private void uploadZip(File zipFile, String nama) {
        ProgressRequestBody.Listener up = (wrote,total) -> {
            int pctUp = total > 0 ? (int)(wrote * 100 / total) : 0;
            int overall = 70 + (pctUp * 30 / 100);
            updateProgress(overall);
        };

        RequestBody namaBody = RequestBody.create(okhttp3.MediaType.parse("text/plain"), nama);
        ProgressRequestBody fileBody = new ProgressRequestBody(zipFile, "application/zip", up);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", zipFile.getName(), fileBody);

        RetrofitClient.api().uploadDataset(namaBody, part).enqueue(new Callback<DatasetItem>() {
            @Override public void onResponse(Call<DatasetItem> call, Response<DatasetItem> resp) {
                if (!resp.isSuccessful() || resp.body()==null) {
                    if (progress!=null) progress.dismiss();
                    Toast.makeText(DatasetList.this, "Upload gagal ("+resp.code()+")", Toast.LENGTH_LONG).show();
                    return;
                }
                loadList();

                DatasetItem saved = resp.body();
                if (progress != null) {
                    progress.setTitle("Training model");
                    progress.setMessage("Menunggu worker...");
                    progress.setProgress(0);
                    progress.show();
                }
                // ⬇️ Mulai training SEKALI saja (tanpa stream/summary/cm)
                startTraining(saved.id, selectedKFold);
            }
            @Override public void onFailure(Call<DatasetItem> call, Throwable t) {
                if (progress!=null) progress.dismiss();
                Toast.makeText(DatasetList.this, "Error upload: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================== Start training (simple) & polling ==================

    private void startTraining(int datasetId, int kfold) {
        int epochs = (kfold > 1) ? 5 : 10;
        String task = (kfold > 1) ? "kfold" : "split";

        StartTrainRequest body = new StartTrainRequest(kfold, task, epochs);
        // ⚠️ Jangan set field lain (stream/kfold_summary_only/cm_only)

        RetrofitClient.api().startTrain(datasetId, body).enqueue(new Callback<TrainStatus>() {
            @Override public void onResponse(Call<TrainStatus> call, Response<TrainStatus> resp) {
                if (!resp.isSuccessful()) {
                    if (progress!=null) progress.dismiss();
                    Toast.makeText(DatasetList.this, "Gagal mulai training: "+resp.code(), Toast.LENGTH_LONG).show();
                    return;
                }
                pollUntilFinish(datasetId);
            }
            @Override public void onFailure(Call<TrainStatus> call, Throwable t) {
                if (progress!=null) progress.dismiss();
                Toast.makeText(DatasetList.this, "Error start: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void pollUntilFinish(int datasetId) {
        stopPolling();
        pollTask = new Runnable() {
            @Override public void run() {
                RetrofitClient.api().getTrainStatus(datasetId).enqueue(new Callback<TrainStatus>() {
                    @Override public void onResponse(Call<TrainStatus> call, Response<TrainStatus> resp) {
                        if (!resp.isSuccessful() || resp.body()==null) {
                            pollHandler.postDelayed(pollTask, 1500);
                            return;
                        }
                        TrainStatus s = resp.body();
                        if (progress != null) {
                            progress.setProgress(Math.max(0, Math.min(100, s.progress)));
                            if (s.message != null) progress.setMessage(s.message);
                        }
                        String st = (s.status==null) ? "" : s.status.toLowerCase(Locale.ROOT);
                        if ("success".equals(st) || "failed".equals(st)) {
                            stopPolling();
                            if (progress != null) progress.dismiss();
                            showResultDialog(s);
                            loadList();
                        } else {
                            pollHandler.postDelayed(pollTask, 1500);
                        }
                    }
                    @Override public void onFailure(Call<TrainStatus> call, Throwable t) {
                        pollHandler.postDelayed(pollTask, 1800);
                    }
                });
            }
        };
        pollHandler.post(pollTask);
    }

    private void stopPolling() {
        if (pollTask != null) { pollHandler.removeCallbacks(pollTask); pollTask = null; }
    }

    private void showResultDialog(TrainStatus s) {
        new AlertDialog.Builder(this)
                .setTitle("Training Selesai")
                .setMessage("Model & metrik tersedia. Buka dari halaman dataset untuk melihat artefak.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (progress != null && progress.isShowing()) progress.dismiss();
    }
}
