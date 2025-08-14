package com.mediacontrol.floatwidget;

import android.content.Context;
import android.view.KeyEvent;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 直接按键注入器 - 使用多种方法尝试发送按键到系统
 */
public class DirectKeyInjector {
    private Context context;

    public DirectKeyInjector(Context context) {
        this.context = context;
    }

    /**
     * 发送左方向键到当前前台应用
     */
    public boolean sendLeftArrowKey() {
        // 尝试多种方法，按优先级排序
        
        // 方法1: 优先使用Root权限（最可靠）
        if (RootHelper.isRootGranted()) {
            if (RootHelper.sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)) {
                return true;
            }
            // 如果简单方法失败，尝试低级别的sendevent
            if (RootHelper.sendKeyDownUp(KeyEvent.KEYCODE_DPAD_LEFT)) {
                return true;
            }
        }
        
        // 方法2: 使用ADB Shell命令
        if (sendKeyViaADB(KeyEvent.KEYCODE_DPAD_LEFT)) {
            return true;
        }
        
        // 方法3: 使用Runtime.exec执行input命令
        if (sendKeyViaInput(KeyEvent.KEYCODE_DPAD_LEFT)) {
            return true;
        }
        
        // 方法4: 尝试使用su权限（备用方案）
        if (sendKeyViaRoot(KeyEvent.KEYCODE_DPAD_LEFT)) {
            return true;
        }
        
        // 方法5: 发送多个媒体按键作为替代
        return sendAlternativeMediaKeys();
    }

    /**
     * 使用ADB shell命令发送按键
     */
    private boolean sendKeyViaADB(int keyCode) {
        try {
            String[] commands = {
                "input keyevent " + keyCode,
                "am broadcast -a android.intent.action.MEDIA_BUTTON --ei android.intent.extra.KEY_EVENT " + keyCode
            };
            
            for (String command : commands) {
                try {
                    Process process = Runtime.getRuntime().exec(command);
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        return true;
                    }
                } catch (Exception e) {
                    continue; // 尝试下一个命令
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 使用input命令发送按键
     */
    private boolean sendKeyViaInput(int keyCode) {
        try {
            // 尝试不同的input命令变体
            String[] commands = {
                "input keyevent " + keyCode,
                "input key " + keyCode,
                "/system/bin/input keyevent " + keyCode
            };
            
            for (String command : commands) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(command.split(" "));
                    Process process = pb.start();
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 使用su权限发送按键
     */
    private boolean sendKeyViaRoot(int keyCode) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            
            // 尝试多个命令
            String[] commands = {
                "input keyevent " + keyCode,
                "sendevent /dev/input/event0 1 " + keyCode + " 1",
                "sendevent /dev/input/event0 1 " + keyCode + " 0"
            };
            
            for (String command : commands) {
                os.writeBytes(command + "\n");
            }
            
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            
            int exitCode = su.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送替代的媒体按键
     */
    private boolean sendAlternativeMediaKeys() {
        try {
            // 发送多个快速的媒体后退键来模拟5秒后退
            for (int i = 0; i < 5; i++) {
                sendKeyViaInput(KeyEvent.KEYCODE_MEDIA_REWIND);
                Thread.sleep(100);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送播放/暂停键
     */
    public boolean sendPlayPauseKey() {
        // 优先使用Root权限
        if (RootHelper.isRootGranted()) {
            if (RootHelper.sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                return true;
            }
        }
        
        return sendKeyViaInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) ||
               sendKeyViaADB(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) ||
               sendKeyViaRoot(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }
}