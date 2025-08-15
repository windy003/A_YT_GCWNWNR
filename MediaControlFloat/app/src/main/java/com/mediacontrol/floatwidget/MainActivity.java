package com.mediacontrol.floatwidget;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private Button btnGrantPermission;
    private Button btnGrantAccessibility;
    private Button btnStartService;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupClickListeners();
        updateUI();
    }

    private void initViews() {
        btnGrantPermission = findViewById(R.id.btn_grant_permission);
        btnGrantAccessibility = findViewById(R.id.btn_grant_accessibility);
        btnStartService = findViewById(R.id.btn_start_service);
        tvStatus = findViewById(R.id.tv_status);
    }

    private void setupClickListeners() {
        btnGrantPermission.setOnClickListener(v -> requestOverlayPermission());
        btnGrantAccessibility.setOnClickListener(v -> requestAccessibilityPermission());
        btnStartService.setOnClickListener(v -> startFloatingService());
    }

    private void updateUI() {
        boolean hasOverlayPermission = canDrawOverlays();
        boolean hasAccessibilityPermission = isAccessibilityServiceEnabled();
        
        updateUIWithoutRoot(hasOverlayPermission, hasAccessibilityPermission);
    }
    
    private void updateUIWithoutRoot(boolean hasOverlayPermission, boolean hasAccessibilityPermission) {
        
        btnGrantPermission.setEnabled(!hasOverlayPermission);
        btnGrantAccessibility.setEnabled(!hasAccessibilityPermission);
        
        if (hasOverlayPermission && hasAccessibilityPermission) {
            tvStatus.setText("✓ 权限已授予，可以启动悬浮窗（无障碍模式）");
            btnStartService.setEnabled(true);
        } else if (hasOverlayPermission) {
            tvStatus.setText("⚠ 还需要授予无障碍权限以确保按键正常工作");
            btnStartService.setEnabled(true);
        } else {
            tvStatus.setText("❌ 需要授予悬浮窗权限");
            btnStartService.setEnabled(false);
        }
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
            }
        }
    }

    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "请在设置中开启\"媒体控制悬浮窗\"的无障碍服务", Toast.LENGTH_LONG).show();
    }


    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + MediaControlAccessibilityService.class.getName();
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        
        if (enabledServices == null) {
            return false;
        }
        
        return enabledServices.contains(serviceName);
    }

    private void startFloatingService() {
        if (canDrawOverlays()) {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            startService(serviceIntent);
            
            boolean hasAccessibility = isAccessibilityServiceEnabled();
            if (hasAccessibility) {
                Toast.makeText(this, "悬浮窗已启动，所有功能可用", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "悬浮窗已启动，建议开启无障碍权限以获得最佳体验", Toast.LENGTH_LONG).show();
            }
            finish();
        } else {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            updateUI();
            if (canDrawOverlays()) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
}