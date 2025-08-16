package com.mediacontrol.floatwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.inputmethod.InputMethodManager;
import android.text.TextWatcher;
import android.media.AudioManager;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {
    private static final String CHANNEL_ID = "FloatingServiceChannel";
    private static final String PREFS_NAME = "FloatingWidgetPrefs";
    private static final String NOTES_KEY = "saved_notes";
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    
    // UI 组件
    private EditText editNotes;
    private Button fontSmallerBtn;
    private Button fontLargerBtn;
    private Button closeBtn;
    private Button unfocusBtn;
    private ImageButton playPauseBtn;
    private float currentTextSize = 18f; // 默认字体大小
    private boolean isPlaying = false; // 播放状态，初始为暂停状态（显示播放按钮）
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable playbackStatusChecker;
    private Runnable saveNotesRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
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
        unfocusBtn = floatingView.findViewById(R.id.btn_unfocus);
        closeBtn = floatingView.findViewById(R.id.btn_close);
        
        // 初始化播放按钮状态
        updatePlayPauseButton();
        
        // 加载保存的文本内容
        loadSavedNotes();
        
        // 设置EditText换行属性
        setupEditTextWordWrap();
        
        // 暂时禁用自动状态检测，使用手动切换更可靠
        // startPlaybackStatusMonitoring();

        // 添加长按功能来手动同步状态
        playPauseBtn.setOnLongClickListener(v -> {
            android.util.Log.d("FloatingService", "播放/暂停按钮长按 - 手动同步状态");
            checkPlaybackStatus();
            Toast.makeText(this, "已同步播放状态", Toast.LENGTH_SHORT).show();
            return true;
        });
        
        playPauseBtn.setOnClickListener(v -> {
            android.util.Log.d("FloatingService", "播放/暂停按钮点击");
            
            // 保存当前输入状态
            boolean hadEditTextFocus = editNotes.hasFocus();
            boolean wasKeyboardVisible = isKeyboardVisible();
            
            android.util.Log.d("FloatingService", "操作前状态 - 焦点: " + hadEditTextFocus + ", 键盘: " + wasKeyboardVisible);
            
            // 播放/暂停使用媒体按键API
            new Thread(() -> {
                try {
                    // 临时清除焦点，确保媒体按键发送到正确的应用
                    if (hadEditTextFocus) {
                        handler.post(() -> editNotes.clearFocus());
                        Thread.sleep(100); // 短暂等待焦点切换
                    }
                    
                    // 使用媒体按键API发送播放/暂停命令
                    boolean success = sendMediaPlayPauseKey();
                    
                    // 在主线程更新UI和恢复状态
                    handler.post(() -> {
                        if (success) {
                            // 直接切换状态
                            isPlaying = !isPlaying;
                            updatePlayPauseButton();
                            android.util.Log.d("FloatingService", "媒体按键发送成功，播放状态: " + (isPlaying ? "播放中" : "暂停"));
                        } else {
                            android.util.Log.e("FloatingService", "媒体按键发送失败");
                        }
                        
                        // 恢复输入状态
                        restoreInputState(hadEditTextFocus, wasKeyboardVisible);
                    });
                } catch (Exception e) {
                    android.util.Log.e("FloatingService", "媒体按键执行异常", e);
                    
                    // 异常时也要恢复状态
                    handler.post(() -> restoreInputState(hadEditTextFocus, wasKeyboardVisible));
                }
            }).start();
        });
        
        rewindBtn.setOnClickListener(v -> {
            android.util.Log.d("FloatingService", "回退按钮点击");
            
            // 保存当前输入状态
            boolean hadEditTextFocus = editNotes.hasFocus();
            boolean wasKeyboardVisible = isKeyboardVisible();
            
            android.util.Log.d("FloatingService", "回退前状态 - 焦点: " + hadEditTextFocus + ", 键盘: " + wasKeyboardVisible);
            
            // 临时清除焦点，让YouTube获得焦点
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
                    
                    // 延迟后恢复输入状态
                    Thread.sleep(200);
                    handler.post(() -> {
                        restoreInputState(hadEditTextFocus, wasKeyboardVisible);
                        android.util.Log.d("FloatingService", "回退操作完成，输入状态已恢复");
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 异常时也要恢复状态
                    handler.post(() -> restoreInputState(hadEditTextFocus, wasKeyboardVisible));
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
                // 字体大小改变后重新应用换行设置
                updateTextWrapSettings();
            }
        });
        
        fontLargerBtn.setOnClickListener(v -> {
            if (currentTextSize < 30f) {
                currentTextSize += 2f;
                editNotes.setTextSize(currentTextSize);
                // 字体大小改变后重新应用换行设置
                updateTextWrapSettings();
            }
        });
        
        // 取消聚焦按钮
        unfocusBtn.setOnClickListener(v -> {
            android.util.Log.d("FloatingService", "取消聚焦按钮点击");
            clearEditTextFocus();
        });
        
        // 关闭按钮
        closeBtn.setOnClickListener(v -> {
            // 保存文本内容
            saveNotes();
            // 停止服务并关闭悬浮窗
            stopSelf();
        });
    }
    
    /**
     * 发送媒体播放/暂停按键
     */
    private boolean sendMediaPlayPauseKey() {
        try {
            android.util.Log.d("FloatingService", "发送媒体播放/暂停按键");
            
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // 创建播放/暂停按键事件
                KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                
                // 发送按键事件
                audioManager.dispatchMediaKeyEvent(downEvent);
                audioManager.dispatchMediaKeyEvent(upEvent);
                
                android.util.Log.d("FloatingService", "媒体按键事件已发送");
                return true;
            } else {
                android.util.Log.e("FloatingService", "AudioManager为null");
                return false;
            }
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "发送媒体按键时出错", e);
            return false;
        }
    }
    
    /**
     * 清除EditText的焦点并隐藏输入法
     */
    private void clearEditTextFocus() {
        try {
            if (editNotes != null && editNotes.hasFocus()) {
                android.util.Log.d("FloatingService", "清除EditText焦点");
                
                // 清除焦点
                editNotes.clearFocus();
                
                // 隐藏输入法
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(editNotes.getWindowToken(), 0);
                }
                
                // 设置悬浮窗为不可聚焦，确保后续点击不会重新获得焦点
                params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(floatingView, params);
                
                android.util.Log.d("FloatingService", "已清除焦点并隐藏输入法");
            } else {
                android.util.Log.d("FloatingService", "EditText没有焦点，无需操作");
            }
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "清除焦点时出错", e);
        }
    }
    
    /**
     * 执行5秒回退操作（仅使用无障碍服务手势）
     */
    private void perform5SecondRewind() {
        android.util.Log.d("FloatingService", "执行5秒回退（无障碍手势模式）");
        
        // 使用双击手势（YouTube标准的5秒回退）
        MediaControlAccessibilityService accessibilityService = 
            MediaControlAccessibilityService.getInstance();
        
        if (accessibilityService != null) {
            boolean success = accessibilityService.performLeftDoubleClick();
            if (success) {
                android.util.Log.d("FloatingService", "5秒回退：双击手势成功");
            } else {
                android.util.Log.d("FloatingService", "5秒回退：双击手势执行失败");
            }
        } else {
            android.util.Log.e("FloatingService", "无障碍服务不可用");
        }
    }
    
    /**
     * 设置EditText的自动换行
     */
    private void setupEditTextWordWrap() {
        if (editNotes != null) {
            // 强制禁用水平滚动
            editNotes.setHorizontallyScrolling(false);
            editNotes.setMaxWidth(420); // 增加宽度
            editNotes.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            
            // 强制设置最大EMS（每行字符数）- 调整到更合理的范围
            editNotes.setMaxEms(25); // 增加每行字符数
            editNotes.setEms(25);
            editNotes.setWidth(420); // 增加像素宽度
            
            // 设置文本改变监听器，确保换行
            editNotes.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // 强制重新布局以确保换行
                    editNotes.post(() -> {
                        editNotes.setHorizontallyScrolling(false);
                        editNotes.setMaxEms(25);
                        editNotes.setWidth(420);
                    });
                }
                
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // 延迟保存文本内容（避免频繁保存）
                    scheduleAutoSave();
                    
                    // 根据当前字体大小动态计算每行字符数限制
                    int maxCharsPerLine = calculateMaxCharsPerLine();
                    
                    String text = s.toString();
                    String[] lines = text.split("\n");
                    boolean needsFormatting = false;
                    
                    for (String line : lines) {
                        if (line.length() > maxCharsPerLine) {
                            needsFormatting = true;
                            break;
                        }
                    }
                    
                    if (needsFormatting) {
                        StringBuilder formattedText = new StringBuilder();
                        for (String line : lines) {
                            if (line.length() <= maxCharsPerLine) {
                                formattedText.append(line).append("\n");
                            } else {
                                // 将长行分割成多行
                                while (line.length() > maxCharsPerLine) {
                                    formattedText.append(line.substring(0, maxCharsPerLine)).append("\n");
                                    line = line.substring(maxCharsPerLine);
                                }
                                if (line.length() > 0) {
                                    formattedText.append(line).append("\n");
                                }
                            }
                        }
                        
                        // 移除监听器避免无限循环
                        editNotes.removeTextChangedListener(this);
                        editNotes.setText(formattedText.toString().trim());
                        editNotes.setSelection(editNotes.length()); // 光标移到末尾
                        editNotes.addTextChangedListener(this);
                    }
                }
            });
        }
    }
    
    /**
     * 根据当前字体大小动态计算每行最大字符数
     */
    private int calculateMaxCharsPerLine() {
        if (editNotes == null) return 20;
        
        // 根据字体大小计算每行字符数
        // 基准：18sp字体大小对应约28个字符（增加宽度）
        float baseFontSize = 18f;
        int baseCharsPerLine = 28;
        
        // 动态调整：字体越大，每行字符数越少
        float ratio = baseFontSize / currentTextSize;
        int maxChars = (int) (baseCharsPerLine * ratio);
        
        // 设置合理的范围：最少18个字符，最多35个字符
        maxChars = Math.max(18, Math.min(35, maxChars));
        
        android.util.Log.d("FloatingService", "字体大小: " + currentTextSize + "sp, 每行最大字符数: " + maxChars);
        return maxChars;
    }
    
    /**
     * 更新文本换行设置（字体大小改变后调用）
     */
    private void updateTextWrapSettings() {
        if (editNotes != null) {
            // 重新计算并应用EMS设置
            int maxChars = calculateMaxCharsPerLine();
            int ems = Math.max(15, Math.min(30, maxChars)); // EMS范围15-30
            
            editNotes.setMaxEms(ems);
            editNotes.setEms(ems);
            
            android.util.Log.d("FloatingService", "更新EMS设置: " + ems);
        }
    }
    
    /**
     * 检查键盘是否可见
     */
    private boolean isKeyboardVisible() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && editNotes != null) {
                return imm.isActive(editNotes);
            }
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "检查键盘状态时出错", e);
        }
        return false;
    }
    
    /**
     * 恢复输入状态（焦点和键盘显示）
     */
    private void restoreInputState(boolean hadFocus, boolean wasKeyboardVisible) {
        try {
            android.util.Log.d("FloatingService", "恢复输入状态 - 焦点: " + hadFocus + ", 键盘: " + wasKeyboardVisible);
            
            if (hadFocus && editNotes != null) {
                // 恢复焦点
                editNotes.requestFocus();
                
                // 如果之前键盘是可见的，则显示键盘
                if (wasKeyboardVisible) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        // 延迟显示键盘，确保焦点已设置
                        handler.postDelayed(() -> {
                            imm.showSoftInput(editNotes, InputMethodManager.SHOW_IMPLICIT);
                        }, 100);
                    }
                }
            }
            
            // 恢复悬浮窗的可聚焦属性
            if (hadFocus) {
                params.flags = params.flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            } else {
                params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
            windowManager.updateViewLayout(floatingView, params);
            
        } catch (Exception e) {
            android.util.Log.e("FloatingService", "恢复输入状态时出错", e);
        }
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
        // 通过无障碍服务检测播放状态
        MediaControlAccessibilityService accessibilityService = 
            MediaControlAccessibilityService.getInstance();
        if (accessibilityService != null && accessibilityService.isYouTubeInForeground()) {
            boolean currentPlayingState = accessibilityService.isYouTubePlaying();
            if (currentPlayingState != isPlaying) {
                isPlaying = currentPlayingState;
                updatePlayPauseButton();
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

    /**
     * 延迟自动保存（避免频繁保存）
     */
    private void scheduleAutoSave() {
        if (handler != null) {
            // 取消之前的保存任务
            if (saveNotesRunnable != null) {
                handler.removeCallbacks(saveNotesRunnable);
            }
            
            // 创建新的保存任务
            saveNotesRunnable = new Runnable() {
                @Override
                public void run() {
                    saveNotes();
                }
            };
            
            // 延迟1秒后保存（用户停止输入1秒后自动保存）
            handler.postDelayed(saveNotesRunnable, 1000);
        }
    }

    /**
     * 保存笔记文本到SharedPreferences
     */
    private void saveNotes() {
        if (editNotes != null) {
            String notes = editNotes.getText().toString();
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(NOTES_KEY, notes);
            editor.apply();
            android.util.Log.d("FloatingService", "已保存笔记，长度: " + notes.length());
        }
    }
    
    /**
     * 从SharedPreferences加载笔记文本
     */
    private void loadSavedNotes() {
        if (editNotes != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedNotes = prefs.getString(NOTES_KEY, "");
            editNotes.setText(savedNotes);
            android.util.Log.d("FloatingService", "已加载笔记，长度: " + savedNotes.length());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 在销毁时保存文本内容
        saveNotes();
        
        // 清理回调
        if (handler != null && saveNotesRunnable != null) {
            handler.removeCallbacks(saveNotesRunnable);
        }
        
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