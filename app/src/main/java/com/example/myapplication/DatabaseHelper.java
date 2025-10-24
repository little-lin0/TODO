package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "todo_app.db";
    private static final int DATABASE_VERSION = 2;

    // 消息表
    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_MESSAGE_ID = "id";
    private static final String COLUMN_SENDER_ID = "sender_id";
    private static final String COLUMN_RECEIVER_ID = "receiver_id";
    private static final String COLUMN_TASK_ID = "task_id";
    private static final String COLUMN_MESSAGE_TYPE = "message_type";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_TASK_TITLE = "task_title";
    private static final String COLUMN_COMPLETION_NOTES = "completion_notes";
    private static final String COLUMN_COMPLETION_IMAGES = "completion_images";
    private static final String COLUMN_IS_READ = "is_read";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_READ_AT = "read_at";

    // 设置表
    private static final String TABLE_USER_SETTINGS = "user_settings";
    private static final String COLUMN_SETTINGS_ID = "id";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_RECEIVE_TASK_COMPLETE = "receive_task_complete";
    private static final String COLUMN_RECEIVE_TASK_ASSIGNED = "receive_task_assigned";
    private static final String COLUMN_RECEIVE_SYSTEM_MESSAGES = "receive_system_messages";
    private static final String COLUMN_ALLOWED_SENDERS = "allowed_senders";
    private static final String COLUMN_BLOCKED_SENDERS = "blocked_senders";
    private static final String COLUMN_ENABLE_SOUND = "enable_sound";
    private static final String COLUMN_ENABLE_VIBRATION = "enable_vibration";
    private static final String COLUMN_ENABLE_LED = "enable_led";
    private static final String COLUMN_WORK_START_TIME = "work_start_time";
    private static final String COLUMN_WORK_END_TIME = "work_end_time";
    private static final String COLUMN_RECEIVE_OUTSIDE_WORK_HOURS = "receive_outside_work_hours";
    private static final String COLUMN_MESSAGE_PREVIEW = "message_preview";
    private static final String COLUMN_AUTO_MARK_READ = "auto_mark_read";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createTables(db);
        }
    }

    private void createTables(SQLiteDatabase db) {
        // 创建消息表
        String CREATE_MESSAGES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGES + "("
                + COLUMN_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_SENDER_ID + " TEXT NOT NULL,"
                + COLUMN_RECEIVER_ID + " TEXT NOT NULL,"
                + COLUMN_TASK_ID + " INTEGER,"
                + COLUMN_MESSAGE_TYPE + " TEXT NOT NULL DEFAULT 'task_complete',"
                + COLUMN_TITLE + " TEXT NOT NULL,"
                + COLUMN_CONTENT + " TEXT NOT NULL,"
                + COLUMN_TASK_TITLE + " TEXT,"
                + COLUMN_COMPLETION_NOTES + " TEXT,"
                + COLUMN_COMPLETION_IMAGES + " TEXT,"
                + COLUMN_IS_READ + " BOOLEAN DEFAULT 0,"
                + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + COLUMN_READ_AT + " DATETIME"
                + ")";

        // 创建设置表
        String CREATE_SETTINGS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_USER_SETTINGS + "("
                + COLUMN_SETTINGS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USER_ID + " TEXT NOT NULL UNIQUE,"
                + COLUMN_RECEIVE_TASK_COMPLETE + " BOOLEAN DEFAULT 1,"
                + COLUMN_RECEIVE_TASK_ASSIGNED + " BOOLEAN DEFAULT 1,"
                + COLUMN_RECEIVE_SYSTEM_MESSAGES + " BOOLEAN DEFAULT 1,"
                + COLUMN_ALLOWED_SENDERS + " TEXT,"
                + COLUMN_BLOCKED_SENDERS + " TEXT,"
                + COLUMN_ENABLE_SOUND + " BOOLEAN DEFAULT 1,"
                + COLUMN_ENABLE_VIBRATION + " BOOLEAN DEFAULT 1,"
                + COLUMN_ENABLE_LED + " BOOLEAN DEFAULT 1,"
                + COLUMN_WORK_START_TIME + " TEXT DEFAULT '09:00',"
                + COLUMN_WORK_END_TIME + " TEXT DEFAULT '18:00',"
                + COLUMN_RECEIVE_OUTSIDE_WORK_HOURS + " BOOLEAN DEFAULT 0,"
                + COLUMN_MESSAGE_PREVIEW + " BOOLEAN DEFAULT 1,"
                + COLUMN_AUTO_MARK_READ + " BOOLEAN DEFAULT 0,"
                + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + COLUMN_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";

        db.execSQL(CREATE_MESSAGES_TABLE);
        db.execSQL(CREATE_SETTINGS_TABLE);

        // 创建索引
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_receiver_id ON " + TABLE_MESSAGES + "(" + COLUMN_RECEIVER_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON " + TABLE_MESSAGES + "(" + COLUMN_SENDER_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_created_at ON " + TABLE_MESSAGES + "(" + COLUMN_CREATED_AT + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_is_read ON " + TABLE_MESSAGES + "(" + COLUMN_IS_READ + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_settings_user_id ON " + TABLE_USER_SETTINGS + "(" + COLUMN_USER_ID + ")");
    }

    // 消息相关操作
    public long insertMessage(String senderId, String receiverId, Integer taskId, String messageType,
                             String title, String content, String taskTitle, String completionNotes,
                             String completionImages) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_SENDER_ID, senderId);
        values.put(COLUMN_RECEIVER_ID, receiverId);
        values.put(COLUMN_TASK_ID, taskId);
        values.put(COLUMN_MESSAGE_TYPE, messageType);
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_CONTENT, content);
        values.put(COLUMN_TASK_TITLE, taskTitle);
        values.put(COLUMN_COMPLETION_NOTES, completionNotes);
        values.put(COLUMN_COMPLETION_IMAGES, completionImages);

        long messageId = db.insert(TABLE_MESSAGES, null, values);
        db.close();
        return messageId;
    }

    public List<Message> getUnreadMessagesForUser(String userId) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selectQuery = "SELECT * FROM " + TABLE_MESSAGES +
                           " WHERE " + COLUMN_RECEIVER_ID + " = ? AND " + COLUMN_IS_READ + " = 0" +
                           " ORDER BY " + COLUMN_CREATED_AT + " DESC";

        Cursor cursor = db.rawQuery(selectQuery, new String[]{userId});

        if (cursor.moveToFirst()) {
            do {
                Message message = new Message();
                message.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_MESSAGE_ID)));
                message.setSenderId(cursor.getString(cursor.getColumnIndex(COLUMN_SENDER_ID)));
                message.setReceiverId(cursor.getString(cursor.getColumnIndex(COLUMN_RECEIVER_ID)));
                message.setTaskId(cursor.getInt(cursor.getColumnIndex(COLUMN_TASK_ID)));
                message.setMessageType(cursor.getString(cursor.getColumnIndex(COLUMN_MESSAGE_TYPE)));
                message.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
                message.setContent(cursor.getString(cursor.getColumnIndex(COLUMN_CONTENT)));
                message.setTaskTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TASK_TITLE)));
                message.setCompletionNotes(cursor.getString(cursor.getColumnIndex(COLUMN_COMPLETION_NOTES)));
                message.setCompletionImages(cursor.getString(cursor.getColumnIndex(COLUMN_COMPLETION_IMAGES)));
                message.setRead(cursor.getInt(cursor.getColumnIndex(COLUMN_IS_READ)) == 1);
                message.setCreatedAt(cursor.getString(cursor.getColumnIndex(COLUMN_CREATED_AT)));
                message.setReadAt(cursor.getString(cursor.getColumnIndex(COLUMN_READ_AT)));

                messages.add(message);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return messages;
    }

    public void markMessageAsRead(long messageId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_READ, 1);
        values.put(COLUMN_READ_AT, getCurrentTimestamp());

        db.update(TABLE_MESSAGES, values, COLUMN_MESSAGE_ID + " = ?", new String[]{String.valueOf(messageId)});
        db.close();
    }

    // 设置相关操作
    public UserSettings getUserSettings(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selectQuery = "SELECT * FROM " + TABLE_USER_SETTINGS + " WHERE " + COLUMN_USER_ID + " = ?";
        Cursor cursor = db.rawQuery(selectQuery, new String[]{userId});

        UserSettings settings = null;
        if (cursor.moveToFirst()) {
            settings = new UserSettings();
            settings.setUserId(cursor.getString(cursor.getColumnIndex(COLUMN_USER_ID)));
            settings.setReceiveTaskComplete(cursor.getInt(cursor.getColumnIndex(COLUMN_RECEIVE_TASK_COMPLETE)) == 1);
            settings.setReceiveTaskAssigned(cursor.getInt(cursor.getColumnIndex(COLUMN_RECEIVE_TASK_ASSIGNED)) == 1);
            settings.setReceiveSystemMessages(cursor.getInt(cursor.getColumnIndex(COLUMN_RECEIVE_SYSTEM_MESSAGES)) == 1);
            settings.setAllowedSenders(cursor.getString(cursor.getColumnIndex(COLUMN_ALLOWED_SENDERS)));
            settings.setBlockedSenders(cursor.getString(cursor.getColumnIndex(COLUMN_BLOCKED_SENDERS)));
            settings.setEnableSound(cursor.getInt(cursor.getColumnIndex(COLUMN_ENABLE_SOUND)) == 1);
            settings.setEnableVibration(cursor.getInt(cursor.getColumnIndex(COLUMN_ENABLE_VIBRATION)) == 1);
            settings.setEnableLed(cursor.getInt(cursor.getColumnIndex(COLUMN_ENABLE_LED)) == 1);
            settings.setWorkStartTime(cursor.getString(cursor.getColumnIndex(COLUMN_WORK_START_TIME)));
            settings.setWorkEndTime(cursor.getString(cursor.getColumnIndex(COLUMN_WORK_END_TIME)));
            settings.setReceiveOutsideWorkHours(cursor.getInt(cursor.getColumnIndex(COLUMN_RECEIVE_OUTSIDE_WORK_HOURS)) == 1);
            settings.setMessagePreview(cursor.getInt(cursor.getColumnIndex(COLUMN_MESSAGE_PREVIEW)) == 1);
            settings.setAutoMarkRead(cursor.getInt(cursor.getColumnIndex(COLUMN_AUTO_MARK_READ)) == 1);
        }

        cursor.close();
        db.close();
        return settings;
    }

    public long insertOrUpdateUserSettings(UserSettings settings) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, settings.getUserId());
        values.put(COLUMN_RECEIVE_TASK_COMPLETE, settings.isReceiveTaskComplete() ? 1 : 0);
        values.put(COLUMN_RECEIVE_TASK_ASSIGNED, settings.isReceiveTaskAssigned() ? 1 : 0);
        values.put(COLUMN_RECEIVE_SYSTEM_MESSAGES, settings.isReceiveSystemMessages() ? 1 : 0);
        values.put(COLUMN_ALLOWED_SENDERS, settings.getAllowedSenders());
        values.put(COLUMN_BLOCKED_SENDERS, settings.getBlockedSenders());
        values.put(COLUMN_ENABLE_SOUND, settings.isEnableSound() ? 1 : 0);
        values.put(COLUMN_ENABLE_VIBRATION, settings.isEnableVibration() ? 1 : 0);
        values.put(COLUMN_ENABLE_LED, settings.isEnableLed() ? 1 : 0);
        values.put(COLUMN_WORK_START_TIME, settings.getWorkStartTime());
        values.put(COLUMN_WORK_END_TIME, settings.getWorkEndTime());
        values.put(COLUMN_RECEIVE_OUTSIDE_WORK_HOURS, settings.isReceiveOutsideWorkHours() ? 1 : 0);
        values.put(COLUMN_MESSAGE_PREVIEW, settings.isMessagePreview() ? 1 : 0);
        values.put(COLUMN_AUTO_MARK_READ, settings.isAutoMarkRead() ? 1 : 0);
        values.put(COLUMN_UPDATED_AT, getCurrentTimestamp());

        long result = db.insertWithOnConflict(TABLE_USER_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return result;
    }

    // 检查用户是否应该接收消息
    public boolean shouldReceiveMessage(String receiverId, String senderId, String messageType) {
        UserSettings settings = getUserSettings(receiverId);
        if (settings == null) {
            return true; // 默认接收所有消息
        }

        // 检查消息类型设置
        switch (messageType) {
            case "task_complete":
                if (!settings.isReceiveTaskComplete()) return false;
                break;
            case "task_assigned":
                if (!settings.isReceiveTaskAssigned()) return false;
                break;
            case "system":
                if (!settings.isReceiveSystemMessages()) return false;
                break;
        }

        // 检查发送人黑名单
        if (settings.getBlockedSenders() != null && !settings.getBlockedSenders().isEmpty()) {
            try {
                JSONArray blocked = new JSONArray(settings.getBlockedSenders());
                for (int i = 0; i < blocked.length(); i++) {
                    if (senderId.equals(blocked.getString(i))) {
                        return false;
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing blocked senders", e);
            }
        }

        // 检查发送人白名单
        if (settings.getAllowedSenders() != null && !settings.getAllowedSenders().isEmpty()) {
            try {
                JSONArray allowed = new JSONArray(settings.getAllowedSenders());
                boolean found = false;
                for (int i = 0; i < allowed.length(); i++) {
                    if (senderId.equals(allowed.getString(i))) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing allowed senders", e);
            }
        }

        // 检查工作时间
        if (!settings.isReceiveOutsideWorkHours()) {
            // 这里可以添加工作时间检查逻辑
            // 为简化，暂时跳过
        }

        return true;
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    // 消息数据模型
    public static class Message {
        private long id;
        private String senderId;
        private String receiverId;
        private int taskId;
        private String messageType;
        private String title;
        private String content;
        private String taskTitle;
        private String completionNotes;
        private String completionImages;
        private boolean isRead;
        private String createdAt;
        private String readAt;

        // Getters and Setters
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }

        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }

        public String getReceiverId() { return receiverId; }
        public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

        public int getTaskId() { return taskId; }
        public void setTaskId(int taskId) { this.taskId = taskId; }

        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getTaskTitle() { return taskTitle; }
        public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }

        public String getCompletionNotes() { return completionNotes; }
        public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }

        public String getCompletionImages() { return completionImages; }
        public void setCompletionImages(String completionImages) { this.completionImages = completionImages; }

        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getReadAt() { return readAt; }
        public void setReadAt(String readAt) { this.readAt = readAt; }
    }

    // 用户设置数据模型
    public static class UserSettings {
        private String userId;
        private boolean receiveTaskComplete = true;
        private boolean receiveTaskAssigned = true;
        private boolean receiveSystemMessages = true;
        private String allowedSenders;
        private String blockedSenders;
        private boolean enableSound = true;
        private boolean enableVibration = true;
        private boolean enableLed = true;
        private String workStartTime = "09:00";
        private String workEndTime = "18:00";
        private boolean receiveOutsideWorkHours = false;
        private boolean messagePreview = true;
        private boolean autoMarkRead = false;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public boolean isReceiveTaskComplete() { return receiveTaskComplete; }
        public void setReceiveTaskComplete(boolean receiveTaskComplete) { this.receiveTaskComplete = receiveTaskComplete; }

        public boolean isReceiveTaskAssigned() { return receiveTaskAssigned; }
        public void setReceiveTaskAssigned(boolean receiveTaskAssigned) { this.receiveTaskAssigned = receiveTaskAssigned; }

        public boolean isReceiveSystemMessages() { return receiveSystemMessages; }
        public void setReceiveSystemMessages(boolean receiveSystemMessages) { this.receiveSystemMessages = receiveSystemMessages; }

        public String getAllowedSenders() { return allowedSenders; }
        public void setAllowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; }

        public String getBlockedSenders() { return blockedSenders; }
        public void setBlockedSenders(String blockedSenders) { this.blockedSenders = blockedSenders; }

        public boolean isEnableSound() { return enableSound; }
        public void setEnableSound(boolean enableSound) { this.enableSound = enableSound; }

        public boolean isEnableVibration() { return enableVibration; }
        public void setEnableVibration(boolean enableVibration) { this.enableVibration = enableVibration; }

        public boolean isEnableLed() { return enableLed; }
        public void setEnableLed(boolean enableLed) { this.enableLed = enableLed; }

        public String getWorkStartTime() { return workStartTime; }
        public void setWorkStartTime(String workStartTime) { this.workStartTime = workStartTime; }

        public String getWorkEndTime() { return workEndTime; }
        public void setWorkEndTime(String workEndTime) { this.workEndTime = workEndTime; }

        public boolean isReceiveOutsideWorkHours() { return receiveOutsideWorkHours; }
        public void setReceiveOutsideWorkHours(boolean receiveOutsideWorkHours) { this.receiveOutsideWorkHours = receiveOutsideWorkHours; }

        public boolean isMessagePreview() { return messagePreview; }
        public void setMessagePreview(boolean messagePreview) { this.messagePreview = messagePreview; }

        public boolean isAutoMarkRead() { return autoMarkRead; }
        public void setAutoMarkRead(boolean autoMarkRead) { this.autoMarkRead = autoMarkRead; }
    }
}