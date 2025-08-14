package com.mediacontrol.floatwidget;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Root权限帮助类
 */
public class RootHelper {
    private static final String TAG = "RootHelper";
    private static Boolean isRootAvailable = null;
    private static Boolean isRootGranted = null;

    /**
     * 检查设备是否已root
     */
    public static boolean isDeviceRooted() {
        if (isRootAvailable != null) {
            return isRootAvailable;
        }

        try {
            // 检查常见的root文件
            String[] rootPaths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/su/bin/su"
            };

            for (String path : rootPaths) {
                if (new java.io.File(path).exists()) {
                    isRootAvailable = true;
                    Log.d(TAG, "Root path found: " + path);
                    return true;
                }
            }

            // 尝试执行which su命令
            Process process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                isRootAvailable = true;
                Log.d(TAG, "su command found at: " + line);
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking root status", e);
        }

        isRootAvailable = false;
        Log.d(TAG, "Device is not rooted");
        return false;
    }

    /**
     * 请求root权限
     */
    public static boolean requestRootPermission() {
        if (isRootGranted != null) {
            return isRootGranted;
        }

        if (!isDeviceRooted()) {
            Log.d(TAG, "Device is not rooted, cannot grant root permission");
            isRootGranted = false;
            return false;
        }

        try {
            Log.d(TAG, "Requesting root permission...");
            
            // 尝试获取su权限
            Process suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(suProcess.getErrorStream()));

            // 发送测试命令
            os.writeBytes("id\n");
            os.flush();

            // 等待一段时间让用户响应权限请求（减少延迟避免ANR）
            Thread.sleep(1000);

            // 发送exit命令
            os.writeBytes("exit\n");
            os.flush();
            os.close();

            // 检查进程退出状态
            int exitValue = suProcess.waitFor();
            
            // 读取输出
            String output = "";
            String line;
            while ((line = reader.readLine()) != null) {
                output += line + "\n";
            }
            
            String error = "";
            while ((line = errorReader.readLine()) != null) {
                error += line + "\n";
            }

            Log.d(TAG, "Su process exit value: " + exitValue);
            Log.d(TAG, "Su process output: " + output);
            if (!error.isEmpty()) {
                Log.d(TAG, "Su process error: " + error);
            }

            // 检查是否成功获取root权限
            boolean success = (exitValue == 0) && output.contains("uid=0");
            
            if (success) {
                Log.d(TAG, "Root permission granted successfully");
                isRootGranted = true;
            } else {
                Log.d(TAG, "Root permission denied or failed");
                isRootGranted = false;
            }

            reader.close();
            errorReader.close();
            
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error requesting root permission", e);
            isRootGranted = false;
            return false;
        }
    }

    /**
     * 检查是否已获得root权限
     */
    public static boolean isRootGranted() {
        if (isRootGranted != null) {
            return isRootGranted;
        }
        return requestRootPermission();
    }

    /**
     * 执行需要root权限的命令
     */
    public static boolean executeRootCommand(String command) {
        return executeRootCommand(new String[]{command});
    }

    /**
     * 执行需要root权限的命令数组
     */
    public static boolean executeRootCommand(String[] commands) {
        if (!isRootGranted()) {
            Log.d(TAG, "Root permission not available for command execution");
            return false;
        }

        try {
            Log.d(TAG, "Executing root commands: " + java.util.Arrays.toString(commands));
            
            Process suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

            for (String command : commands) {
                os.writeBytes(command + "\n");
                os.flush();
            }

            os.writeBytes("exit\n");
            os.flush();
            os.close();

            int exitValue = suProcess.waitFor();
            boolean success = (exitValue == 0);
            
            Log.d(TAG, "Root command execution " + (success ? "succeeded" : "failed") + 
                     " with exit value: " + exitValue);
            
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error executing root command", e);
            return false;
        }
    }

    /**
     * 重置root权限状态（用于重新检测）
     */
    public static void resetRootStatus() {
        isRootAvailable = null;
        isRootGranted = null;
        Log.d(TAG, "Root status reset");
    }

    /**
     * 发送按键事件（使用root权限）
     */
    public static boolean sendKeyEvent(int keyCode) {
        String[] commands = {
            "input keyevent " + keyCode,
            "sleep 0.1"  // 短暂延迟确保按键被处理
        };
        return executeRootCommand(commands);
    }
    
    /**
     * 发送YouTube专用的左方向键（确保焦点正确）
     */
    public static boolean sendYouTubeLeftKey() {
        Log.d(TAG, "发送左方向键21到YouTube");
        
        // 直接发送左方向键（按键码21）
        String[] commands = {
            "input keyevent 21"
        };
        
        boolean success = executeRootCommand(commands);
        if (success) {
            Log.d(TAG, "左方向键21发送成功");
        } else {
            Log.d(TAG, "左方向键21发送失败");
        }
        
        return success;
    }

    /**
     * 发送按键按下和释放事件（使用root权限）
     */
    public static boolean sendKeyDownUp(int keyCode) {
        String[] commands = {
            "sendevent /dev/input/event0 1 " + keyCode + " 1",  // 按下
            "sendevent /dev/input/event0 0 0 0",                // 同步
            "sleep 0.05",
            "sendevent /dev/input/event0 1 " + keyCode + " 0",  // 释放
            "sendevent /dev/input/event0 0 0 0"                 // 同步
        };
        return executeRootCommand(commands);
    }
}