package com.mediacontrol.floatwidget;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import java.io.IOException;
import java.io.DataOutputStream;

public class KeyboardSimulator {
    private Context context;

    public KeyboardSimulator(Context context) {
        this.context = context;
    }

    /**
     * 发送左方向键模拟后退5秒
     */
    public void sendLeftArrowKey() {
        // 尝试多种方法发送左方向键
        boolean success = false;
        
        // 方法1: 直接发送左方向键 (键盘模拟)
        if (!success) {
            success = sendKeyViaBroadcast(KeyEvent.KEYCODE_DPAD_LEFT);
        }
        
        // 方法2: 尝试发送PC键盘左箭头键
        if (!success) {
            success = sendKeyViaBroadcast(37); // 左箭头键的码值
        }
        
        // 方法3: 通过Shell命令发送 (需要ROOT权限)
        if (!success) {
            success = sendKeyViaShell(KeyEvent.KEYCODE_DPAD_LEFT);
        }
        
        // 方法4: 作为后备方案，发送媒体控制键
        if (!success) {
            sendMediaKeyViaBroadcast(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);
        }
        
        // 方法5: 最后的备选方案 - 发送多个后退信号
        if (!success) {
            sendMultipleRewindSignals();
        }
    }
    
    /**
     * 发送多个后退信号模拟5秒后退
     */
    private void sendMultipleRewindSignals() {
        for (int i = 0; i < 5; i++) {
            sendMediaKeyViaBroadcast(KeyEvent.KEYCODE_MEDIA_REWIND);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 通过广播发送按键事件
     */
    private boolean sendKeyViaBroadcast(int keyCode) {
        try {
            // 发送按键按下事件
            Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
            context.sendBroadcast(downIntent);
            
            // 短暂延迟
            Thread.sleep(50);
            
            // 发送按键抬起事件
            Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
            context.sendBroadcast(upIntent);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 通过Shell命令发送按键 (需要ROOT权限)
     */
    private boolean sendKeyViaShell(int keyCode) {
        try {
            // 使用input命令发送按键事件
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("input keyevent " + keyCode + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送媒体控制键作为备选方案
     */
    private boolean sendMediaKeyViaBroadcast(int keyCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            
            intent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
            context.sendOrderedBroadcast(intent, null);
            
            Thread.sleep(50);
            
            intent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
            context.sendOrderedBroadcast(intent, null);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 尝试通过无障碍服务发送按键 (如果可用)
     */
    public void sendKeyViaAccessibility(int keyCode) {
        // 这需要无障碍服务支持，暂时作为占位符
        // 实际实现需要创建AccessibilityService
    }
}