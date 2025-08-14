package com.mediacontrol.floatwidget;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.app.ActivityManager;
import android.content.Context;
import java.util.List;

/**
 * Quick Settings Tile服务，用于快速开启/关闭悬浮窗
 */
public class FloatingWidgetTileService extends TileService {
    private static final String TAG = "FloatingWidgetTileService";

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "Tile was added");
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Log.d(TAG, "Tile was removed");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        
        boolean isServiceRunning = isFloatingServiceRunning();
        Log.d(TAG, "Tile clicked, service running: " + isServiceRunning);
        
        if (isServiceRunning) {
            // 停止悬浮窗服务
            stopFloatingService();
        } else {
            // 启动悬浮窗服务
            startFloatingService();
        }
        
        // 更新tile状态
        updateTileState();
    }

    /**
     * 检查FloatingService是否正在运行
     */
    private boolean isFloatingServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
        
        for (ActivityManager.RunningServiceInfo service : services) {
            if (FloatingService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 启动悬浮窗服务
     */
    private void startFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            startForegroundService(serviceIntent);
            Log.d(TAG, "FloatingService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start FloatingService", e);
        }
    }

    /**
     * 停止悬浮窗服务
     */
    private void stopFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            stopService(serviceIntent);
            Log.d(TAG, "FloatingService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop FloatingService", e);
        }
    }

    /**
     * 更新Tile的状态
     */
    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile != null) {
            boolean isServiceRunning = isFloatingServiceRunning();
            
            if (isServiceRunning) {
                tile.setIcon(Icon.createWithResource(this, R.drawable.nr));
                tile.setLabel("悬浮窗");
                tile.setContentDescription("点击关闭悬浮窗");
                tile.setState(Tile.STATE_ACTIVE);
            } else {
                tile.setIcon(Icon.createWithResource(this, R.drawable.nr));
                tile.setLabel("悬浮窗");
                tile.setContentDescription("点击开启悬浮窗");
                tile.setState(Tile.STATE_INACTIVE);
            }
            
            tile.updateTile();
            Log.d(TAG, "Tile state updated, active: " + isServiceRunning);
        }
    }
}