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
import com.klasifikasi.ubi.adapter.SampelAdapter;
import com.klasifikasi.ubi.model.Sampel;
import com.klasifikasi.ubi.net.RetrofitClient;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SampelList extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private SampelAdapter adapter;
    private SwipeRefreshLayout swipe;
    private SearchView sv;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchTask;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sampel_list);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainSampelList), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        // ----- Toolbar (support dua kemungkinan ID)
        toolbar = findViewById(R.id.toolbar_sampel);
        if (toolbar == null) toolbar = findViewById(R.id.toolbar_sampel);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            // tombol back opsional:
             toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                 @Override
                 public void onClick(View v) {
                     Intent intent = new Intent(SampelList.this, AdminDashboard.class);
                     intent.putExtra("nama", getIntent().getStringExtra("nama"));
                     intent.putExtra("id", getIntent().getStringExtra("id"));
                     startActivity(intent);
                     finish();
                 }
             });
        }

        // ----- RecyclerView (support rvSampel/rv)
        RecyclerView rv = findViewById(R.id.rvSampel);
        if (rv == null) rv = findViewById(R.id.rvSampel);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SampelAdapter(new SampelAdapter.Listener() {
            @Override public void onEdit(Sampel s) {
                Intent i = new Intent(SampelList.this, SampelForm.class);
                i.putExtra("mode", "edit");
                i.putExtra("id", s.id);
                i.putExtra("kd_sampel", s.kd_sampel);
                i.putExtra("jenis", s.jenis);
                i.putExtra("tanggal", s.tanggal);
                i.putExtra("foto", s.foto);
                startActivity(i);
            }
            @Override public void onDelete(Sampel s) {
                // Tampilkan dialog konfirmasi di tempat lain bila perlu
                deleteSampel(s.id);
            }
        });
        rv.setAdapter(adapter);

        // ----- SwipeRefresh
        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> load(currentQuery()));

        // ----- FAB
        View fab = findViewById(R.id.fabAdd);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                Intent i = new Intent(this, SampelForm.class);
                i.putExtra("mode", "create");
                startActivity(i);
            });
        }

        // ----- SearchView: langsung terbuka + fokus + keyboard + debounce
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

        RetrofitClient.api().listSampel(queryParam).enqueue(new Callback<List<Sampel>>() {
            @Override public void onResponse(Call<List<Sampel>> call, Response<List<Sampel>> resp) {
                swipe.setRefreshing(false);
                if (resp.isSuccessful() && resp.body() != null) {
                    adapter.setData(resp.body());
                } else {
                    String err = "";
                    try { if (resp.errorBody() != null) err = resp.errorBody().string(); } catch (IOException ignored) {}
                    Toast.makeText(SampelList.this,
                            "Gagal load: " + resp.code() + (err.isEmpty() ? "" : " • " + err),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<List<Sampel>> call, Throwable t) {
                swipe.setRefreshing(false);
                Toast.makeText(SampelList.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteSampel(int id) {
        RetrofitClient.api().deleteSampelCompat(id, "DELETE").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> resp) {
                if (resp.isSuccessful()) {
                    Toast.makeText(SampelList.this, "Berhasil dihapus", Toast.LENGTH_SHORT).show();
                    load(currentQuery()); // refresh
                } else {
                    String msg = "Gagal hapus: " + resp.code();
                    try { if (resp.errorBody()!=null) msg += " • " + resp.errorBody().string(); } catch (Exception ignored) {}
                    Toast.makeText(SampelList.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(SampelList.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override protected void onResume() {
        super.onResume();
        if (!swipe.isRefreshing()) load(currentQuery());
    }

    private void confirmDelete(Sampel s) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hapus Sampel")
                .setMessage("Yakin ingin menghapus " + s.kd_sampel + "?")
                .setPositiveButton("Hapus", (d, w) -> deleteSampel(s.id))
                .setNegativeButton("Batal", null)
                .show();
    }
}