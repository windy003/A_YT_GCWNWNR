package com.mediacontrol.floatwidget;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.accessibilityservice.AccessibilityService;
import java.io.IOException;

public class MediaKeySimulator {
    private Context context;
    private AudioManager audioManager;
    private KeyboardSimulator keyboardSimulator;
    private DirectKeyInjector directKeyInjector;

    public MediaKeySimulator(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.keyboardSimulator = new KeyboardSimulator(context);
        this.directKeyInjector = new DirectKeyInjector(context);
    }

    public void sendPlayPause() {
        // 直接使用标准媒体键（这个通常都能工作）
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public void sendRewind5Seconds() {
        // 方法1: 使用DirectKeyInjector（最可靠的方法）
        boolean success = directKeyInjector.sendLeftArrowKey();
        if (success) {
            return;
        }
        
        // 方法2: 尝试AccessibilityService
        MediaControlAccessibilityService accessibilityService = 
            MediaControlAccessibilityService.getInstance();
        
        if (accessibilityService != null && accessibilityService.isYouTubeInForeground()) {
            success = accessibilityService.sendLeftArrowToYouTube();
            if (success) {
                return;
            }
        }
        
        // 方法3: 备用方法
        keyboardSimulator.sendLeftArrowKey();
    }

    public void sendLeftArrowKey() {
        // 直接发送左方向键
        sendKeyboardKey(KeyEvent.KEYCODE_DPAD_LEFT);
    }

    private void sendKeyboardKey(int keyCode) {
        try {
            // 方法1: 直接发送按键事件
            KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            
            // 尝试通过广播发送按键事件
            sendKeyIntent(keyCode);
            
            // 方法2: 尝试通过shell命令发送按键（需要root权限）
            sendKeyViaShell(keyCode);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendKeyIntent(int keyCode) {
        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        
        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        
        context.sendBroadcast(downIntent);
        context.sendBroadcast(upIntent);
    }

    private void sendKeyViaShell(int keyCode) {
        try {
            // 使用adb shell input命令发送按键
            Runtime.getRuntime().exec("input keyevent " + keyCode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMediaKey(int keyCode) {
        try {
            // 方法1: 使用AudioManager (适用于较老的Android版本)
            if (audioManager != null) {
                KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
                
                audioManager.dispatchMediaKeyEvent(downEvent);
                audioManager.dispatchMediaKeyEvent(upEvent);
            }

            // 方法2: 使用广播Intent (兼容性更好)
            sendMediaKeyIntent(keyCode);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMediaKeyIntent(int keyCode) {
        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        
        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        
        context.sendOrderedBroadcast(downIntent, null);
        context.sendOrderedBroadcast(upIntent, null);
    }
}