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
    private Button btnGrantRoot;
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
        btnGrantRoot = findViewById(R.id.btn_grant_root);
        btnStartService = findViewById(R.id.btn_start_service);
        tvStatus = findViewById(R.id.tv_status);
    }

    private void setupClickListeners() {
        btnGrantPermission.setOnClickListener(v -> requestOverlayPermission());
        btnGrantAccessibility.setOnClickListener(v -> requestAccessibilityPermission());
        btnGrantRoot.setOnClickListener(v -> requestRootPermission());
        btnStartService.setOnClickListener(v -> startFloatingService());
    }

    private void updateUI() {
        boolean hasOverlayPermission = canDrawOverlays();
        boolean hasAccessibilityPermission = isAccessibilityServiceEnabled();
        
        // 在后台线程检测root权限，避免阻塞UI
        updateUIWithRootInfo(hasOverlayPermission, hasAccessibilityPermission, false, false);
        
        new Thread(() -> {
            boolean hasRootPermission = false;
            boolean isDeviceRooted = false;
            
            try {
                isDeviceRooted = RootHelper.isDeviceRooted();
                if (isDeviceRooted) {
                    hasRootPermission = RootHelper.isRootGranted();
                }
            } catch (Exception e) {
                hasRootPermission = false;
                isDeviceRooted = false;
            }
            
            final boolean finalHasRoot = hasRootPermission;
            final boolean finalIsRooted = isDeviceRooted;
            
            runOnUiThread(() -> {
                updateUIWithRootInfo(hasOverlayPermission, hasAccessibilityPermission, finalHasRoot, finalIsRooted);
            });
        }).start();
    }
    
    private void updateUIWithRootInfo(boolean hasOverlayPermission, boolean hasAccessibilityPermission, 
                                     boolean hasRootPermission, boolean isDeviceRooted) {
        
        btnGrantPermission.setEnabled(!hasOverlayPermission);
        btnGrantAccessibility.setEnabled(!hasAccessibilityPermission);
        btnGrantRoot.setEnabled(isDeviceRooted && !hasRootPermission);
        btnGrantRoot.setVisibility(isDeviceRooted ? android.view.View.VISIBLE : android.view.View.GONE);
        
        if (hasOverlayPermission && (hasAccessibilityPermission || hasRootPermission)) {
            if (hasRootPermission) {
                tvStatus.setText("✓ 所有权限已授予，可以启动悬浮窗（Root模式 - 最佳体验）");
            } else {
                tvStatus.setText("✓ 权限已授予，可以启动悬浮窗（无障碍模式）");
            }
            btnStartService.setEnabled(true);
        } else if (hasOverlayPermission) {
            if (isDeviceRooted) {
                if (hasRootPermission) {
                    tvStatus.setText("✓ Root权限已授予！");
                } else {
                    tvStatus.setText("⚠ 建议先获取Root权限以确保按键功能正常工作");
                }
            } else {
                tvStatus.setText("⚠ 还需要授予无障碍权限以确保按键正常工作");
            }
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

    private void requestRootPermission() {
        // 先检查设备是否已root
        new Thread(() -> {
            boolean isRooted = false;
            try {
                isRooted = RootHelper.isDeviceRooted();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            final boolean deviceRooted = isRooted;
            runOnUiThread(() -> {
                if (!deviceRooted) {
                    Toast.makeText(this, "⚠ 设备未Root，无法获取Root权限\n建议使用无障碍权限作为替代方案", 
                        Toast.LENGTH_LONG).show();
                    return;
                }
                
                Toast.makeText(this, "🔐 正在请求Root权限...\n请在弹出的SuperSU/Magisk对话框中点击\"允许\"", 
                    Toast.LENGTH_LONG).show();
                
                // 在后台线程请求root权限
                new Thread(() -> {
                    boolean granted = false;
                    try {
                        granted = RootHelper.requestRootPermission();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    final boolean finalGranted = granted;
                    // 回到主线程更新UI
                    runOnUiThread(() -> {
                        if (finalGranted) {
                            Toast.makeText(this, "✅ Root权限获取成功！\n现在可以完美控制YouTube播放", 
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "❌ Root权限获取失败\n请检查Root管理器设置，或使用无障碍权限", 
                                Toast.LENGTH_LONG).show();
                        }
                        updateUI();
                    });
                }).start();
            });
        }).start();
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