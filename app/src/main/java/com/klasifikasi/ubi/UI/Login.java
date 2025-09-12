package com.klasifikasi.ubi.UI;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.LoginRequest;
import com.klasifikasi.ubi.model.LoginResponse;
import com.klasifikasi.ubi.net.RetrofitClient;
import com.klasifikasi.ubi.net.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Login extends AppCompatActivity {
    EditText username, password;
    TextInputLayout txUser, txPass;
    private ProgressBar progress;
    private SessionManager session;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLogin), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        progress = findViewById(R.id.progress);
        session  = new SessionManager(this);
        username = findViewById(R.id.logEmail);
        password = findViewById(R.id.logPass);
        txUser = findViewById(R.id.logInEmail);
        txPass = findViewById(R.id.logInPass);

        AppCompatButton login = findViewById(R.id.btnLogin);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doLogin();
            }
        });
    }

    private void doLogin() {
        txUser.setError(null); txPass.setError(null);

        String email = username.getText() == null ? "" : username.getText().toString().trim();
        String pass  = password.getText() == null ? "" : password.getText().toString().trim();

        boolean ok = true;
        if (email.isEmpty()) { txUser.setError("Masukkan email anda"); ok = false; }
        if (pass.isEmpty())  { txPass.setError("Masukkan password anda"); ok = false; }
        if (!ok) return;

        setLoading(true);

        RetrofitClient.api().login(new LoginRequest(email, pass))
                .enqueue(new Callback<LoginResponse>() {
                    @Override public void onResponse(Call<LoginResponse> call, Response<LoginResponse> resp) {
                        setLoading(false);
                        if (resp.isSuccessful() && resp.body() != null && resp.body().token != null) {
                            LoginResponse r = resp.body();
                            session.saveToken(resp.body().token);

                            if (r.user.level.equals("admin")) {
                                Intent intent = new Intent(Login.this, AdminDashboard.class);
                                intent.putExtra("nama", r.user.nama);
                                intent.putExtra("id", r.user.id);
                                startActivity(intent);
                                finish();
                            }

                        } else {
                            String msg = "Email atau kata sandi salah";
                            if (resp.errorBody() != null) try { msg = resp.errorBody().string(); } catch (Exception ignored) {}
                            Toast.makeText(Login.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(Login.this, "Tidak bisa konek: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        AppCompatButton btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setVisibility(b ? View.GONE : View.VISIBLE);
        btnLogin.setEnabled(!b);
    }
}