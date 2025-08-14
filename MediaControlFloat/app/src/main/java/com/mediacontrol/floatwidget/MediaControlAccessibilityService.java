package com.mediacontrol.floatwidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import java.io.DataOutputStream;
import java.util.List;

public class MediaControlAccessibilityService extends AccessibilityService {
    private static MediaControlAccessibilityService instance;
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static MediaControlAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 监听无障碍事件，特别关注窗口状态变化
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? 
                event.getPackageName().toString() : "";
            
            // 记录当前活动的应用
            if (YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                Log.d("AccessibilityService", "YouTube window became active: " + packageName);
            }
        }
    }

    @Override
    public void onInterrupt() {
        // 服务被中断时的处理
    }

    /**
     * 发送左方向键到YouTube应用
     */
    public boolean sendLeftArrowToYouTube() {
        try {
            // 方法1: 直接使用shell命令发送按键（最可靠的方法）
            boolean shellSuccess = sendKeyViaShell(KeyEvent.KEYCODE_DPAD_LEFT);
            if (shellSuccess) {
                return true;
            }
            
            // 方法2: 尝试通过无障碍服务操作
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String packageName = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                
                // 检查当前前台应用是否是YouTube
                if (YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                    // 尝试模拟触摸手势来触发左方向键功能
                    return simulateLeftSwipeGesture();
                }
            }

            // 方法3: 备用方案
            return performGlobalKeyAction(KeyEvent.KEYCODE_DPAD_LEFT);
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向当前活动窗口发送按键事件
     */
    private boolean sendKeyEventToActiveWindow(int keyCode) {
        try {
            // 创建按键事件
            KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            
            // 获取根节点
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                // 查找可以接收按键事件的节点
                AccessibilityNodeInfo focusableNode = findFocusableNode(rootNode);
                if (focusableNode != null) {
                    // 尝试设置焦点并发送按键
                    focusableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    
                    // 发送按键事件到系统
                    return dispatchKeyEvent(downEvent) && dispatchKeyEvent(upEvent);
                }
                
                // 如果没有找到焦点节点，尝试直接在根节点上操作
                return dispatchKeyEvent(downEvent) && dispatchKeyEvent(upEvent);
            }
            
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 查找可以获得焦点的节点
     */
    private AccessibilityNodeInfo findFocusableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        // 检查当前节点是否可以获得焦点
        if (node.isFocusable() || node.isClickable()) {
            return node;
        }
        
        // 递归查找子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo focusableChild = findFocusableNode(child);
                if (focusableChild != null) {
                    return focusableChild;
                }
            }
        }
        
        return null;
    }

    /**
     * 使用shell命令发送按键事件
     */
    private boolean sendKeyViaShell(int keyCode) {
        try {
            // 方法1: 尝试使用su权限
            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
                os.writeBytes("input keyevent " + keyCode + "\n");
                os.writeBytes("exit\n");
                os.flush();
                os.close();
                int exitCode = suProcess.waitFor();
                if (exitCode == 0) {
                    return true;
                }
            } catch (Exception e) {
                // Root权限不可用，继续尝试其他方法
            }
            
            // 方法2: 直接执行input命令（某些设备可能支持）
            Process process = Runtime.getRuntime().exec("input keyevent " + keyCode);
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 模拟左滑手势来触发后退功能
     */
    private boolean simulateLeftSwipeGesture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 获取屏幕尺寸
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    Rect bounds = new Rect();
                    rootNode.getBoundsInScreen(bounds);
                    
                    // 在屏幕中央执行双击手势（YouTube的双击后退功能）
                    int centerX = bounds.centerX();
                    int centerY = bounds.centerY();
                    int leftX = centerX - 200; // 左侧位置
                    
                    return performDoubleClick(leftX, centerY);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
    
    /**
     * 执行双击手势
     */
    private boolean performDoubleClick(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 创建双击手势
                Path clickPath = new Path();
                clickPath.moveTo(x, y);
                
                GestureDescription.StrokeDescription firstClick = 
                    new GestureDescription.StrokeDescription(clickPath, 0, 50);
                GestureDescription.StrokeDescription secondClick = 
                    new GestureDescription.StrokeDescription(clickPath, 100, 50);
                
                GestureDescription gestureDescription = new GestureDescription.Builder()
                    .addStroke(firstClick)
                    .addStroke(secondClick)
                    .build();
                
                return dispatchGesture(gestureDescription, null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 执行全局动作发送按键
     */
    private boolean performGlobalKeyAction(int keyCode) {
        try {
            // 使用shell命令作为备用方案
            return sendKeyViaShell(keyCode);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 分发按键事件到系统
     */
    private boolean dispatchKeyEvent(KeyEvent event) {
        try {
            // 这里我们使用Runtime执行input命令，因为AccessibilityService
            // 本身不能直接调用dispatchKeyEvent方法
            String command = "input keyevent " + event.getKeyCode();
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查YouTube应用是否在前台
     */
    public boolean isYouTubeInForeground() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String packageName = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                return YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName);
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送播放/暂停键
     */
    public boolean sendPlayPauseToYouTube() {
        try {
            return sendKeyEventToActiveWindow(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 确保YouTube窗口获得焦点并准备接收按键事件
     */
    public boolean ensureYouTubeFocus() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String packageName = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                
                if (YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                    // YouTube已经在前台，确保有可接收焦点的节点
                    AccessibilityNodeInfo focusableNode = findFocusableNode(rootNode);
                    if (focusableNode != null) {
                        boolean focusSet = focusableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        Log.d("AccessibilityService", "Set focus on YouTube node: " + focusSet);
                        return focusSet;
                    } else {
                        // 如果没有找到特定的焦点节点，尝试在根节点上设置焦点
                        boolean focusSet = rootNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        Log.d("AccessibilityService", "Set focus on YouTube root: " + focusSet);
                        return focusSet;
                    }
                }
            }
            
            Log.d("AccessibilityService", "YouTube is not the active window");
            return false;
            
        } catch (Exception e) {
            Log.e("AccessibilityService", "Error ensuring YouTube focus", e);
            return false;
        }
    }

    /**
     * 向YouTube发送按键前先确保焦点正确
     */
    public boolean sendKeyWithFocusEnsurance(int keyCode) {
        // 步骤1: 确保YouTube有焦点
        ensureYouTubeFocus();
        
        // 步骤2: 短暂延迟让焦点设置生效
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 步骤3: 发送按键
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return sendLeftArrowToYouTube();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return sendPlayPauseToYouTube();
        }
        
        return false;
    }
}