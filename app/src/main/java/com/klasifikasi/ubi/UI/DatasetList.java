// com/klasifikasi/ubi/UI/DatasetList.java
package com.klasifikasi.ubi.UI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.util.Log;
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
import com.klasifikasi.ubi.model.TrainStatus;
import com.klasifikasi.ubi.net.ApiService;
import com.klasifikasi.ubi.net.ProgressRequestBody;
import com.klasifikasi.ubi.net.RetrofitClient;
import com.klasifikasi.ubi.utils.UrlUtil;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
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
    private MaterialToolbar toolbar;

    // ==== training polling ====
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollTask = null;
    private int currentTrainingId = -1;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dataset_list);

        View root = findViewById(R.id.mainDatasetList);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v,insets)->{
            v.setPadding(0,insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,0,0);
            return insets;
        });

        toolbar = findViewById(R.id.toolbar_dataset);
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
        adapter = new DatasetAdapter(new DatasetAdapter.Listener() {
            @Override public void onDownload(com.klasifikasi.ubi.model.DatasetItem d) {
                String url = com.klasifikasi.ubi.utils.UrlUtil.buildPublicUrl(d.file);
                if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.fabAddDataset).setOnClickListener(v -> onAddDataset());

        loadList();
    }

    private void loadList() {
        RetrofitClient.api().listDataset().enqueue(new Callback<List<DatasetItem>>() {
            @Override public void onResponse(Call<List<DatasetItem>> call, Response<List<DatasetItem>> resp) {
                if (resp.isSuccessful() && resp.body()!=null) adapter.setData(resp.body());
                else Toast.makeText(DatasetList.this, "Gagal load dataset "+resp.code(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<List<DatasetItem>> call, Throwable t) {
                Toast.makeText(DatasetList.this, "Error: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // === 1) Dialog konfirmasi & hitung jumlah kelas ===
    private void onAddDataset() {
        RetrofitClient.api().listSampel(null).enqueue(new Callback<List<Sampel>>() {
            @Override public void onResponse(Call<List<Sampel>> call, Response<List<Sampel>> resp) {
                if (!resp.isSuccessful() || resp.body()==null) {
                    Toast.makeText(DatasetList.this, "Gagal ambil sampel ("+resp.code()+")", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Sampel> items = resp.body();
                Map<String,Integer> count = countByClass(items);

                String pesan = "Apakah Anda ingin membuat dataset?\n\n"
                        + "Ubi Ungu : " + count.getOrDefault("Ubi Ungu",0) + "\n"
                        + "Ubi Putih: " + count.getOrDefault("Ubi Putih",0) + "\n"
                        + "Ubi Jingga: " + count.getOrDefault("Ubi Jingga",0);

                new AlertDialog.Builder(DatasetList.this)
                        .setTitle("Buat Dataset")
                        .setMessage(pesan)
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Mulai", (d,w) -> startBuildAndUpload(items, count))
                        .show();
            }
            @Override public void onFailure(Call<List<Sampel>> call, Throwable t) {
                Toast.makeText(DatasetList.this, "Error: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String norm(String jenis) {
        if (jenis==null) return "";
        String j = jenis.trim().toLowerCase();
        if (j.contains("ungu") || j.contains("ungi")) return "Ubi Ungu";
        if (j.contains("putih")) return "Ubi Putih";
        if (j.contains("jingga") || j.contains("orange")) return "Ubi Jingga";
        return jenis.trim();
    }

    private Map<String,Integer> countByClass(List<Sampel> items) {
        Map<String,Integer> c = new HashMap<>();
        for (Sampel s: items) {
            String k = norm(s.jenis);
            c.put(k, c.getOrDefault(k,0)+1);
        }
        return c;
    }

    // === 2) Build ZIP + upload (progress 0..70 zipping, 70..100 upload) ===
    private void startBuildAndUpload(List<Sampel> items, Map<String,Integer> count) {
        progress = new ProgressDialog(this);
        progress.setTitle("Membuat dataset");
        progress.setMessage("Menyiapkan...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                File outDir = new File(getExternalFilesDir(null), "dataset");
                if (!outDir.exists()) outDir.mkdirs();
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File zipFile = new File(outDir, "dataset_"+stamp+".zip");

                int total = items.size();
                if (total == 0) throw new RuntimeException("Tidak ada sampel bergambar.");

                // 0–70% saat zipping
                makeZip(items, zipFile, p -> updateProgress((int)(p * 0.7)));

                String nama = "Ungu: "+count.getOrDefault("Ubi Ungu",0)
                        +" • Putih: "+count.getOrDefault("Ubi Putih",0)
                        +" • Jingga: "+count.getOrDefault("Ubi Jingga",0);

                // lanjut upload → onResponse akan auto start training
                uploadZip(zipFile, nama);

            } catch (Exception e) {
                Log.e("DATASET", "Gagal: "+e.getMessage(), e);
                main.post(() -> {
                    if (progress!=null) progress.dismiss();
                    Toast.makeText(DatasetList.this, "Gagal: "+e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private interface IntCallback { void call(int percent); }

    private void makeZip(List<Sampel> items, File outZip, IntCallback progressCb) throws Exception {
        OkHttpClient http = new OkHttpClient();
        int n = items.size(), i = 0;

        try (FileOutputStream fos = new FileOutputStream(outZip);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            for (Sampel s : items) {
                i++;
                String cls = norm(s.jenis);
                if (s.foto==null || s.foto.trim().isEmpty()) continue;

                String url = buildFotoUrl(s.foto);
                if (url == null) continue;

                okhttp3.Response resp = http.newCall(new Request.Builder().url(url).build()).execute();
                if (!resp.isSuccessful() || resp.body()==null) {
                    if (resp.body()!=null) resp.close();
                    continue;
                }
                byte[] bytes = resp.body().bytes();
                resp.close();

                String entryName = cls.replace(" ", "_") + "/" + s.id + ".jpg";
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(bytes);
                zos.closeEntry();

                int pct = Math.min(100, (int)Math.round((i * 100.0) / n));
                progressCb.call(pct);
            }
            zos.finish();
        }
    }

    private String buildFotoUrl(String rel) { return UrlUtil.buildPublicUrl(rel); }

    private void updateProgress(int pct) {
        main.post(() -> { if (progress != null) progress.setProgress(pct); });
    }

    private void uploadZip(File zipFile, String nama) {
        ProgressRequestBody.Listener listener = (wrote,total) -> {
            int pctUp = total>0 ? (int)(wrote * 100 / total) : 0;
            int overall = 70 + (pctUp * 30 / 100); // 70..100
            updateProgress(overall);
        };

        RequestBody namaBody = RequestBody.create(okhttp3.MediaType.parse("text/plain"), nama);
        ProgressRequestBody fileBody = new ProgressRequestBody(zipFile, "application/zip", listener);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", zipFile.getName(), fileBody);

        ApiService api = RetrofitClient.api();
        api.uploadDataset(namaBody, part).enqueue(new Callback<DatasetItem>() {
            @Override public void onResponse(Call<DatasetItem> call, Response<DatasetItem> resp) {
                if (!resp.isSuccessful() || resp.body()==null) {
                    if (progress!=null) progress.dismiss();
                    String err = "";
                    try { if (resp.errorBody()!=null) err = resp.errorBody().string(); } catch (Exception ignored) {}
                    Toast.makeText(DatasetList.this,
                            "Upload gagal ("+resp.code()+") "+err, Toast.LENGTH_LONG).show();
                    return;
                }

                // upload sukses → refresh list
                loadList();

                // === Auto mulai training ===
                DatasetItem saved = resp.body();
                currentTrainingId = saved.id;

                // reset dialog untuk training
                if (progress != null) {
                    progress.setTitle("Training model");
                    progress.setMessage("Menunggu worker...");
                    progress.setProgress(0);
                    progress.show();
                }

                startTraining(saved.id);
            }

            @Override public void onFailure(Call<DatasetItem> call, Throwable t) {
                if (progress!=null) progress.dismiss();
                Toast.makeText(DatasetList.this, "Error upload: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // === 3) Mulai training & polling status ===
    private void startTraining(int datasetId) {
        RetrofitClient.api().startTrain(datasetId).enqueue(new Callback<TrainStatus>() {
            @Override public void onResponse(Call<TrainStatus> call, Response<TrainStatus> resp) {
                if (!resp.isSuccessful()) {
                    if (progress!=null) progress.dismiss();
                    Toast.makeText(DatasetList.this, "Gagal mulai training ("+resp.code()+")", Toast.LENGTH_LONG).show();
                    return;
                }
                // mulai polling tiap 1.5s
                startPolling(datasetId);
            }
            @Override public void onFailure(Call<TrainStatus> call, Throwable t) {
                if (progress!=null) progress.dismiss();
                Toast.makeText(DatasetList.this, "Error start training: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startPolling(int datasetId) {
        stopPolling(); // pastikan 1 task saja
        pollTask = new Runnable() {
            @Override public void run() {
                RetrofitClient.api().getTrainStatus(datasetId).enqueue(new Callback<TrainStatus>() {
                    @Override public void onResponse(Call<TrainStatus> call, Response<TrainStatus> resp) {
                        if (!resp.isSuccessful() || resp.body()==null) {
                            // coba lagi nanti
                            pollHandler.postDelayed(pollTask, 1500);
                            return;
                        }
                        TrainStatus s = resp.body();
                        // update UI progress & message
                        if (progress != null) {
                            progress.setProgress(Math.max(0, Math.min(100, s.progress)));
                            if (s.message != null) progress.setMessage(s.message);
                        }

                        if ("success".equalsIgnoreCase(s.status)) {
                            if (progress != null) progress.dismiss();
                            stopPolling();
                            showResultDialog(s);
                            loadList(); // refresh list dataset (kalau ada kolom trained_at dsb)
                        } else if ("failed".equalsIgnoreCase(s.status)) {
                            if (progress != null) progress.dismiss();
                            stopPolling();
                            Toast.makeText(DatasetList.this,
                                    "Training gagal" + (s.message!=null? (": "+s.message):""),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            // running → lanjut polling
                            pollHandler.postDelayed(pollTask, 1500);
                        }
                    }
                    @Override public void onFailure(Call<TrainStatus> call, Throwable t) {
                        // jeda dulu lalu coba lagi
                        pollHandler.postDelayed(pollTask, 2000);
                    }
                });
            }
        };
        pollHandler.post(pollTask);
    }

    private void stopPolling() {
        if (pollTask != null) {
            pollHandler.removeCallbacks(pollTask);
            pollTask = null;
        }
    }

    private void showResultDialog(TrainStatus s) {
        StringBuilder sb = new StringBuilder();
        if (s.metrics != null) {
            sb//.append("Akurasi akhir: ")
                    //.append(String.format(Locale.US, "%.2f%%", s.metrics.val_accuracy_last*100))
                    .append("\nKelas: ").append(s.metrics.classes);
        }
        new AlertDialog.Builder(this)
                .setTitle("Training Selesai")
                .setMessage(sb.length()>0 ? sb.toString() : "Model berhasil dibuat.")
                .setPositiveButton("Unduh TFLite", (d,w) -> {
                    String url = UrlUtil.buildPublicUrl(s.result_tflite);
                    if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                })
                .setNeutralButton("Unduh H5", (d,w) -> {
                    String url = UrlUtil.buildPublicUrl(s.result_model);
                    if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                })
                .setNegativeButton("Labels", (d,w) -> {
                    String url = UrlUtil.buildPublicUrl(s.result_labels);
                    if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                })
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (progress != null && progress.isShowing()) progress.dismiss();
    }
}
