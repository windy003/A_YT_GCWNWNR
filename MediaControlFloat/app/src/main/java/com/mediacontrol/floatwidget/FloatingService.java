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
import android.os.Handler;
import android.os.Looper;

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
    private ImageButton playPauseBtn;
    private float currentTextSize = 18f; // 默认字体大小
    private boolean isPlaying = false; // 播放状态，初始为暂停状态（显示播放按钮）
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable playbackStatusChecker;

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
        playPauseBtn = floatingView.findViewById(R.id.btn_play_pause);
        ImageButton rewindBtn = floatingView.findViewById(R.id.btn_rewind);
        editNotes = floatingView.findViewById(R.id.edit_notes);
        fontSmallerBtn = floatingView.findViewById(R.id.btn_font_smaller);
        fontLargerBtn = floatingView.findViewById(R.id.btn_font_larger);
        closeBtn = floatingView.findViewById(R.id.btn_close);
        
        // 初始化播放按钮状态
        updatePlayPauseButton();
        
        // 暂时禁用自动状态检测，先测试手动切换
        // startPlaybackStatusMonitoring();

        playPauseBtn.setOnClickListener(v -> {
            // 播放/暂停使用标准媒体键，通常不需要特殊处理
            mediaKeySimulator.sendPlayPause();
            // 切换播放状态
            isPlaying = !isPlaying;
            android.util.Log.d("FloatingService", "按钮点击，播放状态切换为: " + (isPlaying ? "播放中" : "暂停"));
            updatePlayPauseButton();
        });
        
        rewindBtn.setOnClickListener(v -> {
            // 先清除EditText焦点，让YouTube获得焦点
            editNotes.clearFocus();
            
            // 临时设置悬浮窗为不可聚焦，让YouTube获得系统焦点
            params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(floatingView, params);
            
            // 在后台线程发送5秒回退指令
            new Thread(() -> {
                try {
                    // 短暂延迟确保焦点切换完成
                    Thread.sleep(150);
                    // 执行5秒回退
                    perform5SecondRewind();
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
    
    /**
     * 执行5秒回退操作
     */
    private void perform5SecondRewind() {
        android.util.Log.d("FloatingService", "执行5秒回退");
        
        // 优先使用双击手势（YouTube标准的5秒回退）
        MediaControlAccessibilityService accessibilityService = 
            MediaControlAccessibilityService.getInstance();
        if (accessibilityService != null && accessibilityService.isYouTubeInForeground()) {
            if (accessibilityService.performLeftDoubleClick()) {
                android.util.Log.d("FloatingService", "5秒回退：双击手势成功");
                return;
            }
        }
        
        // 备用方案：使用左方向键（虽然是10秒，但至少能回退）
        android.util.Log.d("FloatingService", "双击手势失败，使用左方向键备用方案");
        youTubeWindowManager.sendLeftArrowToYouTube();
    }
    
    /**
     * 更新播放/暂停按钮的图标状态
     * 如果正在播放，显示暂停图标（点击可暂停）
     * 如果已暂停，显示播放图标（点击可播放）
     */
    private void updatePlayPauseButton() {
        if (playPauseBtn != null) {
            if (isPlaying) {
                // 正在播放时，显示暂停图标
                playPauseBtn.setImageResource(R.drawable.ic_pause);
                playPauseBtn.setContentDescription("暂停");
                android.util.Log.d("FloatingService", "更新按钮图标为：暂停图标（当前播放中）");
            } else {
                // 已暂停时，显示播放图标
                playPauseBtn.setImageResource(R.drawable.ic_play);
                playPauseBtn.setContentDescription("播放");
                android.util.Log.d("FloatingService", "更新按钮图标为：播放图标（当前暂停）");
            }
        }
    }
    
    /**
     * 启动播放状态监控
     */
    private void startPlaybackStatusMonitoring() {
        playbackStatusChecker = new Runnable() {
            @Override
            public void run() {
                checkPlaybackStatus();
                handler.postDelayed(this, 2000); // 每2秒检查一次
            }
        };
        handler.post(playbackStatusChecker);
    }
    
    /**
     * 检查当前播放状态
     */
    private void checkPlaybackStatus() {
        if (youTubeWindowManager != null && youTubeWindowManager.isYouTubeInForeground()) {
            // 通过无障碍服务检测播放状态
            MediaControlAccessibilityService accessibilityService = 
                MediaControlAccessibilityService.getInstance();
            if (accessibilityService != null) {
                boolean currentPlayingState = accessibilityService.isYouTubePlaying();
                if (currentPlayingState != isPlaying) {
                    isPlaying = currentPlayingState;
                    updatePlayPauseButton();
                }
            }
        }
    }
    
    /**
     * 停止播放状态监控
     */
    private void stopPlaybackStatusMonitoring() {
        if (handler != null && playbackStatusChecker != null) {
            handler.removeCallbacks(playbackStatusChecker);
        }
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
        stopPlaybackStatusMonitoring();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}