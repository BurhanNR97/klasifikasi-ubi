package com.klasifikasi.ubi.UI;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.JumlahResponse;
import com.klasifikasi.ubi.model.LatestTrainResponse;
import com.klasifikasi.ubi.net.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminDashboard extends AppCompatActivity {
    TextView nmUser, jmlSampel, jmlDataset, jmlHistory;
    LinearLayout menuSampel, menuHistory, menuDataset;
    ImageView btnLogout;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_dashboard);

        final View root = findViewById(R.id.mainAdminDashboard);
        final int pL = root.getPaddingLeft();
        final int pT = root.getPaddingTop();
        final int pR = root.getPaddingRight();
        final int pB = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pL + sb.left, pT + sb.top, pR + sb.right, pB + sb.bottom);
            return insets;
        });

        nmUser = findViewById(R.id.namaUser);
        nmUser.setText(getIntent().getStringExtra("nama"));

        jmlSampel = findViewById(R.id.qty_sampel);
        jmlDataset = findViewById(R.id.qty_dataset);
        jmlHistory = findViewById(R.id.qty_history);

        menuSampel = findViewById(R.id.menuSampel);
        menuDataset = findViewById(R.id.menuDataset);
        menuHistory = findViewById(R.id.menuHistory);

        menuSampel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AdminDashboard.this, SampelList.class);
                intent.putExtra("nama", getIntent().getStringExtra("nama"));
                intent.putExtra("id", getIntent().getStringExtra("id"));
                startActivity(intent);
                finish();
            }
        });

        menuDataset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AdminDashboard.this, DatasetList.class);
                intent.putExtra("nama", getIntent().getStringExtra("nama"));
                intent.putExtra("id", getIntent().getStringExtra("id"));
                startActivity(intent);
                finish();
            }
        });

        menuHistory.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboard.this, HasilActivity.class);
            intent.putExtra("nama", getIntent().getStringExtra("nama"));
            intent.putExtra("id", getIntent().getStringExtra("id"));
            startActivity(intent);
            finish();
        });

        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialAlertDialogBuilder(AdminDashboard.this)
                        .setTitle("Keluar Akun")
                        .setMessage("Yakin ingin logout dari aplikasi?")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(AdminDashboard.this, MainActivity.class));
                                finish();
                            }
                        })
                        .show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCounts();
    }

    private void loadCounts() {
        RetrofitClient.api().getJumlah().enqueue(new Callback<JumlahResponse>() {
            @Override
            public void onResponse(Call<JumlahResponse> call, Response<JumlahResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    jmlSampel.setText(String.valueOf(resp.body().sampel));
                    jmlDataset.setText(String.valueOf(resp.body().dataset));
                    jmlHistory.setText(String.valueOf(resp.body().hasil));
                } else {
                    jmlSampel.setText("0");
                    jmlDataset.setText("0");
                    jmlHistory.setText("0");
                    Toast.makeText(AdminDashboard.this,
                            "Gagal ambil statistik (" + resp.code() + ")",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JumlahResponse> call, Throwable t) {
                jmlSampel.setText("-");
                jmlDataset.setText("-");
                jmlHistory.setText("-");
                Toast.makeText(AdminDashboard.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}