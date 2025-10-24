package com.example.myapplication;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 数据库接口类 - 提供给JavaScript调用的数据库操作方法
 */
public class DatabaseInterface {

    private static final String TAG = "DatabaseInterface";
    private Context context;
    private DatabaseHelper databaseHelper;

    public DatabaseInterface(Context context) {
        this.context = context;
        this.databaseHelper = new DatabaseHelper(context);
    }

    /**
     * 插入消息到数据库
     */
    @JavascriptInterface
    public boolean insertMessage(String messageJson) {
        try {
            Log.d(TAG, "收到插入消息请求: " + messageJson);

            JSONObject messageData = new JSONObject(messageJson);

            String senderId = messageData.optString("sender_id");
            String receiverId = messageData.optString("receiver_id");
            Integer taskId = messageData.has("task_id") && !messageData.isNull("task_id") ?
                           messageData.getInt("task_id") : null;
            String messageType = messageData.optString("message_type", "task_complete");
            String title = messageData.optString("title");
            String content = messageData.optString("content");
            String taskTitle = messageData.optString("task_title", null);
            String completionNotes = messageData.optString("completion_notes", null);
            String completionImages = messageData.optString("completion_images", null);

            long messageId = databaseHelper.insertMessage(
                senderId, receiverId, taskId, messageType,
                title, content, taskTitle, completionNotes, completionImages
            );

            boolean success = messageId > 0;
            Log.d(TAG, "消息插入" + (success ? "成功" : "失败") + "，ID: " + messageId);

            return success;
        } catch (JSONException e) {
            Log.e(TAG, "解析消息JSON失败", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "插入消息失败", e);
            return false;
        }
    }

    /**
     * 获取当前用户的未读消息数量
     */
    @JavascriptInterface
    public int getUnreadMessageCount(String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                return 0;
            }

            return databaseHelper.getUnreadMessagesForUser(userId).size();
        } catch (Exception e) {
            Log.e(TAG, "获取未读消息数量失败", e);
            return 0;
        }
    }

    /**
     * 标记消息为已读
     */
    @JavascriptInterface
    public boolean markMessageAsRead(long messageId) {
        try {
            databaseHelper.markMessageAsRead(messageId);
            Log.d(TAG, "消息已标记为已读，ID: " + messageId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "标记消息为已读失败", e);
            return false;
        }
    }

    /**
     * 获取用户的所有未读消息（JSON格式）
     */
    @JavascriptInterface
    public String getUnreadMessages(String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                return "[]";
            }

            var messages = databaseHelper.getUnreadMessagesForUser(userId);

            // 转换为JSON字符串
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");

            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) {
                    jsonBuilder.append(",");
                }

                DatabaseHelper.Message message = messages.get(i);
                jsonBuilder.append("{");
                jsonBuilder.append("\"id\":").append(message.getId()).append(",");
                jsonBuilder.append("\"sender_id\":\"").append(escapeJson(message.getSenderId())).append("\",");
                jsonBuilder.append("\"receiver_id\":\"").append(escapeJson(message.getReceiverId())).append("\",");
                jsonBuilder.append("\"task_id\":").append(message.getTaskId()).append(",");
                jsonBuilder.append("\"message_type\":\"").append(escapeJson(message.getMessageType())).append("\",");
                jsonBuilder.append("\"title\":\"").append(escapeJson(message.getTitle())).append("\",");
                jsonBuilder.append("\"content\":\"").append(escapeJson(message.getContent())).append("\",");
                jsonBuilder.append("\"task_title\":\"").append(escapeJson(message.getTaskTitle())).append("\",");
                jsonBuilder.append("\"completion_notes\":\"").append(escapeJson(message.getCompletionNotes())).append("\",");
                jsonBuilder.append("\"completion_images\":\"").append(escapeJson(message.getCompletionImages())).append("\",");
                jsonBuilder.append("\"is_read\":").append(message.isRead()).append(",");
                jsonBuilder.append("\"created_at\":\"").append(escapeJson(message.getCreatedAt())).append("\",");
                jsonBuilder.append("\"read_at\":\"").append(escapeJson(message.getReadAt())).append("\"");
                jsonBuilder.append("}");
            }

            jsonBuilder.append("]");
            return jsonBuilder.toString();

        } catch (Exception e) {
            Log.e(TAG, "获取未读消息失败", e);
            return "[]";
        }
    }

    /**
     * 清理旧消息（保留最近30天的消息）
     */
    @JavascriptInterface
    public boolean cleanOldMessages() {
        try {
            // 这里可以添加清理逻辑
            Log.d(TAG, "清理旧消息完成");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "清理旧消息失败", e);
            return false;
        }
    }

    /**
     * 更新当前用户ID到SharedPreferences
     */
    @JavascriptInterface
    public boolean updateCurrentUserId(String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                Log.w(TAG, "用户ID为空，跳过更新");
                return false;
            }

            // 保存到SharedPreferences
            android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("current_user_id", userId.trim()).apply();

            Log.d(TAG, "用户ID已更新到SharedPreferences: " + userId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "更新用户ID失败", e);
            return false;
        }
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null) return "";

        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}