package com.klasifikasi.ubi.UI;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.Sampel;
import com.klasifikasi.ubi.net.ApiConst;
import com.klasifikasi.ubi.net.RetrofitClient;
import com.klasifikasi.ubi.utils.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SampelForm extends AppCompatActivity {

    private TextInputEditText etKode;
    private Spinner spJenis;
    private ImageView imgPreview;

    private Uri pickedUri = null;   // hasil akhir dari ImagePicker
    private String mode;
    private int editId = -1;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sampel_form);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainSampelForm), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        etKode     = findViewById(R.id.etKode);
        spJenis    = findViewById(R.id.spJenis);
        imgPreview = findViewById(R.id.imgPreview);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_sampelForm);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Spinner jenis
        ArrayAdapter<CharSequence> adapterJenis = ArrayAdapter.createFromResource(
                this, R.array.jenis_sampel, android.R.layout.simple_spinner_item);
        adapterJenis.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spJenis.setAdapter(adapterJenis);

        // Mode (create/edit)
        Intent i = getIntent();
        mode = i.getStringExtra("mode");

        if ("edit".equals(mode)) {
            setTitle("Edit Sampel");
            editId = i.getIntExtra("id", -1);

            etKode.setText(i.getStringExtra("kd_sampel"));
            etKode.setEnabled(false);

            String jenis = i.getStringExtra("jenis");
            if (jenis != null) {
                int pos = adapterJenis.getPosition(jenis);
                if (pos >= 0) spJenis.setSelection(pos);
            }

            String fotoPath = i.getStringExtra("foto");
            if (fotoPath != null && !fotoPath.isEmpty()) {
                String full = fotoPath.startsWith("http")
                        ? fotoPath
                        : ApiConst.BASE_FILE_URL + fotoPath;
                Glide.with(this).load(full).into(imgPreview);
            }
        } else {
            setTitle("Tambah Sampel");

            RetrofitClient.api().getNextKode().enqueue(new Callback<Sampel>() {
                @Override public void onResponse(Call<Sampel> call, Response<Sampel> resp) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        etKode.setText(resp.body().kd_sampel);
                        etKode.setEnabled(false);
                    } else {
                        Toast.makeText(SampelForm.this,
                                "Gagal ambil kode ("+resp.code()+")",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<Sampel> call, Throwable t) {
                    Toast.makeText(SampelForm.this,
                            "Error: " + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // ==== ImagePicker ====
        findViewById(R.id.btnPilihFoto).setOnClickListener(v -> {
            ImagePicker.with(this)
                    .galleryOnly()
                    .cropSquare()
                    .compress(1024)
                    .maxResultSize(1080,1080)
                    .start();
        });

        findViewById(R.id.btnAmbilFoto).setOnClickListener(v -> {
            ImagePicker.with(this)
                    .cameraOnly()
                    .cropSquare()
                    .compress(1024)
                    .maxResultSize(1080,1080)
                    .start();
        });

        findViewById(R.id.btnSimpan).setOnClickListener(v -> save());
    }

    // Hasil dari ImagePicker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                pickedUri = uri;
                Glide.with(this).load(pickedUri).into(imgPreview);
            }
        } else if (resultCode == com.github.dhaval2404.imagepicker.ImagePicker.RESULT_ERROR) {
            Toast.makeText(this,
                    com.github.dhaval2404.imagepicker.ImagePicker.getError(data),
                    Toast.LENGTH_SHORT).show();
        } else if (resultCode != RESULT_CANCELED) {
            Toast.makeText(this, "Ambil gambar dibatalkan", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        String jenis = spJenis.getSelectedItem() != null ? spJenis.getSelectedItem().toString() : "";
        if (jenis.isEmpty()) {
            Toast.makeText(this, "Pilih jenis", Toast.LENGTH_SHORT).show();
            return;
        }

        // siapkan base64 bila ada foto
        String fotoBase64 = null;
        if (pickedUri != null) {
            File file = FileUtil.from(this, pickedUri);
            fotoBase64 = encodeFileToBase64(file);
            if (fotoBase64 == null) {
                Toast.makeText(this, "Gagal membaca file gambar", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if ("edit".equals(mode)) {
            // update (POST + _method=PUT), kirim 'foto' base64 jika user pilih foto baru
            RetrofitClient.api().updateSampelBase64(
                    editId,
                    "PUT",
                    jenis,
                    fotoBase64  // boleh null
            ).enqueue(cb("Diupdate"));
        } else {
            // create baru (kd_sampel & tanggal otomatis server)
            RetrofitClient.api().createSampelBase64(
                    jenis,
                    fotoBase64  // boleh null
            ).enqueue(cb("Disimpan"));
        }
    }

    private String encodeFileToBase64(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            if (read <= 0) return null;
            // NO_WRAP supaya tidak ada \n yang mengacaukan
            return android.util.Base64.encodeToString(buf, android.util.Base64.NO_WRAP);
        } catch (IOException e) {
            return null;
        }
    }

    private Callback<Sampel> cb(String okMsg) {
        return new Callback<Sampel>() {
            @Override public void onResponse(Call<Sampel> call, Response<Sampel> resp) {
                if (resp.isSuccessful() && resp.body()!=null) {
                    String kode = resp.body().kd_sampel != null
                            ? (" • Kode: " + resp.body().kd_sampel) : "";
                    Toast.makeText(SampelForm.this,
                            okMsg + kode,
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String msg = "Gagal: " + resp.code();
                    try {
                        if (resp.errorBody()!=null) msg += " • " + resp.errorBody().string();
                    } catch (Exception ignored) {}
                    Toast.makeText(SampelForm.this, msg, Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<Sampel> call, Throwable t) {
                Toast.makeText(SampelForm.this,
                        "Error: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };
    }
}
