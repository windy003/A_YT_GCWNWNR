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
     * 发送左方向键到YouTube应用进行10秒回退
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
            // 方法1: 直接执行input命令（某些设备可能支持）
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

    /**
     * 执行左上侧双击手势（5秒回退）- 安全版本
     */
    public boolean performLeftDoubleClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 首先检查YouTube是否在前台
                if (!isYouTubeInForeground()) {
                    Log.w("AccessibilityService", "YouTube不在前台，跳过双击回退操作");
                    return false;
                }
                
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    String packageName = rootNode.getPackageName() != null ? 
                        rootNode.getPackageName().toString() : "";
                    
                    // 再次确认是YouTube应用
                    if (!YOUTUBE_PACKAGE.equals(packageName) && !YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                        Log.w("AccessibilityService", "当前应用不是YouTube: " + packageName);
                        return false;
                    }
                    
                    Rect bounds = new Rect();
                    rootNode.getBoundsInScreen(bounds);
                    
                    // 基于用户反馈的有效坐标 (96, 445) 进行调整
                    int targetX = 96;
                    int targetY = 445;
                    
                    // 按比例调整适配不同屏幕
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        float xRatio = 96f / 1080f; // 约8.9%
                        float yRatio = 445f / 2340f; // 约19%
                        
                        targetX = (int)(bounds.width() * xRatio);
                        targetY = bounds.top + (int)(bounds.height() * yRatio);
                        
                        // 安全边界检查，避免点击系统区域
                        targetX = Math.max(80, Math.min(targetX, bounds.width() / 3));
                        targetY = Math.max(bounds.top + 200, Math.min(targetY, bounds.height() / 2));
                    }
                    
                    Log.d("AccessibilityService", "安全双击位置: (" + targetX + ", " + targetY + ")");
                    Log.d("AccessibilityService", "屏幕范围: " + bounds.toString());
                    Log.d("AccessibilityService", "目标应用: " + packageName);
                    
                    return performDoubleClickAt(targetX, targetY);
                }
            } catch (Exception e) {
                Log.e("AccessibilityService", "双击手势执行失败", e);
            }
        }
        return false;
    }
    
    /**
     * 在指定位置执行双击手势（针对YouTube优化）
     */
    private boolean performDoubleClickAt(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 创建双击路径
                Path clickPath = new Path();
                clickPath.moveTo(x, y);
                
                // 第一次点击（持续时间50ms）
                GestureDescription.StrokeDescription firstClick = 
                    new GestureDescription.StrokeDescription(clickPath, 0, 50);
                
                // 第二次点击（间隔200ms，YouTube双击识别的最佳间隔）
                GestureDescription.StrokeDescription secondClick = 
                    new GestureDescription.StrokeDescription(clickPath, 200, 50);
                
                GestureDescription gestureDescription = new GestureDescription.Builder()
                    .addStroke(firstClick)
                    .addStroke(secondClick)
                    .build();
                
                Log.d("AccessibilityService", "发送双击手势，间隔200ms");
                return dispatchGesture(gestureDescription, null, null);
            } catch (Exception e) {
                Log.e("AccessibilityService", "执行双击手势时发生错误", e);
            }
        }
        return false;
    }
    
    /**
     * 执行播放/暂停点击手势 - 安全版本
     */
    public boolean performPlayPauseClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 首先检查YouTube是否在前台
                if (!isYouTubeInForeground()) {
                    Log.w("AccessibilityService", "YouTube不在前台，跳过播放/暂停操作");
                    return false;
                }
                
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    String packageName = rootNode.getPackageName() != null ? 
                        rootNode.getPackageName().toString() : "";
                    
                    // 再次确认是YouTube应用
                    if (!YOUTUBE_PACKAGE.equals(packageName) && !YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                        Log.w("AccessibilityService", "当前应用不是YouTube: " + packageName);
                        return false;
                    }
                    
                    Rect bounds = new Rect();
                    rootNode.getBoundsInScreen(bounds);
                    
                    // YouTube播放/暂停区域在视频中央，更保守的位置
                    int centerX = bounds.centerX();
                    int centerY = bounds.centerY(); // 改为正中央，更安全
                    
                    Log.d("AccessibilityService", "播放/暂停安全点击位置: (" + centerX + ", " + centerY + ")");
                    Log.d("AccessibilityService", "目标应用: " + packageName);
                    
                    return performSingleClickAt(centerX, centerY);
                }
            } catch (Exception e) {
                Log.e("AccessibilityService", "播放/暂停手势执行失败", e);
            }
        }
        return false;
    }
    
    /**
     * 在指定位置执行单击手势 - 安全版本
     */
    private boolean performSingleClickAt(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 添加延迟，避免过快的手势操作
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                
                Path clickPath = new Path();
                clickPath.moveTo(x, y);
                
                // 缩短点击时间，减少对系统的影响
                GestureDescription.StrokeDescription clickStroke = 
                    new GestureDescription.StrokeDescription(clickPath, 0, 50);
                
                GestureDescription gestureDescription = new GestureDescription.Builder()
                    .addStroke(clickStroke)
                    .build();
                
                Log.d("AccessibilityService", "发送安全单击手势");
                
                // 使用回调来监控手势执行结果
                final boolean[] result = {false};
                GestureResultCallback callback = new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        Log.d("AccessibilityService", "单击手势执行完成");
                        result[0] = true;
                    }
                    
                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        Log.w("AccessibilityService", "单击手势被取消");
                    }
                };
                
                return dispatchGesture(gestureDescription, callback, null);
            } catch (Exception e) {
                Log.e("AccessibilityService", "执行单击手势时发生错误", e);
            }
        }
        return false;
    }
    
    /**
     * 检测YouTube是否正在播放
     */
    public boolean isYouTubePlaying() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String packageName = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                
                // 确认当前是YouTube应用
                if (YOUTUBE_PACKAGE.equals(packageName) || YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
                    // 查找播放/暂停按钮来判断状态
                    // 注意：如果界面显示"播放"按钮，说明当前是暂停状态
                    // 如果界面显示"暂停"按钮，说明当前正在播放
                    AccessibilityNodeInfo playButton = findNodeByContentDescription(rootNode, "播放");
                    AccessibilityNodeInfo pauseButton = findNodeByContentDescription(rootNode, "暂停");
                    
                    // 如果找到"暂停"按钮，说明正在播放中
                    if (pauseButton != null) {
                        Log.d("AccessibilityService", "找到暂停按钮，正在播放");
                        return true;
                    }
                    // 如果找到"播放"按钮，说明当前已暂停
                    if (playButton != null) {
                        Log.d("AccessibilityService", "找到播放按钮，已暂停");
                        return false;
                    }
                    
                    // 备用方案：查找英文描述
                    AccessibilityNodeInfo playButtonEn = findNodeByContentDescription(rootNode, "Play");
                    AccessibilityNodeInfo pauseButtonEn = findNodeByContentDescription(rootNode, "Pause");
                    
                    if (pauseButtonEn != null) {
                        Log.d("AccessibilityService", "找到Pause按钮，正在播放");
                        return true;
                    }
                    if (playButtonEn != null) {
                        Log.d("AccessibilityService", "找到Play按钮，已暂停");
                        return false;
                    }
                    
                    // 默认假设已暂停（更保守的方案）
                    Log.d("AccessibilityService", "未找到播放/暂停按钮，默认为暂停状态");
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e("AccessibilityService", "检测播放状态时出错", e);
        }
        
        // 默认返回false（暂停状态）
        return false;
    }
    
    /**
     * 根据内容描述查找节点
     */
    private AccessibilityNodeInfo findNodeByContentDescription(AccessibilityNodeInfo node, String description) {
        if (node == null || description == null) return null;
        
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.toString().contains(description)) {
            return node;
        }
        
        // 递归查找子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findNodeByContentDescription(child, description);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
}