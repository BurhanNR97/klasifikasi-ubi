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
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;

public class DatasetList extends AppCompatActivity {

    private DatasetAdapter adapter;
    private ProgressDialog progress;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MaterialToolbar toolbar;

    // polling
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollTask = null;

    // ===== Opsi training (disederhanakan) =====
    // Hanya 2 pilihan: Split (1) atau 5-Fold. Opsi lain auto-ON sesuai mode.
    private int selectedKFold = 1;               // 1 = split 70/30, >1 = kfold
    private boolean selectedSummaryOnly = true;  // k-fold: ringkasan saja
    private boolean selectedCmOnly = true;       // split: hanya Confusion Matrix

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dataset_list);

        View root = findViewById(R.id.mainDatasetList);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0, 0);
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
            @Override public void onDownload(DatasetItem d) {
                String rel = (d != null) ? d.getFile() : null;
                if (rel == null || rel.trim().isEmpty()) {
                    Toast.makeText(DatasetList.this, "Tidak ada file untuk diunduh", Toast.LENGTH_SHORT).show();
                    return;
                }
                String url = UrlUtil.buildPublicUrl(rel);
                if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.fabAddDataset).setOnClickListener(v -> onAddDataset());
        loadList();
    }

    private void loadList() {
        RetrofitClient.api().listDataset().enqueue(new Callback<List<DatasetItem>>() {
            @Override public void onResponse(Call<List<DatasetItem>> call, retrofit2.Response<List<DatasetItem>> resp) {
                if (resp.isSuccessful() && resp.body()!=null) adapter.setData(resp.body());
                else Toast.makeText(DatasetList.this, "Gagal load dataset "+resp.code(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<List<DatasetItem>> call, Throwable t) {
                Toast.makeText(DatasetList.this, "Error: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===== 1) Konfirmasi pembuatan dataset =====
    private void onAddDataset() {
        RetrofitClient.api().listSampel(null).enqueue(new Callback<List<Sampel>>() {
            @Override public void onResponse(Call<List<Sampel>> call, retrofit2.Response<List<Sampel>> resp) {
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
                        .setPositiveButton("Mulai", (d,w) -> askTrainOptionsThenBuild(items, count))
                        .show();
            }
            @Override public void onFailure(Call<List<Sampel>> call, Throwable t) {
                Toast.makeText(DatasetList.this, "Error: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===== 1b) Hanya pilih Split vs 5-Fold (opsi lain auto) =====
    private void askTrainOptionsThenBuild(List<Sampel> items, Map<String,Integer> count) {
        final String[] kChoices = {"1 (Split 70/30)", "5-Fold"};
        final int[] kValues = {1, 5};
        selectedKFold = 1; // default split

        // Opsi lain otomatis ON (sesuai requirement)
        selectedSummaryOnly = true; // k-fold ⇒ ringkasan saja (server akan pakai ini saat kfold>1)
        selectedCmOnly = true;      // split ⇒ hanya Confusion Matrix (server akan pakai ini saat kfold==1)

        new AlertDialog.Builder(this)
                .setTitle("Pilih Metode Training")
                .setSingleChoiceItems(kChoices, 0, (dialog, which) -> selectedKFold = kValues[which])
                .setPositiveButton("Lanjut", (dialog, which) -> startBuildAndUpload(items, count))
                .setNegativeButton("Batal", null)
                .show();
    }

    private static String norm(String jenis) {
        if (jenis==null) return "";
        String j = jenis.trim().toLowerCase(Locale.ROOT);
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

    // ===== 2) Zip → Upload =====
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

                makeZip(items, zipFile, p -> updateProgress((int)(p * 0.7)));

                String nama = "Ungu: "+count.getOrDefault("Ubi Ungu",0)
                        +" • Putih: "+count.getOrDefault("Ubi Putih",0)
                        +" • Jingga: "+count.getOrDefault("Ubi Jingga",0);

                uploadZip(zipFile, nama);
            } catch (Exception e) {
                Log.e("DATASET", "Gagal: "+e.getMessage(), e);
                main.post(() -> {
                    if (progress!=null && progress.isShowing()) progress.dismiss();
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

                String url = UrlUtil.buildPublicUrl(s.foto);
                if (url == null) continue;

                Response resp = http.newCall(new Request.Builder().url(url).build()).execute();
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
            @Override public void onResponse(Call<DatasetItem> call, retrofit2.Response<DatasetItem> resp) {
                if (!resp.isSuccessful() || resp.body()==null) {
                    if (progress!=null) progress.dismiss();
                    String err = "";
                    try { if (resp.errorBody()!=null) err = resp.errorBody().string(); } catch (Exception ignored) {}
                    Toast.makeText(DatasetList.this,
                            "Upload gagal ("+resp.code()+") "+err, Toast.LENGTH_LONG).show();
                    return;
                }
                loadList();

                // === jalankan otomatis: color → texture → fused
                DatasetItem saved = resp.body();
                if (progress != null) {
                    progress.setTitle("Training model");
                    progress.setMessage("Menunggu worker...");
                    progress.setProgress(0);
                    progress.show();
                }
                // pastikan flag sesuai mode terpilih
                boolean summaryOnly = selectedKFold > 1;
                boolean cmOnly = selectedKFold <= 1;
                startTrainingAllSequential(saved.id, selectedKFold, summaryOnly, cmOnly);
            }

            @Override public void onFailure(Call<DatasetItem> call, Throwable t) {
                if (progress!=null) progress.dismiss();
                Toast.makeText(DatasetList.this, "Error upload: "+t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===== 3) Training otomatis berurutan (dengan progress gabungan stream×fold) =====
    private void startTrainingAllSequential(int datasetId, int kfold, boolean summaryOnly, boolean cmOnly) {
        List<String> queue = new ArrayList<>(Arrays.asList("color", "texture", "fused"));
        int totalStreams = queue.size();
        runNext(datasetId, kfold, queue, summaryOnly, cmOnly, 0, totalStreams);
    }

    private void runNext(int datasetId, int kfold, List<String> queue,
                         boolean summaryOnly, boolean cmOnly,
                         int streamIndex, int totalStreams) {
        if (queue.isEmpty()) {
            if (progress != null) progress.dismiss();
            RetrofitClient.api().getTrainStatus(datasetId).enqueue(new Callback<TrainStatus>() {
                @Override public void onResponse(Call<TrainStatus> call, retrofit2.Response<TrainStatus> resp) {
                    if (resp.isSuccessful() && resp.body()!=null) showResultDialog(resp.body());
                    loadList();
                }
                @Override public void onFailure(Call<TrainStatus> call, Throwable t) { loadList(); }
            });
            return;
        }
        String stream = queue.remove(0);
        if (progress != null) {
            progress.setTitle("Training (" + stream + ")");
            progress.setMessage("Menunggu worker...");
            progress.setProgress(0);
            progress.show();
        }

        startTrainingSingle(datasetId, kfold, stream, summaryOnly, cmOnly,
                streamIndex, totalStreams,
                () -> runNext(datasetId, kfold, queue, summaryOnly, cmOnly, streamIndex+1, totalStreams));
    }

    private void startTrainingSingle(int datasetId, int kfold, String stream,
                                     boolean summaryOnly, boolean cmOnly,
                                     int streamIndex, int totalStreams,
                                     Runnable onDone) {
        int epochs = (kfold > 1) ? 5 : 10;          // selaras dengan worker
        String task = (kfold > 1) ? "kfold" : "split";

        StartTrainRequest body = new StartTrainRequest(kfold, task, epochs);
        body.stream = stream;                        // "color" | "texture" | "fused"
        body.kfold_summary_only = (kfold > 1) && summaryOnly; // aman walau dipanggil dari mana saja
        body.cm_only = (kfold <= 1) && cmOnly;

        RetrofitClient.api().startTrain(datasetId, body)
                .enqueue(new Callback<TrainStatus>() {
                    @Override public void onResponse(Call<TrainStatus> call, retrofit2.Response<TrainStatus> resp) {
                        if (!resp.isSuccessful()) {
                            if (progress!=null) progress.dismiss();
                            Toast.makeText(DatasetList.this, "Gagal mulai ("+stream+"): "+resp.code(), Toast.LENGTH_LONG).show();
                            onDone.run();
                            return;
                        }
                        // totalStages = totalStreams * kfold (kalau kfold==1, maka = totalStreams)
                        int folds = Math.max(1, body.kfold);
                        int totalStages = totalStreams * folds;
                        int baseStageIndex = streamIndex * folds; // offset tahap untuk stream ini
                        pollUntilFinish(datasetId, stream, folds, baseStageIndex, totalStages, onDone);
                    }
                    @Override public void onFailure(Call<TrainStatus> call, Throwable t) {
                        if (progress!=null) progress.dismiss();
                        Toast.makeText(DatasetList.this, "Error start ("+stream+"): "+t.getMessage(), Toast.LENGTH_LONG).show();
                        onDone.run();
                    }
                });
    }

    private void pollUntilFinish(int datasetId,
                                 String streamName,
                                 int foldsForThisStream,
                                 int baseStageIndex,  // offset tahap berdasarkan stream
                                 int totalStages,     // semua tahap (stream x folds)
                                 Runnable onFinish) {
        stopPolling();

        pollTask = new Runnable() {
            int currentFold = 1;

            @Override public void run() {
                RetrofitClient.api().getTrainStatus(datasetId).enqueue(new Callback<TrainStatus>() {
                    @Override public void onResponse(Call<TrainStatus> call, retrofit2.Response<TrainStatus> resp) {
                        if (!resp.isSuccessful() || resp.body()==null) {
                            pollHandler.postDelayed(pollTask, 1500);
                            return;
                        }
                        TrainStatus s = resp.body();

                        int foldNow = extractFoldFromMessage(s.message, currentFold);
                        currentFold = Math.max(1, Math.min(foldsForThisStream, foldNow));

                        int intra = Math.max(0, Math.min(100, s.progress));

                        int stageIndex = baseStageIndex + (currentFold - 1);
                        float perStageSize = 100f / Math.max(1, totalStages);
                        int overall = Math.round(stageIndex * perStageSize + (intra/100f) * perStageSize);

                        if (progress != null) {
                            progress.setProgress(Math.max(0, Math.min(100, overall)));
                            String label = (foldsForThisStream > 1)
                                    ? (streamName + " (fold " + currentFold + "/" + foldsForThisStream + ")")
                                    : streamName;
                            if (s.message != null && !s.message.trim().isEmpty())
                                progress.setMessage(label + " • " + s.message);
                            else
                                progress.setMessage(label);
                            progress.setTitle("Training " + label);
                        }

                        String st = (s.status == null) ? "" : s.status.toLowerCase(Locale.ROOT);
                        if ("success".equals(st) || "failed".equals(st)) {
                            stopPolling();
                            onFinish.run();
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

    private int extractFoldFromMessage(@Nullable String msg, int fallback) {
        if (msg == null) return fallback;
        try {
            String m = msg.toLowerCase(Locale.ROOT);
            int i = m.indexOf("fold ");
            if (i >= 0) {
                String sub = m.substring(i + 5);
                String digits = "";
                for (int k=0; k<sub.length(); k++) {
                    char c = sub.charAt(k);
                    if (Character.isDigit(c)) digits += c;
                    else break;
                }
                if (!digits.isEmpty()) return Math.max(1, Integer.parseInt(digits));
            }
        } catch (Exception ignore) {}
        return fallback;
    }

    private void stopPolling() {
        if (pollTask != null) {
            pollHandler.removeCallbacks(pollTask);
            pollTask = null;
        }
    }

    // ===== Hasil =====
    private void showResultDialog(TrainStatus s) {
        new AlertDialog.Builder(this)
                .setTitle("Training Selesai")
                .setMessage("Model & metrik tersedia. Buka dari halaman dataset untuk melihat artefak.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (progress != null && progress.isShowing()) progress.dismiss();
    }

    // ===== util refleksi (ambil field string dari objek images) =====
    private static @Nullable String imgField(Object images, String name) {
        if (images == null) return null;
        try {
            java.lang.reflect.Field f = images.getClass().getField(name); // public field
            f.setAccessible(true);
            Object v = f.get(images);
            if (v instanceof String) {
                String s = (String) v;
                return (s != null && !s.trim().isEmpty()) ? s : null;
            }
        } catch (Throwable ignore) {}
        return null;
    }
}
