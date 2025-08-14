package com.mediacontrol.floatwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {
    private static final String CHANNEL_ID = "FloatingServiceChannel";
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private MediaKeySimulator mediaKeySimulator;
    private YouTubeWindowManager youTubeWindowManager;
    
    // UI 组件
    private EditText editNotes;
    private Button fontSmallerBtn;
    private Button fontLargerBtn;
    private Button closeBtn;
    private float currentTextSize = 18f; // 默认字体大小

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaKeySimulator = new MediaKeySimulator(this);
        youTubeWindowManager = new YouTubeWindowManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (floatingView == null) {
            createFloatingView();
        }
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.floating_service_notification))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        
        startForeground(1, notification);
        return START_STICKY;
    }

    private void createFloatingView() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_widget, null);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager.addView(floatingView, params);

        setupButtons();
        setupDragListener();
    }

    private void setupButtons() {
        ImageButton playPauseBtn = floatingView.findViewById(R.id.btn_play_pause);
        ImageButton rewindBtn = floatingView.findViewById(R.id.btn_rewind);
        editNotes = floatingView.findViewById(R.id.edit_notes);
        fontSmallerBtn = floatingView.findViewById(R.id.btn_font_smaller);
        fontLargerBtn = floatingView.findViewById(R.id.btn_font_larger);
        closeBtn = floatingView.findViewById(R.id.btn_close);

        playPauseBtn.setOnClickListener(v -> {
            // 播放/暂停使用标准媒体键，通常不需要特殊处理
            mediaKeySimulator.sendPlayPause();
        });
        
        rewindBtn.setOnClickListener(v -> {
            // 先清除EditText焦点，让YouTube获得焦点
            editNotes.clearFocus();
            
            // 临时设置悬浮窗为不可聚焦，让YouTube获得系统焦点
            params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(floatingView, params);
            
            // 在后台线程发送按键
            new Thread(() -> {
                try {
                    // 短暂延迟确保焦点切换完成
                    Thread.sleep(150);
                    // 发送按键到YouTube
                    youTubeWindowManager.sendLeftArrowToYouTube();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
        
        // 设置EditText获得焦点时的处理
        editNotes.setOnFocusChangeListener((v, hasFocus) -> {
            try {
                if (hasFocus) {
                    // 当EditText获得焦点时，临时移除FLAG_NOT_FOCUSABLE
                    params.flags = params.flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(floatingView, params);
                } else {
                    // 失去焦点时恢复FLAG_NOT_FOCUSABLE
                    params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(floatingView, params);
                }
            } catch (Exception e) {
                // 避免窗口更新异常导致卡顿
                e.printStackTrace();
            }
        });
        
        // 点击EditText时确保能获得焦点
        editNotes.setOnClickListener(v -> {
            editNotes.requestFocus();
        });
        
        // 字体大小调整按钮
        fontSmallerBtn.setOnClickListener(v -> {
            if (currentTextSize > 10f) {
                currentTextSize -= 2f;
                editNotes.setTextSize(currentTextSize);
            }
        });
        
        fontLargerBtn.setOnClickListener(v -> {
            if (currentTextSize < 30f) {
                currentTextSize += 2f;
                editNotes.setTextSize(currentTextSize);
            }
        });
        
        // 关闭按钮
        closeBtn.setOnClickListener(v -> {
            // 停止服务并关闭悬浮窗
            stopSelf();
        });
    }

    private void setupDragListener() {
        // 在整个视图上设置拖拽监听，但排除EditText区域
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 如果触摸的是EditText区域，不处理拖拽
                if (isTouchingEditText(event)) {
                    return false;
                }
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }
    
    private boolean isTouchingEditText(MotionEvent event) {
        if (editNotes == null || !editNotes.isShown()) return false;
        
        try {
            int[] location = new int[2];
            editNotes.getLocationOnScreen(location);
            
            float x = event.getRawX();
            float y = event.getRawY();
            
            return x >= location[0] && x <= location[0] + editNotes.getWidth() &&
                   y >= location[1] && y <= location[1] + editNotes.getHeight();
        } catch (Exception e) {
            // 避免计算异常导致卡顿
            return false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}