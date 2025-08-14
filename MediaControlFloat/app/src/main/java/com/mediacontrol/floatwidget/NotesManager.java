package com.mediacontrol.floatwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 笔记管理器 - 负责保存和管理YouTube视频笔记
 */
public class NotesManager {
    private static final String PREFS_NAME = "youtube_notes";
    private static final String KEY_CURRENT_NOTES = "current_notes";
    private static final String KEY_NOTES_HISTORY = "notes_history";
    
    private Context context;
    private SharedPreferences preferences;
    
    public NotesManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 保存当前笔记
     */
    public void saveCurrentNotes(String notes) {
        preferences.edit()
            .putString(KEY_CURRENT_NOTES, notes)
            .apply();
    }
    
    /**
     * 获取当前笔记
     */
    public String getCurrentNotes() {
        return preferences.getString(KEY_CURRENT_NOTES, "");
    }
    
    /**
     * 保存笔记到历史记录
     */
    public void saveNotesToHistory(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            return;
        }
        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date());
        
        String historyEntry = timestamp + "\n" + notes + "\n" + "---\n";
        
        String existingHistory = preferences.getString(KEY_NOTES_HISTORY, "");
        String newHistory = historyEntry + existingHistory;
        
        // 限制历史记录大小（保留最近50条）
        String[] entries = newHistory.split("---\n");
        if (entries.length > 50) {
            StringBuilder limitedHistory = new StringBuilder();
            for (int i = 0; i < 50 && i < entries.length; i++) {
                if (!entries[i].trim().isEmpty()) {
                    limitedHistory.append(entries[i]).append("---\n");
                }
            }
            newHistory = limitedHistory.toString();
        }
        
        preferences.edit()
            .putString(KEY_NOTES_HISTORY, newHistory)
            .apply();
    }
    
    /**
     * 获取笔记历史记录
     */
    public String getNotesHistory() {
        return preferences.getString(KEY_NOTES_HISTORY, "暂无历史笔记记录");
    }
    
    /**
     * 清空当前笔记
     */
    public void clearCurrentNotes() {
        preferences.edit()
            .remove(KEY_CURRENT_NOTES)
            .apply();
    }
    
    /**
     * 清空所有笔记历史
     */
    public void clearAllHistory() {
        preferences.edit()
            .remove(KEY_NOTES_HISTORY)
            .apply();
    }
    
    /**
     * 获取当前YouTube视频信息（如果可能）
     */
    public String getCurrentVideoInfo() {
        // 这里可以扩展来获取当前YouTube视频的标题、时间等信息
        // 目前返回一个简单的时间戳
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }
    
    /**
     * 添加时间戳到笔记
     */
    public String addTimestampToNotes(String currentNotes) {
        String timestamp = getCurrentVideoInfo();
        String timestampLine = "[" + timestamp + "] ";
        
        if (currentNotes.isEmpty()) {
            return timestampLine;
        } else {
            return currentNotes + "\n" + timestampLine;
        }
    }
}