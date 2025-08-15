package com.mediacontrol.floatwidget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * YouTube窗口管理器 - 确保YouTube保持前台活动状态
 */
public class YouTubeWindowManager {
    private static final String TAG = "YouTubeWindowManager";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music";
    
    private Context context;
    private ActivityManager activityManager;
    
    public YouTubeWindowManager(Context context) {
        this.context = context;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }
    
    /**
     * 检查YouTube是否在前台运行
     */
    public boolean isYouTubeInForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0+ 使用更新的API
                List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
                if (appProcesses != null) {
                    for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                        if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            if (YOUTUBE_PACKAGE.equals(appProcess.processName) || 
                                YOUTUBE_MUSIC_PACKAGE.equals(appProcess.processName)) {
                                Log.d(TAG, "YouTube is in foreground: " + appProcess.processName);
                                return true;
                            }
                        }
                    }
                }
            } else {
                // 较老的Android版本
                List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
                if (!tasks.isEmpty()) {
                    ActivityManager.RunningTaskInfo topTask = tasks.get(0);
                    String packageName = topTask.topActivity.getPackageName();
                    if (YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                        Log.d(TAG, "YouTube is in foreground (legacy): " + packageName);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking YouTube foreground status", e);
        }
        
        return false;
    }
    
    /**
     * 确保YouTube回到前台（如果已经在运行）
     */
    public boolean bringYouTubeToForeground() {
        try {
            // 方法1: 通过AccessibilityService检查
            MediaControlAccessibilityService accessibilityService = 
                MediaControlAccessibilityService.getInstance();
            
            if (accessibilityService != null) {
                AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
                if (rootNode != null) {
                    String packageName = rootNode.getPackageName() != null ? 
                        rootNode.getPackageName().toString() : "";
                    
                    if (YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                        Log.d(TAG, "YouTube is already active via AccessibilityService");
                        return true;
                    }
                }
            }
            
            // 方法2: 尝试启动YouTube（不创建新Activity，只是带到前台）
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(YOUTUBE_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Brought YouTube to foreground");
                
                // 短暂延迟确保切换完成
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error bringing YouTube to foreground", e);
        }
        
        return false;
    }
    
    /**
     * 向YouTube发送按键，确保YouTube保持前台状态
     */
    public boolean sendKeyToYouTube(int keyCode) {
        Log.d(TAG, "Attempting to send key " + keyCode + " to YouTube");
        
        // 直接发送按键事件，不强制切换应用（避免干扰用户）
        boolean success = false;
        
        // 方法1: 使用AccessibilityService
        try {
            MediaControlAccessibilityService accessibilityService = 
                MediaControlAccessibilityService.getInstance();
            if (accessibilityService != null) {
                success = accessibilityService.sendKeyWithFocusEnsurance(keyCode);
                
                if (success) {
                    Log.d(TAG, "Successfully sent key via AccessibilityService");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "AccessibilityService method failed: " + e.getMessage());
        }
        
        // 方法2: 直接按键注入
        try {
            DirectKeyInjector injector = new DirectKeyInjector(context);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                success = injector.sendLeftArrowKey();
            }
            
            if (success) {
                Log.d(TAG, "Successfully sent key via DirectKeyInjector");
            } else {
                Log.w(TAG, "All methods failed to send key to YouTube");
            }
        } catch (Exception e) {
            Log.w(TAG, "DirectKeyInjector method failed: " + e.getMessage());
        }
        
        return success;
    }
    
    /**
     * 发送左方向键到YouTube（10秒倒退）或双击手势（5秒倒退）
     */
    public boolean sendLeftArrowToYouTube() {
        Log.d(TAG, "尝试发送5秒回退指令到YouTube");
        
        // 方法1: 优先尝试双击手势（5秒回退）
        MediaControlAccessibilityService accessibilityService = 
            MediaControlAccessibilityService.getInstance();
        if (accessibilityService != null && accessibilityService.isYouTubeInForeground()) {
            if (accessibilityService.performLeftDoubleClick()) {
                Log.d(TAG, "双击手势发送成功（5秒回退）");
                return true;
            }
        }
        
        // 方法2: 备用方案 - 使用左方向键（10秒回退）
        Log.d(TAG, "双击手势失败，使用左方向键（10秒回退）");
        return sendKeyToYouTube(KeyEvent.KEYCODE_DPAD_LEFT);
    }
    
    /**
     * 发送播放/暂停键到YouTube
     */
    public boolean sendPlayPauseToYouTube() {
        return sendKeyToYouTube(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }
    
    /**
     * 监控YouTube窗口状态的辅助方法
     */
    public void monitorYouTubeWindow() {
        // 这个方法可以在后台线程中定期调用来监控YouTube状态
        new Thread(() -> {
            try {
                while (true) {
                    boolean isYouTubeForeground = isYouTubeInForeground();
                    Log.d(TAG, "YouTube foreground status: " + isYouTubeForeground);
                    
                    Thread.sleep(5000); // 每5秒检查一次
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.d(TAG, "YouTube monitoring thread interrupted");
            }
        }).start();
    }
}