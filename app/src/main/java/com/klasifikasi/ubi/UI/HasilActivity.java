package com.klasifikasi.ubi.UI;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.adapter.HasilAdapter;
import com.klasifikasi.ubi.model.Hasil;
import com.klasifikasi.ubi.net.ApiConst;
import com.klasifikasi.ubi.net.RetrofitClient;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HasilActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private HasilAdapter adapter;
    private SwipeRefreshLayout swipe;
    private SearchView sv;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchTask;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hasil);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootHasil), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        // ----- Toolbar
        toolbar = findViewById(R.id.toolbar_hasil);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // kembali ke dashboard/admin (samakan dengan alurmu)
                    Intent intent = new Intent(HasilActivity.this, AdminDashboard.class);
                    intent.putExtra("nama", getIntent().getStringExtra("nama"));
                    intent.putExtra("id", getIntent().getStringExtra("id"));
                    startActivity(intent);
                    finish();
                }
            });
        }

        // ----- RecyclerView
        RecyclerView rv = findViewById(R.id.rvHasil);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new HasilAdapter(new HasilAdapter.Listener() {
            @Override public void onView(Hasil h) {
                // buka URL gambar (atau halaman detail kalau ada)
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ApiConst.BASE_FILE_URL + h.getUrl()));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(HasilActivity.this, "Tidak bisa membuka URL", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onDelete(Hasil h) {
                confirmDelete(h);
            }
        });
        rv.setAdapter(adapter);

        // ----- SwipeRefresh
        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> load(currentQuery()));

        // ----- FAB (opsional tambah fitur lain)
        View fab = findViewById(R.id.fabAdd);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                Toast.makeText(this, "Aksi tambah tidak tersedia untuk Hasil", Toast.LENGTH_SHORT).show();
            });
        }

        // ----- SearchView: terbuka + fokus + keyboard + debounce
        sv = findViewById(R.id.searchView);
        if (sv != null) {
            sv.setIconifiedByDefault(false);
            sv.setIconified(false);
            sv.requestFocus();
            sv.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(sv.findFocus(), 0);
            });

            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) {
                    load(query);
                    return true;
                }
                @Override public boolean onQueryTextChange(String newText) {
                    if (searchTask != null) handler.removeCallbacks(searchTask);
                    searchTask = () -> load(newText);
                    handler.postDelayed(searchTask, 300); // debounce 300ms
                    return true;
                }
            });
        }

        // load awal
        swipe.setRefreshing(true);
        load("");
    }

    private String currentQuery() {
        return sv == null ? "" : (sv.getQuery() == null ? "" : sv.getQuery().toString());
    }

    private void load(String query) {
        String q = query == null ? "" : query.trim();
        swipe.setRefreshing(true);

        // jika API ingin parameter search di-skip saat kosong, kirim null
        String queryParam = q.isEmpty() ? null : q;

        RetrofitClient.api().listHasil(queryParam).enqueue(new Callback<List<Hasil>>() {
            @Override public void onResponse(Call<List<Hasil>> call, Response<List<Hasil>> resp) {
                swipe.setRefreshing(false);
                if (resp.isSuccessful() && resp.body() != null) {
                    adapter.setData(resp.body());
                } else {
                    String err = "";
                    try { if (resp.errorBody() != null) err = resp.errorBody().string(); } catch (IOException ignored) {}
                    Toast.makeText(HasilActivity.this,
                            "Gagal load: " + resp.code() + (err.isEmpty() ? "" : " • " + err),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<List<Hasil>> call, Throwable t) {
                swipe.setRefreshing(false);
                Toast.makeText(HasilActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteHasil(long id) {
        RetrofitClient.api().deleteHasilCompat(id, "DELETE").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) {
                    Toast.makeText(HasilActivity.this, "Berhasil dihapus", Toast.LENGTH_SHORT).show();
                    load(currentQuery()); // refresh
                } else {
                    String msg = "Gagal hapus: " + resp.code();
                    try { if (resp.errorBody()!=null) msg += " • " + resp.errorBody().string(); } catch (Exception ignored) {}
                    Toast.makeText(HasilActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(HasilActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDelete(Hasil h) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hapus Hasil")
                .setMessage("Yakin ingin menghapus data ini?\n" + (h.getWarna() == null ? "" : h.getWarna()))
                .setPositiveButton("Hapus", (d, w) -> deleteHasil(h.getId() == null ? 0 : h.getId()))
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!swipe.isRefreshing()) load(currentQuery());
    }
}
