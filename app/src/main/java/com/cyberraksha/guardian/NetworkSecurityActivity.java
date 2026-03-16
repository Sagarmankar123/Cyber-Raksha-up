package com.cyberraksha.guardian;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NetworkSecurityActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 456;
    private ProgressBar progressBar;
    private TextView networkName, networkStatus, aiResult;
    private TextView tvWifi, tvIp, tvSecurity;
    private CardView resultCard;
    private MaterialButton btnCheck;
    private GeminiService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_network_security);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true); // Light background, dark icons

        AppBarLayout appBarLayout = findViewById(R.id.networkToolbar).getParent() instanceof AppBarLayout ? (AppBarLayout) findViewById(R.id.networkToolbar).getParent() : null;
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        MaterialToolbar toolbar = findViewById(R.id.networkToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.networkProgressBar);
        networkName = findViewById(R.id.networkNameText);
        networkStatus = findViewById(R.id.networkStatusDetail);
        aiResult = findViewById(R.id.networkAiResult);
        resultCard = findViewById(R.id.resultCard);
        btnCheck = findViewById(R.id.btnCheckNetwork);

        // Map the included rows
        View rowWifi = findViewById(R.id.rowWifi);
        if (rowWifi != null) {
            TextView labelWifi = rowWifi.findViewById(R.id.label);
            if (labelWifi != null) labelWifi.setText("Wi-Fi Name");
            tvWifi = rowWifi.findViewById(R.id.value);
        }

        View rowIp = findViewById(R.id.rowIp);
        if (rowIp != null) {
            TextView labelIp = rowIp.findViewById(R.id.label);
            if (labelIp != null) labelIp.setText("IP Address");
            tvIp = rowIp.findViewById(R.id.value);
        }

        View rowSecurity = findViewById(R.id.rowSecurity);
        if (rowSecurity != null) {
            TextView labelSecurity = rowSecurity.findViewById(R.id.label);
            if (labelSecurity != null) labelSecurity.setText("Security Type");
            tvSecurity = rowSecurity.findViewById(R.id.value);
        }

        initGeminiService();
        btnCheck.setOnClickListener(v -> checkPermissionsAndScan());
    }

    private void initGeminiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        geminiService = retrofit.create(GeminiService.class);
    }

    private void checkPermissionsAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            analyzeNetwork();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                analyzeNetwork();
            } else {
                Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void analyzeNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork == null || !activeNetwork.isConnected()) {
            networkName.setText("Offline");
            networkStatus.setText("Connect to a network to scan.");
            return;
        }

        String type = activeNetwork.getTypeName();
        String ssid = "Unknown";
        String ip = "0.0.0.0";
        boolean isEncrypted = true;

        if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifiManager.getConnectionInfo();
            ssid = info.getSSID().replace("\"", "");
            int ipAddress = info.getIpAddress();
            ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            
            if (ssid.toLowerCase().contains("free") || ssid.toLowerCase().contains("public") || ssid.equals("<unknown ssid>")) {
                isEncrypted = false;
            }
        }

        networkName.setText(ssid);
        networkStatus.setText("Vulnerability scan in progress...");
        
        tvWifi.setText(ssid);
        tvIp.setText(ip);
        tvSecurity.setText(isEncrypted ? "WPA2/WPA3" : "None (Risky)");

        performAiSecurityCheck(ssid, type, isEncrypted);
    }

    private void performAiSecurityCheck(String ssid, String type, boolean looksEncrypted) {
        progressBar.setVisibility(View.VISIBLE);
        btnCheck.setVisibility(View.INVISIBLE);

        String prompt = "Analyze network safety: SSID:" + ssid + ", Type:" + type + ", Security:" + (looksEncrypted ? "Encrypted" : "Open") + ". Provide a very short safety verdict.";

        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.URL_CHECK);
        String authHeader = ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.URL_CHECK);
        GeminiRequest request = new GeminiRequest(prompt);
        request.setModel(config.modelId);

        geminiService.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                progressBar.setVisibility(View.GONE);
                btnCheck.setVisibility(View.VISIBLE);
                btnCheck.setText("Re-Scan Network");
                resultCard.setVisibility(View.VISIBLE);

                if (response.isSuccessful() && response.body() != null) {
                    aiResult.setText(response.body().getResponseText());
                } else {
                    aiResult.setText("Assessment failed.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnCheck.setVisibility(View.VISIBLE);
                aiResult.setText("Check connection.");
            }
        });
    }
}
