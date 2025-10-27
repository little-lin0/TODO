package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Supabase数据库接口类 - 提供给JavaScript调用的Supabase操作方法
 */
public class SupabaseInterface {

    private static final String TAG = "SupabaseInterface";
    private Context context;
    private ExecutorService executorService;
    private android.webkit.WebView webView; // 用于刷新前端页面

    public SupabaseInterface(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.webView = null;
    }

    public SupabaseInterface(Context context, android.webkit.WebView webView) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.webView = webView;
    }

    /**
     * 异步获取用户的未读消息
     */
    @JavascriptInterface
    public CompletableFuture<String> getUnreadMessagesAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                String supabaseUrl = prefs.getString("supabase_url", "");
                String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
                String supabaseUserId = prefs.getString("supabase_user_id", "");

                if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                    Log.w(TAG, "Supabase配置不完整");
                    return "[]";
                }

                // 构建查询URL：获取当前用户作为接收人的未读消息，且发送人不是自己
                String queryUrl = supabaseUrl + "/rest/v1/messages" +
                                "?user_id=eq." + supabaseUserId +
                                "&receiver_id=eq." + userId +
                                "&is_read=eq.false" +
                                "&sender_id=neq." + userId +
                                "&order=created_at.desc";

                URL url = new URL(queryUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", supabaseAnonKey);
                connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
                connection.setRequestProperty("Content-Type", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String result = response.toString();
                    Log.d(TAG, "获取未读消息成功，用户: " + userId);
                    return result;
                } else {
                    Log.e(TAG, "获取未读消息失败，响应码: " + responseCode);
                    return "[]";
                }
            } catch (Exception e) {
                Log.e(TAG, "获取未读消息异常", e);
                return "[]";
            }
        }, executorService);
    }

    /**
     * 同步获取用户的未读消息（阻塞版本，用于服务中调用）
     */
    public String getUnreadMessages(String userId) {
        try {
            return getUnreadMessagesAsync(userId).get();
        } catch (Exception e) {
            Log.e(TAG, "同步获取未读消息失败", e);
            return "[]";
        }
    }

    /**
     * 解析消息JSON并返回简化的消息列表
     */
    public java.util.List<SimpleMessage> parseMessages(String messagesJson) {
        java.util.List<SimpleMessage> messages = new java.util.ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(messagesJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject messageObj = jsonArray.getJSONObject(i);
                SimpleMessage message = new SimpleMessage();
                message.id = messageObj.optLong("id");
                message.senderId = messageObj.optString("sender_id");
                message.receiverId = messageObj.optString("receiver_id");
                message.messageType = messageObj.optString("message_type");
                message.title = messageObj.optString("title");
                message.content = messageObj.optString("content");
                message.taskTitle = messageObj.optString("task_title");
                message.completionNotes = messageObj.optString("completion_notes");
                message.createdAt = messageObj.optString("created_at");
                message.isRead = messageObj.optBoolean("is_read");
                messages.add(message);
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析消息JSON失败", e);
        }
        return messages;
    }

    /**
     * 异步标记消息为已读
     */
    @JavascriptInterface
    public CompletableFuture<Boolean> markMessageAsReadAsync(long messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                String supabaseUrl = prefs.getString("supabase_url", "");
                String supabaseAnonKey = prefs.getString("supabase_anon_key", "");

                if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                    Log.w(TAG, "Supabase配置不完整");
                    return false;
                }

                String updateUrl = supabaseUrl + "/rest/v1/messages?id=eq." + messageId;

                URL url = new URL(updateUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setRequestProperty("apikey", supabaseAnonKey);
                connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // 构建更新数据
                JSONObject updateData = new JSONObject();
                updateData.put("is_read", true);
                updateData.put("read_at", getCurrentLocalTimestamp());

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(updateData.toString());
                writer.close();

                int responseCode = connection.getResponseCode();
                boolean success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT;

                if (success) {
                    Log.d(TAG, "消息已标记为已读: " + messageId);
                } else {
                    Log.e(TAG, "标记消息为已读失败，响应码: " + responseCode);
                }

                return success;
            } catch (Exception e) {
                Log.e(TAG, "标记消息为已读异常", e);
                return false;
            }
        }, executorService);
    }

    /**
     * 更新当前用户ID到SharedPreferences和Supabase用户ID
     */
    @JavascriptInterface
    public boolean updateCurrentUserId(String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                Log.w(TAG, "用户ID为空，跳过更新");
                return false;
            }

            // 保存到SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("current_user_id", userId.trim()).apply();
            prefs.edit().putString("supabase_user_id", userId.trim()).apply(); // 同时更新Supabase用户ID

            Log.d(TAG, "用户ID已更新: " + userId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "更新用户ID失败", e);
            return false;
        }
    }

    /**
     * 获取当前用户ID
     */
    @JavascriptInterface
    public String getCurrentUserId() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            return prefs.getString("current_user_id", "");
        } catch (Exception e) {
            Log.e(TAG, "获取当前用户ID失败", e);
            return "";
        }
    }

    /**
     * 获取晨报晚报时间设置
     */
    @JavascriptInterface
    public String getReportTimeSettings() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String morningTime = prefs.getString("morning_notify_time", "09:00");
            String eveningTime = prefs.getString("evening_notify_time", "18:00");

            JSONObject result = new JSONObject();
            result.put("morningTime", morningTime);
            result.put("eveningTime", eveningTime);

            Log.d(TAG, "获取报告时间设置: 晨报=" + morningTime + ", 晚报=" + eveningTime);
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "获取报告时间设置失败", e);
            // 返回默认时间
            try {
                JSONObject defaultResult = new JSONObject();
                defaultResult.put("morningTime", "09:00");
                defaultResult.put("eveningTime", "18:00");
                return defaultResult.toString();
            } catch (JSONException je) {
                return "{\"morningTime\":\"09:00\",\"eveningTime\":\"18:00\"}";
            }
        }
    }

    /**
     * 保存晨报晚报时间设置
     */
    @JavascriptInterface
    public boolean saveReportTimeSettings(String morningTime, String eveningTime) {
        try {
            if (morningTime == null || eveningTime == null) {
                Log.w(TAG, "时间设置参数为空");
                return false;
            }

            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString("morning_notify_time", morningTime.trim());
            editor.putString("evening_notify_time", eveningTime.trim());

            boolean success = editor.commit();
            if (success) {
                Log.d(TAG, "报告时间设置已保存: 晨报=" + morningTime + ", 晚报=" + eveningTime);
            } else {
                Log.e(TAG, "保存报告时间设置失败");
            }

            return success;
        } catch (Exception e) {
            Log.e(TAG, "保存报告时间设置异常", e);
            return false;
        }
    }

    /**
     * 同步数据库配置到Android SharedPreferences
     */
    @JavascriptInterface
    public boolean updateDatabaseConfig(String supabaseUrl, String supabaseAnonKey, String supabaseUserId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            if (supabaseUrl != null && !supabaseUrl.trim().isEmpty()) {
                editor.putString("supabase_url", supabaseUrl.trim());
                Log.d(TAG, "Supabase URL已更新: " + supabaseUrl.trim());
            }

            if (supabaseAnonKey != null && !supabaseAnonKey.trim().isEmpty()) {
                editor.putString("supabase_anon_key", supabaseAnonKey.trim());
                Log.d(TAG, "Supabase Anon Key已更新");
            }

            if (supabaseUserId != null && !supabaseUserId.trim().isEmpty()) {
                editor.putString("supabase_user_id", supabaseUserId.trim());
                Log.d(TAG, "Supabase User ID已更新: " + supabaseUserId.trim());
            }

            boolean success = editor.commit();
            if (success) {
                Log.d(TAG, "数据库配置同步成功");
            } else {
                Log.e(TAG, "数据库配置同步失败");
            }

            return success;
        } catch (Exception e) {
            Log.e(TAG, "同步数据库配置异常", e);
            return false;
        }
    }

    /**
     * 新消息通知回调（当前端发送消息后调用）
     */
    @JavascriptInterface
    public void onNewMessageNotification() {
        Log.d(TAG, "收到新消息通知");
        // 这里可以触发立即检查消息，而不是等待下一个定时周期
        // 可以通过广播或其他方式通知MessageListenerService
        try {
            android.content.Intent intent = new android.content.Intent("com.example.myapplication.NEW_MESSAGE");
            context.sendBroadcast(intent);
            Log.d(TAG, "已发送新消息广播");
        } catch (Exception e) {
            Log.e(TAG, "发送新消息广播失败", e);
        }
    }

    /**
     * 手动触发任务提醒检查（用于测试）
     */
    @JavascriptInterface
    public void triggerTaskReminders() {
        Log.d(TAG, "收到手动触发任务提醒请求");
        try {
            android.content.Intent intent = new android.content.Intent("com.example.myapplication.TRIGGER_REMINDERS");
            context.sendBroadcast(intent);
            Log.d(TAG, "已发送手动触发任务提醒广播");
        } catch (Exception e) {
            Log.e(TAG, "发送手动触发任务提醒广播失败", e);
        }
    }

    /**
     * 手动触发晚报发送
     */
    @JavascriptInterface
    public void triggerEveningReport() {
        Log.d(TAG, "收到手动触发晚报请求");
        try {
            android.content.Intent intent = new android.content.Intent("com.example.myapplication.TRIGGER_EVENING_REPORT");
            context.sendBroadcast(intent);
            Log.d(TAG, "已发送手动触发晚报广播");
        } catch (Exception e) {
            Log.e(TAG, "发送手动触发晚报广播失败", e);
        }
    }

    /**
     * 手动触发晨报发送
     */
    @JavascriptInterface
    public void triggerMorningReport() {
        Log.d(TAG, "收到手动触发晨报请求");
        try {
            android.content.Intent intent = new android.content.Intent("com.example.myapplication.TRIGGER_MORNING_REPORT");
            context.sendBroadcast(intent);
            Log.d(TAG, "已发送手动触发晨报广播");
        } catch (Exception e) {
            Log.e(TAG, "发送手动触发晨报广播失败", e);
        }
    }

    /**
     * 手动触发所有报告检查（晨报+晚报+任务提醒）
     */
    @JavascriptInterface
    public void triggerAllReports() {
        Log.d(TAG, "收到手动触发所有报告请求");
        try {
            android.content.Intent intent = new android.content.Intent("com.example.myapplication.TRIGGER_ALL_REPORTS");
            context.sendBroadcast(intent);
            Log.d(TAG, "已发送手动触发所有报告广播");
        } catch (Exception e) {
            Log.e(TAG, "发送手动触发所有报告广播失败", e);
        }
    }

    /**
     * 异步清理已读消息（删除指定天数前的已读消息）
     */
    @JavascriptInterface
    public CompletableFuture<Boolean> cleanupReadMessagesAsync(int daysOld) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                String supabaseUrl = prefs.getString("supabase_url", "");
                String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
                String supabaseUserId = prefs.getString("supabase_user_id", "");

                if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                    Log.w(TAG, "Supabase配置不完整，跳过清理");
                    return false;
                }

                // 计算清理日期（当前时间减去指定天数）
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysOld);
                String cutoffDate = formatLocalTimestamp(calendar.getTime());

                // 构建删除URL：删除已读且创建时间早于cutoffDate的消息
                String deleteUrl = supabaseUrl + "/rest/v1/messages" +
                                "?user_id=eq." + supabaseUserId +
                                "&is_read=eq.true" +
                                "&created_at.lt=" + cutoffDate;

                URL url = new URL(deleteUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("apikey", supabaseAnonKey);
                connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
                connection.setRequestProperty("Content-Type", "application/json");

                int responseCode = connection.getResponseCode();
                boolean success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT;

                if (success) {
                    Log.d(TAG, "已读消息清理成功，清理了 " + daysOld + " 天前的消息");
                } else {
                    Log.e(TAG, "清理已读消息失败，响应码: " + responseCode);
                }

                return success;
            } catch (Exception e) {
                Log.e(TAG, "清理已读消息异常", e);
                return false;
            }
        }, executorService);
    }

    /**
     * 同步清理已读消息（阻塞版本）
     */
    public boolean cleanupReadMessages(int daysOld) {
        try {
            return cleanupReadMessagesAsync(daysOld).get();
        } catch (Exception e) {
            Log.e(TAG, "同步清理已读消息失败", e);
            return false;
        }
    }

    /**
     * 格式化当前时间为本地时间ISO字符串（不带时区标识）
     */
    private String getCurrentLocalTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    /**
     * 格式化指定时间为本地时间ISO字符串（不带时区标识）
     */
    private String formatLocalTimestamp(java.util.Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * 获取今日任务（使用created_at字段）
     */
    @JavascriptInterface
    public String getTodayTasks(String userId) {
        return getTodayTasksByField(userId, "deadline");
    }

    /**
     * 获取今日任务（按指定日期字段）
     */
    @JavascriptInterface
    public String getTodayTasksByField(String userId, String dateField) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整");
                return "[]";
            }

            // 获取今日日期（本地时间格式：YYYY-MM-DD）
            java.text.SimpleDateFormat localDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String today = localDateFormat.format(new java.util.Date());
            String tomorrow = getNextDay(today);

            // 根据不同的日期字段构建查询条件
            String dateCondition;
            if ("created_at".equals(dateField) || "updated_at".equals(dateField) || "completed_at".equals(dateField)) {
                // 对于时间戳字段，使用完整的日期时间范围
                dateCondition = "&" + dateField + ".gte=" + today + "T00:00:00" +
                               "&" + dateField + ".lt=" + tomorrow + "T00:00:00";
            } else {
                // 对于日期字段，只使用日期部分
                dateCondition = "&" + dateField + ".gte=" + today +
                               "&" + dateField + ".lt=" + tomorrow;
            }

            // 构建查询URL：获取今日任务（assignee字段可能包含多个用户ID，用逗号分割）
            String queryUrl = supabaseUrl + "/rest/v1/tasks" +
                            "?user_id=eq." + supabaseUserId +
                            "&or=(assignee.ilike.%25" + java.net.URLEncoder.encode(userId, "UTF-8") + "%25,assignee.eq." + java.net.URLEncoder.encode(userId, "UTF-8") + ")" +
                            dateCondition +
                            "&order=" + dateField + ".asc";

            Log.d(TAG, "获取今日任务查询URL: " + queryUrl);
            return executeGetRequest(queryUrl, supabaseAnonKey);
        } catch (Exception e) {
            Log.e(TAG, "获取今日任务失败", e);
            return "[]";
        }
    }

    /**
     * 获取今日任务（使用due_date字段，适用于任务有明确到期日期的情况）
     */
    @JavascriptInterface
    public String getTodayTasksByDueDate(String userId) {
        return getTodayTasksByField(userId, "due_date");
    }

    /**
     * 获取今日任务（使用task_date字段，适用于任务有指定执行日期的情况）
     */
    @JavascriptInterface
    public String getTodayTasksByTaskDate(String userId) {
        return getTodayTasksByField(userId, "task_date");
    }

    /**
     * 获取今日已完成任务（使用completed_at字段）
     */
    @JavascriptInterface
    public String getTodayCompletedTasks(String userId) {
        return getTodayCompletedTasksByField(userId, "completed_at");
    }

    /**
     * 获取今日已完成任务（按指定日期字段）
     */
    @JavascriptInterface
    public String getTodayCompletedTasksByField(String userId, String dateField) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整");
                return "[]";
            }

            // 获取今日日期（本地时间格式：YYYY-MM-DD）
            java.text.SimpleDateFormat localDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String today = localDateFormat.format(new java.util.Date());
            String tomorrow = getNextDay(today);

            // 根据不同的日期字段构建查询条件
            String dateCondition;
            if ("created_at".equals(dateField) || "updated_at".equals(dateField) || "completed_at".equals(dateField)) {
                dateCondition = "&" + dateField + ".gte=" + today + "T00:00:00" +
                               "&" + dateField + ".lt=" + tomorrow + "T00:00:00";
            } else {
                dateCondition = "&" + dateField + ".gte=" + today +
                               "&" + dateField + ".lt=" + tomorrow;
            }

            // 构建查询URL：获取今日完成任务
            String queryUrl = supabaseUrl + "/rest/v1/tasks" +
                            "?user_id=eq." + supabaseUserId +
                            "&or=(assignee.ilike.%25" + java.net.URLEncoder.encode(userId, "UTF-8") + "%25,assignee.eq." + java.net.URLEncoder.encode(userId, "UTF-8") + ")" +
                            dateCondition +
                            "&completed=eq.true" +
                            "&order=" + dateField + ".desc";

            Log.d(TAG, "获取今日完成任务查询URL: " + queryUrl);
            return executeGetRequest(queryUrl, supabaseAnonKey);
        } catch (Exception e) {
            Log.e(TAG, "获取今日完成任务失败", e);
            return "[]";
        }
    }

    /**
     * 获取今日待完成任务（使用deadline字段）
     */
    @JavascriptInterface
    public String getTodayPendingTasks(String userId) {
        return getTodayPendingTasksByField(userId, "deadline");
    }

    /**
     * 获取今日待完成任务（按指定日期字段）
     */
    @JavascriptInterface
    public String getTodayPendingTasksByField(String userId, String dateField) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整");
                return "[]";
            }

            // 获取今日日期（本地时间格式：YYYY-MM-DD）
            java.text.SimpleDateFormat localDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String today = localDateFormat.format(new java.util.Date());
            String tomorrow = getNextDay(today);

            // 根据不同的日期字段构建查询条件
            String dateCondition;
            if ("created_at".equals(dateField) || "updated_at".equals(dateField) || "completed_at".equals(dateField)) {
                dateCondition = "&" + dateField + ".gte=" + today + "T00:00:00" +
                               "&" + dateField + ".lt=" + tomorrow + "T00:00:00";
            } else {
                dateCondition = "&" + dateField + ".gte=" + today +
                               "&" + dateField + ".lt=" + tomorrow;
            }

            // 构建查询URL：获取今日待完成任务
            String queryUrl = supabaseUrl + "/rest/v1/tasks" +
                            "?user_id=eq." + supabaseUserId +
                            "&or=(assignee.ilike.%25" + java.net.URLEncoder.encode(userId, "UTF-8") + "%25,assignee.eq." + java.net.URLEncoder.encode(userId, "UTF-8") + ")" +
                            dateCondition +
                            "&completed=eq.false" +
                            "&order=" + dateField + ".asc";

            Log.d(TAG, "获取今日待完成任务查询URL: " + queryUrl);
            return executeGetRequest(queryUrl, supabaseAnonKey);
        } catch (Exception e) {
            Log.e(TAG, "获取今日待完成任务失败", e);
            return "[]";
        }
    }

    /**
     * 获取即将到期的任务（未来24小时内即将到期）
     */
    @JavascriptInterface
    public String getUpcomingDeadlineTasks(String userId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整");
                return "[]";
            }

            // 获取当前本地时间和未来24小时的时间
            java.util.Date localNow = new java.util.Date();
            java.util.Date localNext24Hours = new java.util.Date(localNow.getTime() + 24 * 60 * 60 * 1000);

            // 使用本地时间格式化（不带时区标识）
            String nowStr = formatLocalTimestamp(localNow);
            String next24HoursStr = formatLocalTimestamp(localNext24Hours);

            // 添加本地时间调试日志
            Log.d(TAG, "本地时间: " + nowStr);
            Log.d(TAG, "查询即将到期任务时间范围: " + nowStr + " 到 " + next24HoursStr);

            // 获取Supabase用户ID用于数据库查询
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            // 构建查询URL：获取即将到期任务（assignee字段可能包含多个用户ID，用逗号分割）
            // 使用ilike操作符匹配包含指定用户ID的逗号分割字符串
            String queryUrl = supabaseUrl + "/rest/v1/tasks" +
                            "?user_id=eq." + supabaseUserId +
                            "&or=(assignee.ilike.%25" + java.net.URLEncoder.encode(userId, "UTF-8") + "%25,assignee.eq." + java.net.URLEncoder.encode(userId, "UTF-8") + ")" +
                            "&completed=eq.false" +
                            "&deadline.gte=" + nowStr +
                            "&deadline=lte." + next24HoursStr +
                            "&order=deadline.asc";

            return executeGetRequest(queryUrl, supabaseAnonKey);
        } catch (Exception e) {
            Log.e(TAG, "获取即将到期任务失败", e);
            return "[]";
        }
    }

    /**
     * 获取逾期任务
     */
    @JavascriptInterface
    public String getOverdueTasks(String userId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整");
                return "[]";
            }

            // 获取当前本地时间（不带时区标识）
            java.util.Date localNow = new java.util.Date();
            String nowStr = formatLocalTimestamp(localNow);

            // 添加本地时间调试日志
            Log.d(TAG, "本地时间: " + nowStr);
            Log.d(TAG, "查询逾期任务，当前时间: " + nowStr);

            // 获取Supabase用户ID用于数据库查询
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            // 构建查询URL：获取逾期任务（assignee字段可能包含多个用户ID，用逗号分割）
            // 使用ilike操作符匹配包含指定用户ID的逗号分割字符串
            String queryUrl = supabaseUrl + "/rest/v1/tasks" +
                            "?user_id=eq." + supabaseUserId +
                            "&or=(assignee.ilike.%25" + java.net.URLEncoder.encode(userId, "UTF-8") + "%25,assignee.eq." + java.net.URLEncoder.encode(userId, "UTF-8") + ")" +
                            "&completed=eq.false" +
                            "&deadline.lt=" + nowStr +
                            "&order=deadline.asc";

            return executeGetRequest(queryUrl, supabaseAnonKey);
        } catch (Exception e) {
            Log.e(TAG, "获取逾期任务失败", e);
            return "[]";
        }
    }

    /**
     * 将任务JSON解析为SimpleMessage列表（用于通知显示）
     */
    public java.util.List<SimpleMessage> parseTasksAsMessages(String tasksJson) {
        java.util.List<SimpleMessage> messages = new java.util.ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(tasksJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject taskObj = jsonArray.getJSONObject(i);
                SimpleMessage message = new SimpleMessage();
                message.id = taskObj.optLong("id");
                message.title = taskObj.optString("title", "未命名任务");
                message.content = taskObj.optString("description", "");
                message.senderId = taskObj.optString("user_id", "");
                message.createdAt = taskObj.optString("created_at");
                message.messageType = "task";
                messages.add(message);
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析任务JSON失败", e);
        }
        return messages;
    }

    /**
     * 将任务JSON解析为DetailedTask列表
     */
    public java.util.List<DetailedTask> parseTasksDetailed(String tasksJson) {
        java.util.List<DetailedTask> tasks = new java.util.ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(tasksJson);
            java.util.Date currentTime = new java.util.Date();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject taskObj = jsonArray.getJSONObject(i);
                DetailedTask task = new DetailedTask();

                // 基本信息
                task.id = taskObj.optLong("id");
                task.userId = taskObj.optString("user_id", "");
                task.title = taskObj.optString("title", "未命名任务");
                task.description = taskObj.optString("description", "");
                task.assignee = taskObj.optString("assignee", "");
                task.priority = taskObj.optString("priority", "medium");
                task.status = taskObj.optString("status", "pending");
                task.completed = taskObj.optBoolean("completed", false);
                task.date = taskObj.optString("date", "");
                task.deadline = taskObj.optString("deadline", "");
                task.createdAt = taskObj.optString("created_at", "");
                task.updatedAt = taskObj.optString("updated_at", "");
                task.completedAt = taskObj.optString("completed_at", "");
                task.category = taskObj.optString("category", "");
                task.tags = taskObj.optString("tags", "");
                task.estimatedHours = taskObj.optInt("estimated_hours", 0);
                task.actualHours = taskObj.optInt("actual_hours", 0);
                task.notes = taskObj.optString("notes", "");
                task.notesImages = taskObj.optString("notes_images", "[]");
                task.attachments = taskObj.optString("attachments", "");

                // 计算格式化字段
                task.formattedPriority = formatPriority(task.priority);
                task.formattedStatus = formatStatus(task.status, task.completed);
                task.formattedDate = formatDateTime(task.date);
                task.formattedDeadline = formatDateTime(task.deadline);
                task.timeRemaining = calculateTimeRemaining(task.deadline, currentTime);
                task.isOverdue = calculateIsOverdue(task.deadline, currentTime, task.completed);
                task.isDueToday = calculateIsDueToday(task.deadline);
                task.isDueSoon = calculateIsDueSoon(task.deadline, currentTime);
                task.completionPercentage = task.completed ? 100 : 0;

                tasks.add(task);
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析详细任务JSON失败", e);
        }
        return tasks;
    }

    /**
     * 格式化任务优先级
     */
    private String formatPriority(String priority) {
        if (priority == null || priority.isEmpty()) return "普通";
        switch (priority.toLowerCase()) {
            case "high":
            case "urgent":
                return "🔴 紧急";
            case "medium":
                return "🟡 普通";
            case "low":
                return "🟢 低";
            default:
                return "🟡 " + priority;
        }
    }

    /**
     * 格式化任务状态
     */
    private String formatStatus(String status, boolean completed) {
        if (completed) {
            return "✅ 已完成";
        }
        if (status == null || status.isEmpty()) return "⏳ 待处理";
        switch (status.toLowerCase()) {
            case "pending":
                return "⏳ 待处理";
            case "in_progress":
            case "working":
                return "🔄 进行中";
            case "review":
                return "👀 待审核";
            case "blocked":
                return "🚫 已阻塞";
            case "cancelled":
                return "❌ 已取消";
            default:
                return "📋 " + status;
        }
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return "";
        }
        try {
            java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
            java.util.Date date = inputFormat.parse(dateTimeStr);
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateTimeStr;
        }
    }

    /**
     * 计算剩余时间
     */
    private String calculateTimeRemaining(String deadlineStr, java.util.Date currentTime) {
        if (deadlineStr == null || deadlineStr.isEmpty()) {
            return "";
        }
        try {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date deadline = dateFormat.parse(deadlineStr);
            long diffMillis = deadline.getTime() - currentTime.getTime();

            if (diffMillis < 0) {
                long overdueDays = Math.abs(diffMillis) / (24 * 60 * 60 * 1000);
                return "逾期 " + overdueDays + " 天";
            } else {
                long days = diffMillis / (24 * 60 * 60 * 1000);
                long hours = (diffMillis % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);

                if (days > 0) {
                    return "剩余 " + days + " 天 " + hours + " 小时";
                } else if (hours > 0) {
                    return "剩余 " + hours + " 小时";
                } else {
                    long minutes = (diffMillis % (60 * 60 * 1000)) / (60 * 1000);
                    return "剩余 " + minutes + " 分钟";
                }
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 计算是否逾期
     */
    private boolean calculateIsOverdue(String deadlineStr, java.util.Date currentTime, boolean completed) {
        if (completed || deadlineStr == null || deadlineStr.isEmpty()) {
            return false;
        }
        try {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date deadline = dateFormat.parse(deadlineStr);
            return currentTime.after(deadline);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算是否今天到期
     */
    private boolean calculateIsDueToday(String deadlineStr) {
        if (deadlineStr == null || deadlineStr.isEmpty()) {
            return false;
        }
        try {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String todayStr = dateFormat.format(new java.util.Date());
            return deadlineStr.startsWith(todayStr);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算是否即将到期（24小时内）
     */
    private boolean calculateIsDueSoon(String deadlineStr, java.util.Date currentTime) {
        if (deadlineStr == null || deadlineStr.isEmpty()) {
            return false;
        }
        try {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date deadline = dateFormat.parse(deadlineStr);
            long diffMillis = deadline.getTime() - currentTime.getTime();
            return diffMillis > 0 && diffMillis <= 24 * 60 * 60 * 1000; // 24小时内
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将DetailedTask列表转换为格式化的JSON字符串
     */
    @JavascriptInterface
    public String formatTasksAsDetailedJson(String tasksJson) {
        try {
            java.util.List<DetailedTask> tasks = parseTasksDetailed(tasksJson);
            JSONArray resultArray = new JSONArray();

            for (DetailedTask task : tasks) {
                JSONObject taskObj = new JSONObject();
                taskObj.put("id", task.id);
                taskObj.put("title", task.title);
                taskObj.put("description", task.description);
                taskObj.put("assignee", task.assignee);
                taskObj.put("priority", task.priority);
                taskObj.put("formattedPriority", task.formattedPriority);
                taskObj.put("status", task.status);
                taskObj.put("formattedStatus", task.formattedStatus);
                taskObj.put("completed", task.completed);
                taskObj.put("date", task.date);
                taskObj.put("formattedDate", task.formattedDate);
                taskObj.put("deadline", task.deadline);
                taskObj.put("formattedDeadline", task.formattedDeadline);
                taskObj.put("timeRemaining", task.timeRemaining);
                taskObj.put("isOverdue", task.isOverdue);
                taskObj.put("isDueToday", task.isDueToday);
                taskObj.put("isDueSoon", task.isDueSoon);
                taskObj.put("completionPercentage", task.completionPercentage);
                taskObj.put("category", task.category);
                taskObj.put("tags", task.tags);
                taskObj.put("estimatedHours", task.estimatedHours);
                taskObj.put("actualHours", task.actualHours);
                taskObj.put("notes", task.notes);
                taskObj.put("notesImages", task.notesImages);
                taskObj.put("createdAt", task.createdAt);
                taskObj.put("updatedAt", task.updatedAt);
                taskObj.put("completedAt", task.completedAt);

                resultArray.put(taskObj);
            }

            return resultArray.toString();
        } catch (Exception e) {
            Log.e(TAG, "格式化任务详情JSON失败", e);
            return tasksJson; // 返回原始数据
        }
    }

    /**
     * 获取今日任务（详细格式化版本）
     */
    @JavascriptInterface
    public String getTodayTasksDetailed(String userId) {
        String rawTasks = getTodayTasks(userId);
        return formatTasksAsDetailedJson(rawTasks);
    }

    /**
     * 获取今日已完成任务（详细格式化版本）
     */
    @JavascriptInterface
    public String getTodayCompletedTasksDetailed(String userId) {
        String rawTasks = getTodayCompletedTasks(userId);
        return formatTasksAsDetailedJson(rawTasks);
    }

    /**
     * 获取今日待完成任务（详细格式化版本）
     */
    @JavascriptInterface
    public String getTodayPendingTasksDetailed(String userId) {
        String rawTasks = getTodayPendingTasks(userId);
        return formatTasksAsDetailedJson(rawTasks);
    }

    /**
     * 获取即将到期任务（详细格式化版本）
     */
    @JavascriptInterface
    public String getUpcomingDeadlineTasksDetailed(String userId) {
        String rawTasks = getUpcomingDeadlineTasks(userId);
        return formatTasksAsDetailedJson(rawTasks);
    }

    /**
     * 获取逾期任务（详细格式化版本）
     */
    @JavascriptInterface
    public String getOverdueTasksDetailed(String userId) {
        String rawTasks = getOverdueTasks(userId);
        return formatTasksAsDetailedJson(rawTasks);
    }

    /**
     * 获取任务统计信息
     */
    @JavascriptInterface
    public String getTasksStatistics(String userId) {
        try {
            String todayTasks = getTodayTasks(userId);
            String todayCompleted = getTodayCompletedTasks(userId);
            String todayPending = getTodayPendingTasks(userId);
            String upcomingTasks = getUpcomingDeadlineTasks(userId);
            String overdueTasks = getOverdueTasks(userId);

            java.util.List<DetailedTask> todayTasksList = parseTasksDetailed(todayTasks);
            java.util.List<DetailedTask> todayCompletedList = parseTasksDetailed(todayCompleted);
            java.util.List<DetailedTask> todayPendingList = parseTasksDetailed(todayPending);
            java.util.List<DetailedTask> upcomingList = parseTasksDetailed(upcomingTasks);
            java.util.List<DetailedTask> overdueList = parseTasksDetailed(overdueTasks);

            JSONObject statistics = new JSONObject();
            statistics.put("todayTotal", todayTasksList.size());
            statistics.put("todayCompleted", todayCompletedList.size());
            statistics.put("todayPending", todayPendingList.size());
            statistics.put("upcomingDeadlines", upcomingList.size());
            statistics.put("overdue", overdueList.size());

            // 计算完成率
            double completionRate = todayTasksList.size() > 0 ?
                (double) todayCompletedList.size() / todayTasksList.size() * 100 : 0;
            statistics.put("completionRate", Math.round(completionRate * 100.0) / 100.0);

            // 优先级统计
            int highPriority = 0, mediumPriority = 0, lowPriority = 0;
            for (DetailedTask task : todayPendingList) {
                String priority = task.priority != null ? task.priority.toLowerCase() : "medium";
                switch (priority) {
                    case "high":
                    case "urgent":
                        highPriority++;
                        break;
                    case "low":
                        lowPriority++;
                        break;
                    default:
                        mediumPriority++;
                        break;
                }
            }
            JSONObject priorityStats = new JSONObject();
            priorityStats.put("high", highPriority);
            priorityStats.put("medium", mediumPriority);
            priorityStats.put("low", lowPriority);
            statistics.put("priorityDistribution", priorityStats);

            // 时间统计
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            statistics.put("lastUpdated", timeFormat.format(new java.util.Date()));

            return statistics.toString();
        } catch (Exception e) {
            Log.e(TAG, "获取任务统计信息失败", e);
            return "{}";
        }
    }

    /**
     * 执行GET请求的通用方法
     */
    private String executeGetRequest(String queryUrl, String supabaseAnonKey) {
        try {
            Log.d(TAG, "执行GET请求: " + queryUrl);

            URL url = new URL(queryUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", supabaseAnonKey);
            connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "响应码: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String result = response.toString();
                Log.d(TAG, "GET请求成功，返回数据长度: " + result.length());
                Log.d(TAG, "返回数据内容: " + (result.length() > 200 ? result.substring(0, 200) + "..." : result));
                return result;
            } else {
                // 读取错误响应
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorReader.close();

                Log.e(TAG, "GET请求失败，响应码: " + responseCode + ", URL: " + queryUrl);
                Log.e(TAG, "错误响应: " + errorResponse.toString());
                return "[]";
            }
        } catch (Exception e) {
            Log.e(TAG, "执行GET请求异常: " + queryUrl, e);
            return "[]";
        }
    }

    /**
     * 获取指定日期的下一天
     */
    private String getNextDay(String dateStr) {
        try {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date date = dateFormat.parse(dateStr);
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
            return dateFormat.format(calendar.getTime());
        } catch (Exception e) {
            Log.e(TAG, "计算下一天失败", e);
            return dateStr;
        }
    }

    /**
     * 获取每日待办配置
     */
    @JavascriptInterface
    public String getDailyTodoSettings() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

            JSONObject settings = new JSONObject();
            settings.put("template", prefs.getString("daily_todo_template", "上班打卡|high|work|09:50\n下班打卡|high|work|19:00"));
            settings.put("enabled", prefs.getBoolean("daily_todo_enabled", true));
            settings.put("skipHolidays", prefs.getBoolean("daily_todo_skip_holidays", true));
            settings.put("lastAddedDate", prefs.getString("daily_todo_last_added_date", ""));

            Log.d(TAG, "获取每日待办配置: " + settings.toString());
            return settings.toString();
        } catch (Exception e) {
            Log.e(TAG, "获取每日待办配置失败", e);
            return "{}";
        }
    }

    /**
     * 保存每日待办配置
     */
    @JavascriptInterface
    public boolean saveDailyTodoSettings(String template, boolean enabled, boolean skipHolidays) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            if (template != null) {
                editor.putString("daily_todo_template", template);
            }
            editor.putBoolean("daily_todo_enabled", enabled);
            editor.putBoolean("daily_todo_skip_holidays", skipHolidays);

            boolean success = editor.commit();
            if (success) {
                Log.d(TAG, "每日待办配置已保存");
            } else {
                Log.e(TAG, "保存每日待办配置失败");
            }

            return success;
        } catch (Exception e) {
            Log.e(TAG, "保存每日待办配置异常", e);
            return false;
        }
    }

    /**
     * 更新最后添加日期
     */
    public boolean updateLastAddedDate(String date) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("daily_todo_last_added_date", date);
            return editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "更新最后添加日期失败", e);
            return false;
        }
    }

    /**
     * 手动触发每日待办任务生成（用户保存配置后调用）
     * 直接在后台线程执行，不使用广播机制
     * @param showToast 是否显示Toast提示
     */
    @JavascriptInterface
    public void triggerDailyTodoGeneration() {
        triggerDailyTodoGenerationInternal(true);
    }

    /**
     * 触发每日待办任务生成（内部方法）
     * @param showToast 是否显示Toast提示
     */
    public void triggerDailyTodoGenerationInternal(boolean showToast) {
        Log.d(TAG, "收到手动触发每日待办任务生成请求，showToast=" + showToast);

        // 显示Toast提示（仅当showToast为true时）
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        if (showToast) {
            mainHandler.post(() -> {
                android.widget.Toast.makeText(context,
                    "正在生成每日待办任务...",
                    android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        // 在后台线程直接执行任务生成
        executorService.execute(() -> {
            try {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());

                Log.d(TAG, "开始生成每日待办任务，日期: " + today);

                // 读取配置
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                boolean enabled = prefs.getBoolean("daily_todo_enabled", true);
                boolean skipHolidays = prefs.getBoolean("daily_todo_skip_holidays", true);
                String template = prefs.getString("daily_todo_template", "");
                String lastAddedDate = prefs.getString("daily_todo_last_added_date", "");
                String supabaseUrl = prefs.getString("supabase_url", "");
                String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
                String supabaseUserId = prefs.getString("supabase_user_id", "");
                String currentUserId = prefs.getString("current_user_id", supabaseUserId);

                // 检查配置
                if (!enabled) {
                    Log.d(TAG, "每日待办未启用，跳过生成");
                    if (showToast) showToast(mainHandler, "每日待办未启用");
                    return;
                }

                if (template == null || template.trim().isEmpty()) {
                    Log.d(TAG, "模板为空，跳过生成");
                    if (showToast) showToast(mainHandler, "每日待办模板为空");
                    return;
                }

                if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                    Log.w(TAG, "Supabase配置不完整，无法生成每日待办任务");
                    if (showToast) showToast(mainHandler, "Supabase配置不完整");
                    return;
                }

                // 检查是否今天已经生成过
//                if (today.equals(lastAddedDate)) {
//                    Log.d(TAG, "今天已经生成过每日待办任务，跳过生成。上次生成日期: " + lastAddedDate);
//                    return;
//                }

                // 检查节假日
                java.util.Date currentDate = new java.util.Date();
                boolean isHoliday = isHolidayCheck(currentDate);
                Log.d(TAG, "节假日检查 - skipHolidays: " + skipHolidays + ", isHoliday: " + isHoliday + ", 日期: " + today);

                if (skipHolidays && isHoliday) {
                    Log.d(TAG, "今天是节假日，跳过每日待办任务生成");
                    if (showToast) showToast(mainHandler, "今天是节假日，已跳过");
                    return;
                }

                Log.d(TAG, "节假日检查通过，继续生成任务");

                // 解析模板并生成任务
                String[] lines = template.split("\n");
                int createdCount = 0;

                Log.d(TAG, "模板内容: " + template);
                Log.d(TAG, "解析出 " + lines.length + " 行任务模板");

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        Log.d(TAG, "跳过空行");
                        continue;
                    }

                    Log.d(TAG, "解析任务行: " + line);

                    String[] parts = line.split("\\|");
                    if (parts.length < 1) {
                        Log.w(TAG, "任务行格式错误，跳过: " + line);
                        continue;
                    }

                    String title = parts[0].trim();
                    String priority = parts.length > 1 ? parts[1].trim() : "medium";
                    String category = parts.length > 2 ? parts[2].trim() : "other";
                    String timeStr = parts.length > 3 ? parts[3].trim() : "23:59";
                    String assignee = parts.length > 4 ? parts[4].trim() : currentUserId;

                    Log.d(TAG, "任务信息 - 标题: " + title + ", 优先级: " + priority + ", 分类: " + category + ", 时间: " + timeStr + ", 负责人: " + assignee);

                    // 检查今天是否已存在相同标题和负责人的任务
                    if (checkTaskExistsToday(supabaseUrl, supabaseAnonKey, supabaseUserId, title, assignee, today)) {
                        Log.d(TAG, "⚠ 今天已存在相同任务，跳过: " + title + " (负责人: " + assignee + ")");
                        continue;
                    }

                    // 构建截止时间
                    String[] timeParts = timeStr.split(":");
                    int hours = timeParts.length > 0 ? Integer.parseInt(timeParts[0].trim()) : 23;
                    int minutes = timeParts.length > 1 ? Integer.parseInt(timeParts[1].trim()) : 59;
                    String deadline = String.format("%sT%02d:%02d:00", today, hours, minutes);

                    Log.d(TAG, "截止时间: " + deadline);
                    Log.d(TAG, "准备调用createTaskDirect创建任务...");

                    // 创建任务
                    if (createTaskDirect(supabaseUrl, supabaseAnonKey, supabaseUserId, title, priority, category, deadline, assignee)) {
                        createdCount++;
                        Log.d(TAG, "✓ 创建任务成功: " + title + " (第" + createdCount + "个)");
                    } else {
                        Log.e(TAG, "✗ 创建任务失败: " + title);
                    }
                }

                // 更新最后添加日期
                prefs.edit().putString("daily_todo_last_added_date", today).apply();

                int finalCount = createdCount;
                if (showToast) {
                    mainHandler.post(() -> {
                        android.widget.Toast.makeText(context,
                            "成功创建 " + finalCount + " 个每日待办任务",
                            android.widget.Toast.LENGTH_SHORT).show();
                    });
                }

                Log.d(TAG, "共创建 " + createdCount + " 个每日待办任务");

                // 如果创建了任务，刷新前端任务列表
                if (finalCount > 0) {
                    // 延迟500ms后刷新，确保任务已经写入数据库
                    mainHandler.postDelayed(() -> {
                        refreshTaskList();
                    }, 10000);
                }

            } catch (Exception e) {
                Log.e(TAG, "生成每日待办任务失败", e);
                if (showToast) {
                    mainHandler.post(() -> {
                        android.widget.Toast.makeText(context,
                            "生成任务失败: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    /**
     * 显示Toast消息的辅助方法
     */
    private void showToast(android.os.Handler handler, String message) {
        handler.post(() -> {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 判断是否为节假日（包含周末和法定节假日，排除调休上班日）
     */
    private boolean isHolidayCheck(java.util.Date date) {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);

            int year = calendar.get(java.util.Calendar.YEAR);
            int month = calendar.get(java.util.Calendar.MONTH) + 1;
            int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
            int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);

            // 2024年调休上班日（这些日期虽然是周末，但需要上班）
            if (year == 2024) {
                if (month == 2 && day == 4) {
                    Log.d(TAG, "调休上班日: 2024-02-04 (春节调休)");
                    return false;
                }
                if (month == 2 && day == 18) {
                    Log.d(TAG, "调休上班日: 2024-02-18 (春节调休)");
                    return false;
                }
                if (month == 4 && day == 7) {
                    Log.d(TAG, "调休上班日: 2024-04-07 (清明调休)");
                    return false;
                }
                if (month == 4 && day == 28) {
                    Log.d(TAG, "调休上班日: 2024-04-28 (劳动节调休)");
                    return false;
                }
                if (month == 5 && day == 11) {
                    Log.d(TAG, "调休上班日: 2024-05-11 (劳动节调休)");
                    return false;
                }
                if (month == 9 && day == 14) {
                    Log.d(TAG, "调休上班日: 2024-09-14 (中秋调休)");
                    return false;
                }
                if (month == 9 && day == 29) {
                    Log.d(TAG, "调休上班日: 2024-09-29 (国庆调休)");
                    return false;
                }
                if (month == 10 && day == 12) {
                    Log.d(TAG, "调休上班日: 2024-10-12 (国庆调休)");
                    return false;
                }
            }

            // 2025年调休上班日（这些日期虽然是周末，但需要上班）
            if (year == 2025) {
                if (month == 1 && day == 26) {
                    Log.d(TAG, "调休上班日: 2025-01-26 (春节调休)");
                    return false;
                }
                if (month == 2 && day == 8) {
                    Log.d(TAG, "调休上班日: 2025-02-08 (春节调休)");
                    return false;
                }
                if (month == 4 && day == 27) {
                    Log.d(TAG, "调休上班日: 2025-04-27 (劳动节调休)");
                    return false;
                }
                if (month == 5 && day == 4) {
                    Log.d(TAG, "调休上班日: 2025-05-04 (劳动节调休)");
                    return false;
                }
                if (month == 9 && day == 28) {
                    Log.d(TAG, "调休上班日: 2025-09-28 (国庆调休)");
                    return false;
                }
                if (month == 10 && day == 11) {
                    Log.d(TAG, "调休上班日: 2025-10-11 (国庆节调休)");
                    return false;
                }
            }

            // 检查是否为周末（排除了上面的调休上班日）
            if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
                Log.d(TAG, "周末: " + year + "-" + month + "-" + day);
                return true;
            }

            // 检查是否为法定节假日
            if (year == 2024) {
                // 元旦：1月1日
                if (month == 1 && day == 1) {
                    Log.d(TAG, "法定节假日: 元旦");
                    return true;
                }
                // 春节：2月10日-17日
                if (month == 2 && day >= 10 && day <= 17) {
                    Log.d(TAG, "法定节假日: 春节");
                    return true;
                }
                // 清明节：4月4日-6日
                if (month == 4 && day >= 4 && day <= 6) {
                    Log.d(TAG, "法定节假日: 清明节");
                    return true;
                }
                // 劳动节：5月1日-5日
                if (month == 5 && day >= 1 && day <= 5) {
                    Log.d(TAG, "法定节假日: 劳动节");
                    return true;
                }
                // 端午节：6月10日
                if (month == 6 && day == 10) {
                    Log.d(TAG, "法定节假日: 端午节");
                    return true;
                }
                // 中秋节：9月15日-17日
                if (month == 9 && day >= 15 && day <= 17) {
                    Log.d(TAG, "法定节假日: 中秋节");
                    return true;
                }
                // 国庆节：10月1日-7日
                if (month == 10 && day >= 1 && day <= 7) {
                    Log.d(TAG, "法定节假日: 国庆节");
                    return true;
                }
            }

            if (year == 2025) {
                // 元旦：1月1日
                if (month == 1 && day == 1) {
                    Log.d(TAG, "法定节假日: 元旦");
                    return true;
                }
                // 春节：1月28日-2月4日
                if (month == 1 && day >= 28 && day <= 31) {
                    Log.d(TAG, "法定节假日: 春节");
                    return true;
                }
                if (month == 2 && day >= 1 && day <= 4) {
                    Log.d(TAG, "法定节假日: 春节");
                    return true;
                }
                // 清明节：4月4日-6日
                if (month == 4 && day >= 4 && day <= 6) {
                    Log.d(TAG, "法定节假日: 清明节");
                    return true;
                }
                // 劳动节：5月1日-5日
                if (month == 5 && day >= 1 && day <= 5) {
                    Log.d(TAG, "法定节假日: 劳动节");
                    return true;
                }
                // 端午节：5月31日-6月2日
                if (month == 5 && day == 31) {
                    Log.d(TAG, "法定节假日: 端午节");
                    return true;
                }
                if (month == 6 && day >= 1 && day <= 2) {
                    Log.d(TAG, "法定节假日: 端午节");
                    return true;
                }
                // 中秋节+国庆节：10月1日-7日
                if (month == 10 && day >= 1 && day <= 7) {
                    Log.d(TAG, "法定节假日: 国庆节+中秋节");
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "判断节假日失败", e);
            return false;
        }
    }

    /**
     * 测试方法：直接生成每日待办任务（不通过广播）
     */
    @JavascriptInterface
    public String triggerDailyTodoGenerationDirect() {
        Log.d(TAG, "收到直接生成每日待办任务请求");
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("daily_todo_enabled", true);
            boolean skipHolidays = prefs.getBoolean("daily_todo_skip_holidays", true);
            String template = prefs.getString("daily_todo_template", "");
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            // 构建调试信息
            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("配置状态:\n");
            debugInfo.append("- 是否启用: ").append(enabled).append("\n");
            debugInfo.append("- 跳过节假日: ").append(skipHolidays).append("\n");
            debugInfo.append("- 模板长度: ").append(template != null ? template.length() : 0).append("\n");
            debugInfo.append("- Supabase URL: ").append(supabaseUrl.isEmpty() ? "空" : "已设置").append("\n");
            debugInfo.append("- Supabase Key: ").append(supabaseAnonKey.isEmpty() ? "空" : "已设置").append("\n");
            debugInfo.append("- User ID: ").append(supabaseUserId.isEmpty() ? "空" : supabaseUserId).append("\n");

            Log.d(TAG, "调试信息: " + debugInfo.toString());

            if (!enabled) {
                return debugInfo.append("\n结果: 每日待办未启用").toString();
            }

            if (template == null || template.trim().isEmpty()) {
                return debugInfo.append("\n结果: 模板为空").toString();
            }

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                return debugInfo.append("\n结果: Supabase配置不完整").toString();
            }

            // 在后台线程执行任务生成
            executorService.execute(() -> {
                try {
                    String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(new java.util.Date());

                    Log.d(TAG, "开始生成每日待办任务，日期: " + today);

                    String[] lines = template.split("\n");
                    int createdCount = 0;

                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        String[] parts = line.split("\\|");
                        if (parts.length < 1) continue;

                        String title = parts[0].trim();
                        String priority = parts.length > 1 ? parts[1].trim() : "medium";
                        String category = parts.length > 2 ? parts[2].trim() : "other";
                        String timeStr = parts.length > 3 ? parts[3].trim() : "23:59";
                        String assignee = parts.length > 4 ? parts[4].trim() : supabaseUserId;

                        // 构建截止时间
                        String[] timeParts = timeStr.split(":");
                        int hours = timeParts.length > 0 ? Integer.parseInt(timeParts[0].trim()) : 23;
                        int minutes = timeParts.length > 1 ? Integer.parseInt(timeParts[1].trim()) : 59;
                        String deadline = String.format("%sT%02d:%02d:00", today, hours, minutes);

                        // 创建任务
                        if (createTaskDirect(supabaseUrl, supabaseAnonKey, supabaseUserId, title, priority, category, deadline, assignee)) {
                            createdCount++;
                            Log.d(TAG, "创建任务成功: " + title);
                        }
                    }

                    int finalCount = createdCount;
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        android.widget.Toast.makeText(context,
                            "成功创建 " + finalCount + " 个任务",
                            android.widget.Toast.LENGTH_SHORT).show();
                    });

                    Log.d(TAG, "共创建 " + createdCount + " 个每日待办任务");

                } catch (Exception e) {
                    Log.e(TAG, "生成每日待办任务异常", e);
                }
            });

            return debugInfo.append("\n结果: 任务生成已启动，请查看Toast提示").toString();

        } catch (Exception e) {
            Log.e(TAG, "直接生成每日待办任务失败", e);
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 检查今天是否已存在相同标题和负责人的任务
     */
    private boolean checkTaskExistsToday(String supabaseUrl, String supabaseAnonKey, String supabaseUserId,
                                         String title, String assignee, String today) {
        try {
            String tomorrow = getNextDay(today);

            // 构建查询URL：查询今天创建的、标题和负责人都相同的任务
            String queryUrl = supabaseUrl + "/rest/v1/tasks" +
                    "?user_id=eq." + supabaseUserId +
                    "&title=eq." + java.net.URLEncoder.encode(title, "UTF-8") +
                    "&assignee=eq." + java.net.URLEncoder.encode(assignee, "UTF-8") +
                    "&created_at=gte." + today + "T00:00:00" +
                    "&created_at=lt." + tomorrow + "T00:00:00" +
                    "&select=id";

            Log.d(TAG, "检查任务是否存在 - URL: " + queryUrl);

            java.net.URL url = new java.net.URL(queryUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", supabaseAnonKey);
            connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();

            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String result = response.toString();
                Log.d(TAG, "查询结果: " + result);

                // 解析JSON数组，如果有数据说明任务已存在
                org.json.JSONArray jsonArray = new org.json.JSONArray(result);
                boolean exists = jsonArray.length() > 0;

                if (exists) {
                    Log.d(TAG, "任务已存在: " + title + " (负责人: " + assignee + ")");
                } else {
                    Log.d(TAG, "任务不存在，可以创建: " + title + " (负责人: " + assignee + ")");
                }

                return exists;
            } else {
                Log.e(TAG, "查询任务失败，响应码: " + responseCode);
                return false; // 查询失败时，默认认为不存在，允许创建
            }
        } catch (Exception e) {
            Log.e(TAG, "检查任务是否存在时发生异常", e);
            return false; // 异常时，默认认为不存在，允许创建
        }
    }

    /**
     * 直接创建任务（不通过服务）
     */
    private boolean createTaskDirect(String supabaseUrl, String supabaseAnonKey, String supabaseUserId,
                                    String title, String priority, String category, String deadline, String assignee) {
        Log.d(TAG, "▶ 进入createTaskDirect方法");
        Log.d(TAG, "参数 - URL: " + supabaseUrl + ", 标题: " + title + ", 优先级: " + priority);

        try {
            String createUrl = supabaseUrl + "/rest/v1/tasks";
            Log.d(TAG, "完整请求URL: " + createUrl);

            java.net.URL url = new java.net.URL(createUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("apikey", supabaseAnonKey);
            connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "return=minimal");
            connection.setDoOutput(true);

            Log.d(TAG, "HTTP连接已配置，准备构建任务数据");

            // 生成随机ID
            String taskId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);

            org.json.JSONObject taskData = new org.json.JSONObject();
            taskData.put("id", taskId);
            taskData.put("user_id", supabaseUserId);
            taskData.put("title", title);
            taskData.put("priority", priority);
            taskData.put("category", category);
            taskData.put("deadline", deadline);
            taskData.put("assignee", assignee);
            taskData.put("completed", false);
            // 使用本地时间设置创建时间
            taskData.put("created_at", getCurrentLocalTimestamp());

            String jsonPayload = taskData.toString();
            Log.d(TAG, "任务数据JSON: " + jsonPayload);
            Log.d(TAG, "准备发送POST请求...");

            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(connection.getOutputStream());
            writer.write(jsonPayload);
            writer.flush();
            writer.close();

            Log.d(TAG, "POST请求已发送，等待响应...");

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "收到响应码: " + responseCode);

            boolean success = responseCode == java.net.HttpURLConnection.HTTP_OK ||
                            responseCode == java.net.HttpURLConnection.HTTP_CREATED ||
                            responseCode == java.net.HttpURLConnection.HTTP_NO_CONTENT;

            if (success) {
                Log.d(TAG, "✓ HTTP请求成功，任务已创建");
            } else {
                Log.e(TAG, "✗ HTTP请求失败，响应码: " + responseCode);

                // 读取错误响应
                try {
                    java.io.BufferedReader errorReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    Log.e(TAG, "错误响应内容: " + errorResponse.toString());
                } catch (Exception readError) {
                    Log.e(TAG, "无法读取错误响应", readError);
                }
            }

            return success;
        } catch (Exception e) {
            Log.e(TAG, "✗ 创建任务异常，异常类型: " + e.getClass().getName(), e);
            Log.e(TAG, "异常消息: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 刷新前端任务列表
     */
    private void refreshTaskList() {
        if (webView != null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // 调用前端的刷新函数
                    webView.evaluateJavascript(
                        "if (window.loadTasks) { window.loadTasks(); } " +
                        "else if (window.refreshTasks) { window.refreshTasks(); } " +
                        "else { console.log('刷新函数未找到'); }",
                        null
                    );
                    Log.d(TAG, "已触发前端任务列表刷新");
                } catch (Exception e) {
                    Log.e(TAG, "刷新前端任务列表失败", e);
                }
            });
        } else {
            Log.d(TAG, "WebView引用为空，无法刷新前端任务列表");
        }
    }

    /**
     * 释放资源
     */
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    /**
     * 简化的消息数据模型
     */
    public static class SimpleMessage {
        public long id;
        public String senderId;
        public String receiverId;
        public String messageType;
        public String title;
        public String content;
        public String taskTitle;
        public String completionNotes;
        public String createdAt;
        public boolean isRead;

        @Override
        public String toString() {
            return "SimpleMessage{" +
                    "id=" + id +
                    ", senderId='" + senderId + '\'' +
                    ", messageType='" + messageType + '\'' +
                    ", title='" + title + '\'' +
                    ", content='" + content + '\'' +
                    '}';
        }
    }

    /**
     * 更新任务备注图片
     */
    @JavascriptInterface
    public void updateTaskNotesImages(String taskId, String notesImagesJson) {
        executorService.execute(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                String supabaseUrl = prefs.getString("supabase_url", "");
                String supabaseAnonKey = prefs.getString("supabase_anon_key", "");

                if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
                    Log.w(TAG, "Supabase配置不完整");
                    return;
                }

                String updateUrl = supabaseUrl + "/rest/v1/tasks?id=eq." + taskId;
                URL url = new URL(updateUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setRequestProperty("apikey", supabaseAnonKey);
                connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // 构建更新数据
                JSONObject updateData = new JSONObject();
                updateData.put("notes_images", new JSONArray(notesImagesJson));

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(updateData.toString());
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    Log.d(TAG, "任务备注图片更新成功，任务ID: " + taskId);
                } else {
                    Log.e(TAG, "任务备注图片更新失败，响应码: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "更新任务备注图片异常", e);
            }
        });
    }

    /**
     * 调试方法：获取每日待办任务生成的完整诊断信息
     * 返回JSON格式的详细状态，可用于前端显示
     */
    @JavascriptInterface
    public String getDailyTodoDebugInfo() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

            // 获取配置信息
            boolean enabled = prefs.getBoolean("daily_todo_enabled", true);
            boolean skipHolidays = prefs.getBoolean("daily_todo_skip_holidays", true);
            String template = prefs.getString("daily_todo_template", "");
            String lastAddedDate = prefs.getString("daily_todo_last_added_date", "");
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");
            String currentUserId = prefs.getString("current_user_id", "");

            // 获取当前日期和节假日状态
            java.util.Date currentDate = new java.util.Date();
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String today = dateFormat.format(currentDate);
            boolean isHoliday = isHolidayCheck(currentDate);

            // 检查是否应该跳过
            boolean shouldSkip = skipHolidays && isHoliday;

            // 解析模板
            int templateLineCount = 0;
            if (template != null && !template.trim().isEmpty()) {
                String[] lines = template.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        templateLineCount++;
                    }
                }
            }

            // 构建诊断JSON
            JSONObject debugInfo = new JSONObject();

            // 基本信息
            debugInfo.put("currentDate", today);
            debugInfo.put("isHoliday", isHoliday);
            debugInfo.put("shouldGenerate", enabled && (!skipHolidays || !isHoliday) && !template.trim().isEmpty());

            // 配置状态
            JSONObject configStatus = new JSONObject();
            configStatus.put("enabled", enabled);
            configStatus.put("skipHolidays", skipHolidays);
            configStatus.put("templateLength", template != null ? template.length() : 0);
            configStatus.put("templateLineCount", templateLineCount);
            configStatus.put("lastAddedDate", lastAddedDate);
            debugInfo.put("configuration", configStatus);

            // Supabase状态
            JSONObject supabaseStatus = new JSONObject();
            supabaseStatus.put("urlConfigured", !supabaseUrl.isEmpty());
            supabaseStatus.put("keyConfigured", !supabaseAnonKey.isEmpty());
            supabaseStatus.put("userIdConfigured", !supabaseUserId.isEmpty());
            supabaseStatus.put("supabaseUrl", supabaseUrl.isEmpty() ? "未设置" : supabaseUrl);
            supabaseStatus.put("supabaseUserId", supabaseUserId.isEmpty() ? "未设置" : supabaseUserId);
            supabaseStatus.put("currentUserId", currentUserId.isEmpty() ? "未设置" : currentUserId);
            debugInfo.put("supabaseConfig", supabaseStatus);

            // 阻止原因分析
            JSONArray blockReasons = new JSONArray();
            if (!enabled) {
                blockReasons.put("❌ 每日待办未启用");
            }
            if (template == null || template.trim().isEmpty()) {
                blockReasons.put("❌ 模板为空");
            }
            if (supabaseUrl.isEmpty()) {
                blockReasons.put("❌ Supabase URL未设置");
            }
            if (supabaseAnonKey.isEmpty()) {
                blockReasons.put("❌ Supabase API Key未设置");
            }
            if (supabaseUserId.isEmpty()) {
                blockReasons.put("❌ Supabase User ID未设置");
            }
            if (skipHolidays && isHoliday) {
                blockReasons.put("⚠️ 今天是节假日且设置了跳过节假日");
            }
            if (today.equals(lastAddedDate)) {
                blockReasons.put("ℹ️ 今天已经生成过任务");
            }
            if (blockReasons.length() == 0) {
                blockReasons.put("✅ 无阻止原因，应该可以正常生成");
            }
            debugInfo.put("blockReasons", blockReasons);

            // 模板预览
            if (template != null && !template.isEmpty()) {
                String templatePreview = template.length() > 100 ?
                    template.substring(0, 100) + "..." : template;
                debugInfo.put("templatePreview", templatePreview);
            } else {
                debugInfo.put("templatePreview", "无");
            }

            // 日志输出
            String result = debugInfo.toString(2); // 格式化输出，缩进2个空格
            Log.d(TAG, "每日待办诊断信息:\n" + result);

            return result;

        } catch (Exception e) {
            Log.e(TAG, "获取调试信息失败", e);
            try {
                JSONObject errorInfo = new JSONObject();
                errorInfo.put("error", e.getMessage());
                errorInfo.put("errorType", e.getClass().getName());
                return errorInfo.toString();
            } catch (JSONException je) {
                return "{\"error\":\"获取调试信息失败: " + e.getMessage() + "\"}";
            }
        }
    }

    /**
     * 测试方法：创建一个测试任务来验证Supabase连接
     */
    @JavascriptInterface
    public void testCreateTask() {
        Log.d(TAG, "收到测试任务创建请求");

        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        executorService.execute(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                String supabaseUrl = prefs.getString("supabase_url", "");
                String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
                String supabaseUserId = prefs.getString("supabase_user_id", "");
                String currentUserId = prefs.getString("current_user_id", supabaseUserId);

                if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                    String error = "Supabase配置不完整";
                    Log.e(TAG, error);
                    showToast(mainHandler, "❌ " + error);
                    return;
                }

                // 创建测试任务
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
                String testTime = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());

                String title = "测试任务 " + testTime;
                String priority = "medium";
                String category = "test";
                String deadline = today + "T23:59:00";
                String assignee = currentUserId;

                Log.d(TAG, "准备创建测试任务: " + title);
                Log.d(TAG, "Supabase URL: " + supabaseUrl);
                Log.d(TAG, "User ID: " + supabaseUserId);
                Log.d(TAG, "Assignee: " + assignee);

                boolean success = createTaskDirect(supabaseUrl, supabaseAnonKey, supabaseUserId,
                    title, priority, category, deadline, assignee);

                if (success) {
                    Log.d(TAG, "✅ 测试任务创建成功");
                    showToast(mainHandler, "✅ 测试任务创建成功！");
                } else {
                    Log.e(TAG, "❌ 测试任务创建失败");
                    showToast(mainHandler, "❌ 测试任务创建失败，请检查日志");
                }

            } catch (Exception e) {
                Log.e(TAG, "测试任务创建异常", e);
                showToast(mainHandler, "❌ 异常: " + e.getMessage());
            }
        });
    }

    /**
     * 详细的任务数据模型
     */
    public static class DetailedTask {
        public long id;
        public String userId;
        public String title;
        public String description;
        public String assignee;
        public String priority;
        public String status;
        public boolean completed;
        public String date;
        public String deadline;
        public String createdAt;
        public String updatedAt;
        public String completedAt;
        public String category;
        public String tags;
        public int estimatedHours;
        public int actualHours;
        public String notes;
        public String notesImages; // JSON字符串，存储任务备注相关的图片数据
        public String attachments;

        // 计算属性
        public String formattedPriority;
        public String formattedStatus;
        public String formattedDate;
        public String formattedDeadline;
        public String timeRemaining;
        public boolean isOverdue;
        public boolean isDueToday;
        public boolean isDueSoon;
        public int completionPercentage;

        @Override
        public String toString() {
            return "DetailedTask{" +
                    "id=" + id +
                    ", title='" + title + '\'' +
                    ", priority='" + priority + '\'' +
                    ", status='" + status + '\'' +
                    ", completed=" + completed +
                    ", deadline='" + deadline + '\'' +
                    ", isOverdue=" + isOverdue +
                    '}';
        }
    }
}