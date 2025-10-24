package com.example.myapplication;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MessageListenerService extends Service {

    private static final String TAG = "MessageListenerService";
    private NotificationHelper notificationHelper;
    private SupabaseInterface supabaseInterface;
    private Timer messageTimer;
    private Timer keepAliveTimer; // 额外的保活定时器
    private Handler mainHandler;

    // 当前用户ID (从SharedPreferences获取)
    private String currentUserId = null;

    // 已显示的消息ID集合，避免重复通知
    private Set<Long> displayedMessageIds = new HashSet<>();

    // 用于跟踪已发送的定时通知
    private String lastMorningReportDate = "";
    private String lastEveningReportDate = "";
    private Set<String> sentDeadlineWarnings = new HashSet<>();
    private Set<String> sentOverdueWarnings = new HashSet<>();

    // 新消息广播接收器
    private BroadcastReceiver newMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.myapplication.NEW_MESSAGE".equals(action)) {
                Log.d(TAG, "收到新消息广播，立即检查消息");
                // 立即检查消息，不等待定时器
                checkDatabaseMessages();
            } else if ("com.example.myapplication.TRIGGER_REMINDERS".equals(action)) {
                Log.d(TAG, "收到手动触发任务提醒广播");
                // 立即检查任务提醒
                triggerTaskReminders();
            } else if ("com.example.myapplication.TRIGGER_EVENING_REPORT".equals(action)) {
                Log.d(TAG, "收到手动触发晚报广播");
                // 立即发送晚报
                triggerEveningReport();
            } else if ("com.example.myapplication.TRIGGER_MORNING_REPORT".equals(action)) {
                Log.d(TAG, "收到手动触发晨报广播");
                // 立即发送晨报
                triggerMorningReport();
            } else if ("com.example.myapplication.TRIGGER_ALL_REPORTS".equals(action)) {
                Log.d(TAG, "收到手动触发所有报告广播");
                // 立即检查并发送所有报告
                triggerAllReports();
            } else if ("com.example.myapplication.TRIGGER_DAILY_TODO".equals(action)) {
                Log.d(TAG, "收到手动触发每日待办任务生成广播");
                // 立即生成每日待办任务
                triggerDailyTodoGeneration();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MessageListenerService 创建");

        notificationHelper = new NotificationHelper(this);
        supabaseInterface = new SupabaseInterface(this);
        mainHandler = new Handler(Looper.getMainLooper());

        // 获取当前用户ID
        getCurrentUserIdFromPreferences();

        // 注册新消息广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.myapplication.NEW_MESSAGE");
        filter.addAction("com.example.myapplication.TRIGGER_REMINDERS");
        filter.addAction("com.example.myapplication.TRIGGER_EVENING_REPORT");
        filter.addAction("com.example.myapplication.TRIGGER_MORNING_REPORT");
        filter.addAction("com.example.myapplication.TRIGGER_ALL_REPORTS");
        filter.addAction("com.example.myapplication.TRIGGER_DAILY_TODO");
        registerNewMessageReceiver(filter);

        // 启动前台服务通知
        startForegroundNotification();

        // 设置服务保活机制
        setupKeepAlive();
    }

    /**
     * 注册新消息广播接收器（兼容Android 13+）
     */
    private void registerNewMessageReceiver(IntentFilter filter) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) { // Android 13 (TIRAMISU)
                // 使用反射来安全地调用新的API
                try {
                    // 尝试使用 Context.RECEIVER_NOT_EXPORTED 常量
                    java.lang.reflect.Field field = Context.class.getDeclaredField("RECEIVER_NOT_EXPORTED");
                    int flag = field.getInt(null);
                    registerReceiver(newMessageReceiver, filter, flag);
                    Log.d(TAG, "使用RECEIVER_NOT_EXPORTED标志注册广播接收器");
                } catch (Exception reflectionException) {
                    // 如果反射失败，使用数值常量
                    registerReceiver(newMessageReceiver, filter, 2); // RECEIVER_NOT_EXPORTED = 2
                    Log.d(TAG, "使用数值常量注册广播接收器");
                }
            } else {
                // Android 12及以下版本
                registerReceiver(newMessageReceiver, filter);
                Log.d(TAG, "使用传统方式注册广播接收器");
            }
        } catch (Exception e) {
            Log.e(TAG, "注册广播接收器失败", e);
        }
    }

    /**
     * 设置服务保活机制
     */
    private void setupKeepAlive() {
        try {
            ServiceKeepAliveManger keepAliveManager = new ServiceKeepAliveManger(this);

            // 设置定时唤醒机制
            keepAliveManager.scheduleServiceWakeup();

            Log.d(TAG, "服务保活机制已设置");
        } catch (Exception e) {
            Log.e(TAG, "设置服务保活失败", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MessageListenerService 启动");

        startMessageListening();
        startKeepAliveTimer();

        // 返回 START_STICKY 确保服务被系统杀死后会重启
        return START_STICKY;
    }

    private void startForegroundNotification() {
        android.app.NotificationChannel channel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            channel = new android.app.NotificationChannel(
                "service_channel",
                "后台消息服务",
                android.app.NotificationManager.IMPORTANCE_DEFAULT  // 提升重要性
            );
            channel.setDescription("保持应用在后台监听新消息通知");
            channel.setShowBadge(false);  // 不显示角标
            channel.setSound(null, null);  // 静音

            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // 创建点击通知打开应用的Intent
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        androidx.core.app.NotificationCompat.Builder builder =
            new androidx.core.app.NotificationCompat.Builder(this, "service_channel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("消息监听服务")
                .setContentText("正在后台监听新消息，点击打开应用")
                .setContentIntent(pendingIntent)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)  // 提升优先级
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)  // 持续通知，不可滑动删除
                .setAutoCancel(false)  // 点击后不自动取消
                .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        startForeground(1000, builder.build());
    }

    private void startMessageListening() {
        if (messageTimer != null) {
            messageTimer.cancel();
        }

        messageTimer = new Timer();
        messageTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 只检查数据库中的未读消息（不再模拟消息）
                checkDatabaseMessages();
            }
        }, 1000, 3000); // 1秒后开始，每3秒检查一次（更频繁）
    }

    /**
     * 启动额外的保活定时器
     * 定期发送心跳信号，确保服务保持活跃状态
     */
    private void startKeepAliveTimer() {
        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
        }

        keepAliveTimer = new Timer();
        keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 发送心跳信号和重新设置保活机制
                Log.d(TAG, "服务心跳检查 - 保持活跃状态");

                // 重新设置唤醒机制，确保持续保活
                setupKeepAlive();

                // 检查并确保前台服务状态
                ensureForegroundService();

                // 检查是否需要发送定时提醒和生成每日任务
                checkScheduledNotifications();
            }
        }, 10000, 60000); // 10秒后开始，每1分钟执行一次心跳和检查
    }

    /**
     * 确保前台服务状态
     */
    private void ensureForegroundService() {
        try {
            // 重新设置前台服务通知，确保服务不被系统回收
            startForegroundNotification();
            Log.d(TAG, "前台服务状态已确认");
        } catch (Exception e) {
            Log.e(TAG, "确保前台服务状态失败", e);
        }
    }

    /**
     * 检查并发送定时通知（晨报、晚报、任务提醒）
     */
    private void checkScheduledNotifications() {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = calendar.get(java.util.Calendar.MINUTE);

            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

            Log.d(TAG, "检查定时通知 - 当前时间: " + hour + ":" + minute + ", 日期: " + today);

            // 获取晨报晚报时间配置
            ReportTimeConfig timeConfig = getReportTimeConfig();

            // 检查晨报 - 修复过了晨报时间不发送的问题
            // 1. 在晨报时间的5分钟内正常发送
            // 2. 如果已过晨报时间且当天还没发送过，则补发晨报
            boolean shouldSendMorningReport = false;
            String morningReportReason = "";

            if (isTimeInRange(hour, minute, timeConfig.morningHour, timeConfig.morningMinute, 5)
                && !lastMorningReportDate.equals(today)) {
                shouldSendMorningReport = true;
                morningReportReason = "正常时间发送";
            } else if (isAfterMorningTime(hour, minute, timeConfig.morningHour, timeConfig.morningMinute)
                && !lastMorningReportDate.equals(today)
                && isSuitableForMorningReportResend(hour, timeConfig.morningHour)) {
                shouldSendMorningReport = true;
                morningReportReason = "智能补发";
            }

            if (shouldSendMorningReport) {
                sendMorningReport(today);
                lastMorningReportDate = today;
                Log.d(TAG, "已发送晨报: " + today + " (配置时间: " + timeConfig.morningHour + ":" + timeConfig.morningMinute + ", 发送原因: " + morningReportReason + ")");
            }

            // 检查晚报 - 修复过了晚报时间不发送的问题
            // 1. 在晚报时间的5分钟内正常发送
            // 2. 如果已过晚报时间且当天还没发送过，则补发晚报
            boolean shouldSendEveningReport = false;
            String eveningReportReason = "";

            if (isTimeInRange(hour, minute, timeConfig.eveningHour, timeConfig.eveningMinute, 5)
                && !lastEveningReportDate.equals(today)) {
                shouldSendEveningReport = true;
                eveningReportReason = "正常时间发送";
            } else if (isAfterEveningTime(hour, minute, timeConfig.eveningHour, timeConfig.eveningMinute)
                && !lastEveningReportDate.equals(today)) {
                shouldSendEveningReport = true;
                eveningReportReason = "过时补发";
            }

            if (shouldSendEveningReport) {
                sendEveningReport(today);
                lastEveningReportDate = today;
                Log.d(TAG, "已发送晚报: " + today + " (配置时间: " + timeConfig.eveningHour + ":" + timeConfig.eveningMinute + ", 发送原因: " + eveningReportReason + ")");
            }

            // 检查任务即将超时和逾期提醒（每15分钟检查一次，更频繁）
//            if (minute % 2 == 0) {
                checkTaskDeadlineWarnings(today, hour);
                checkOverdueTasks(today, hour);
                Log.d(TAG, "已执行任务超时检查 - " + hour + ":" + minute);
//            }

            // 每分钟检查并生成每日待办任务（改为每次心跳检查都执行，内部会判断今天是否已生成）
            checkAndGenerateDailyTodos(today);

        } catch (Exception e) {
            Log.e(TAG, "检查定时通知失败", e);
        }
    }

    /**
     * 获取晨报晚报时间配置
     */
    private ReportTimeConfig getReportTimeConfig() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            String morningTime = prefs.getString("morning_notify_time", "09:00");
            String eveningTime = prefs.getString("evening_notify_time", "18:00");

            ReportTimeConfig config = new ReportTimeConfig();

            // 解析晨报时间
            String[] morningParts = morningTime.split(":");
            config.morningHour = Integer.parseInt(morningParts[0]);
            config.morningMinute = morningParts.length > 1 ? Integer.parseInt(morningParts[1]) : 0;

            // 解析晚报时间
            String[] eveningParts = eveningTime.split(":");
            config.eveningHour = Integer.parseInt(eveningParts[0]);
            config.eveningMinute = eveningParts.length > 1 ? Integer.parseInt(eveningParts[1]) : 0;

            Log.d(TAG, "读取报告时间配置: 晨报=" + morningTime + ", 晚报=" + eveningTime);
            return config;

        } catch (Exception e) {
            Log.e(TAG, "获取报告时间配置失败，使用默认值", e);
            // 返回默认配置
            ReportTimeConfig defaultConfig = new ReportTimeConfig();
            defaultConfig.morningHour = 9;
            defaultConfig.morningMinute = 0;
            defaultConfig.eveningHour = 18;
            defaultConfig.eveningMinute = 0;
            return defaultConfig;
        }
    }

    /**
     * 检查当前时间是否在指定时间范围内
     */
    private boolean isTimeInRange(int currentHour, int currentMinute, int targetHour, int targetMinute, int toleranceMinutes) {
        // 将时间转换为分钟数便于比较
        int currentTotalMinutes = currentHour * 60 + currentMinute;
        int targetTotalMinutes = targetHour * 60 + targetMinute;
        int maxTotalMinutes = targetTotalMinutes + toleranceMinutes;

        return currentTotalMinutes >= targetTotalMinutes && currentTotalMinutes < maxTotalMinutes;
    }

    /**
     * 检查是否已过晚报时间（用于晚报补发逻辑）
     * 如果当前时间已经过了设定的晚报时间，则返回true
     */
    private boolean isAfterEveningTime(int currentHour, int currentMinute, int eveningHour, int eveningMinute) {
        int currentTotalMinutes = currentHour * 60 + currentMinute;
        int eveningTotalMinutes = eveningHour * 60 + eveningMinute;
        return currentTotalMinutes >= eveningTotalMinutes;
    }

    /**
     * 检查是否已过晨报时间（用于晨报补发逻辑）
     * 如果当前时间已经过了设定的晨报时间，则返回true
     */
    private boolean isAfterMorningTime(int currentHour, int currentMinute, int morningHour, int morningMinute) {
        int currentTotalMinutes = currentHour * 60 + currentMinute;
        int morningTotalMinutes = morningHour * 60 + morningMinute;
        return currentTotalMinutes >= morningTotalMinutes;
    }

    /**
     * 检查是否适合补发晨报
     * 避免在不合适的时间（如深夜）补发晨报
     */
    private boolean isSuitableForMorningReportResend(int currentHour, int morningHour) {
        // 晨报补发的合适时间范围：
        // 1. 设定的晨报时间之后
        // 2. 但不要超过晚上20:00（避免深夜发送晨报）
        // 3. 或者第二天早上6:00之后（新的一天开始）
        return (currentHour >= morningHour && currentHour < 20) || currentHour >= 6;
    }

    /**
     * 报告时间配置类
     */
    private static class ReportTimeConfig {
        int morningHour = 9;
        int morningMinute = 0;
        int eveningHour = 18;
        int eveningMinute = 0;
    }

    /**
     * 发送晨报
     */
    private void sendMorningReport(String date) {
        try {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "用户ID为空，跳过晨报发送");
                return;
            }

            // 获取今日任务
            String tasksJson = supabaseInterface.getTodayTasks(currentUserId);
            java.util.List<SupabaseInterface.SimpleMessage> todayTasks =
                supabaseInterface.parseTasksAsMessages(tasksJson);

            // 构建折叠和完整内容
            String summary;
            StringBuilder fullContent = new StringBuilder();

            if (todayTasks.isEmpty()) {
                summary = "今天暂无任务安排，祝您度过愉快的一天！";
                fullContent.append("🌅 早安！\n\n")
                          .append("今天暂无任务安排\n")
                          .append("祝您度过愉快的一天！\n\n")
                          .append("💪 保持积极的心态！");
            } else {
                summary = String.format("今日共有 %d 项任务等待处理", todayTasks.size());

                fullContent.append("🌅 早安！今日任务详情\n\n")
                          .append("📊 任务总数：").append(todayTasks.size()).append(" 项\n\n");

                // 完整内容显示所有任务
                for (int i = 0; i < todayTasks.size(); i++) {
                    SupabaseInterface.SimpleMessage task = todayTasks.get(i);
                    fullContent.append(i + 1).append(". 📋 ").append(task.title);

                    if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                        fullContent.append("\n   💡 ").append(task.content);
                    }
                    fullContent.append("\n\n");
                }

                fullContent.append("💪 今日加油，祝您工作顺利！");
            }

            // 使用新的折叠通知系统发送晨报
            NotificationHelper.ExpandableMessage morningMessage = new NotificationHelper.ExpandableMessage(
                "🌅 晨报提醒",
                summary,
                fullContent.toString(),
                "morning_report"
            );

            mainHandler.post(() -> {
                notificationHelper.showExpandableNotification(morningMessage);
            });

        } catch (Exception e) {
            Log.e(TAG, "发送晨报失败", e);
        }
    }

    /**
     * 发送晚报
     */
    private void sendEveningReport(String date) {
        try {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "用户ID为空，跳过晚报发送");
                return;
            }

            // 获取今日完成的任务
            String completedTasksJson = supabaseInterface.getTodayCompletedTasks(currentUserId);
            java.util.List<SupabaseInterface.SimpleMessage> completedTasks =
                supabaseInterface.parseTasksAsMessages(completedTasksJson);

            // 获取今日未完成的任务
            String pendingTasksJson = supabaseInterface.getTodayPendingTasks(currentUserId);
            java.util.List<SupabaseInterface.SimpleMessage> pendingTasks =
                supabaseInterface.parseTasksAsMessages(pendingTasksJson);

            // 构建折叠和完整内容
            String summary;
            StringBuilder fullContent = new StringBuilder();

            if (completedTasks.isEmpty() && pendingTasks.isEmpty()) {
                summary = "今日无任务记录，早点休息！";
                fullContent.append("🌙 晚安！\n\n")
                          .append("今日无任务记录\n")
                          .append("🌟 早点休息，保持健康！");
            } else {
                int totalTasks = completedTasks.size() + pendingTasks.size();
                double completionRate = totalTasks > 0 ? (double) completedTasks.size() / totalTasks * 100 : 0;

                summary = String.format("完成率 %.0f%% (%d/%d)，%s",
                    completionRate, completedTasks.size(), totalTasks,
                    completionRate >= 80 ? "表现优秀！" : "继续努力！");

                fullContent.append("🌙 今日工作总结详情\n\n")
                          .append(String.format("📊 完成率：%.1f%% (%d/%d)\n\n",
                              completionRate, completedTasks.size(), totalTasks));

                // 完整内容显示所有已完成任务
                if (!completedTasks.isEmpty()) {
                    fullContent.append("✅ 已完成任务 (").append(completedTasks.size()).append("项)：\n");
                    for (int i = 0; i < completedTasks.size(); i++) {
                        SupabaseInterface.SimpleMessage task = completedTasks.get(i);
                        fullContent.append((i + 1)).append(". ").append(task.title);
                        if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                            fullContent.append("\n   💡 ").append(task.content);
                        }
                        fullContent.append("\n");
                    }
                    fullContent.append("\n");
                }

                // 完整内容显示所有未完成任务
                if (!pendingTasks.isEmpty()) {
                    fullContent.append("⏰ 待完成任务 (").append(pendingTasks.size()).append("项)：\n");
                    for (int i = 0; i < pendingTasks.size(); i++) {
                        SupabaseInterface.SimpleMessage task = pendingTasks.get(i);
                        fullContent.append((i + 1)).append(". ").append(task.title);
                        if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                            fullContent.append("\n   💡 ").append(task.content);
                        }
                        fullContent.append("\n");
                    }
                    fullContent.append("\n");
                }

                fullContent.append("🌟 辛苦了一天，早点休息！");
            }

            // 使用新的折叠通知系统发送晚报
            NotificationHelper.ExpandableMessage eveningMessage = new NotificationHelper.ExpandableMessage(
                "🌙 晚报总结",
                summary,
                fullContent.toString(),
                "evening_report"
            );

            mainHandler.post(() -> {
                notificationHelper.showExpandableNotification(eveningMessage);
            });

        } catch (Exception e) {
            Log.e(TAG, "发送晚报失败", e);
        }
    }

    /**
     * 检查任务即将超时提醒
     */
    private void checkTaskDeadlineWarnings(String date, int hour) {
        try {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "跳过任务即将超时检查：用户ID为空");
                return;
            }

            Log.d(TAG, "开始检查任务即将超时提醒，用户ID: " + currentUserId);

            // 获取即将到期的任务（24小时内）
            String upcomingTasksJson = supabaseInterface.getUpcomingDeadlineTasks(currentUserId);
            Log.d(TAG, "获取即将到期任务结果: " + upcomingTasksJson);

            java.util.List<SupabaseInterface.SimpleMessage> upcomingTasks =
                supabaseInterface.parseTasksAsMessages(upcomingTasksJson);

            Log.d(TAG, "解析后的即将到期任务数量: " + upcomingTasks.size());

            // 收集需要提醒的任务
            java.util.List<SupabaseInterface.SimpleMessage> newDeadlineTasks = new java.util.ArrayList<>();

            for (SupabaseInterface.SimpleMessage task : upcomingTasks) {
                String warningKey = date + "-" + task.id + "-deadline";

                // 避免重复发送同一天的同一任务提醒
                if (!sentDeadlineWarnings.contains(warningKey)) {
                    sentDeadlineWarnings.add(warningKey);
                    newDeadlineTasks.add(task);
                    Log.d(TAG, "新增任务即将超时提醒: " + task.title);
                } else {
                    Log.d(TAG, "跳过重复提醒: " + task.title + " (key: " + warningKey + ")");
                }
            }

            // 如果有新的即将到期任务，发送折叠通知
            if (!newDeadlineTasks.isEmpty()) {
                String summary;
                StringBuilder fullContent = new StringBuilder();

                if (newDeadlineTasks.size() == 1) {
                    SupabaseInterface.SimpleMessage task = newDeadlineTasks.get(0);
                    summary = task.title + " 即将到期，请及时完成";

                    fullContent.append("⚠️ 任务即将到期提醒\n\n")
                              .append("📋 任务：").append(task.title).append("\n");

                    if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                        fullContent.append("💡 描述：").append(task.content).append("\n");
                    }

                    fullContent.append("\n⏰ 该任务即将在24小时内到期，请及时完成！");
                } else {
                    summary = String.format("有 %d 项任务即将到期，请及时处理", newDeadlineTasks.size());

                    fullContent.append("⚠️ 多项任务即将到期\n\n")
                              .append("📊 即将到期任务数：").append(newDeadlineTasks.size()).append(" 项\n\n");

                    for (int i = 0; i < newDeadlineTasks.size(); i++) {
                        SupabaseInterface.SimpleMessage task = newDeadlineTasks.get(i);
                        fullContent.append(i + 1).append(". 📋 ").append(task.title);

                        if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                            fullContent.append("\n   💡 ").append(task.content);
                        }
                        fullContent.append("\n\n");
                    }

                    fullContent.append("⏰ 以上任务即将在24小时内到期，请优先处理！");
                }

                // 使用新的折叠通知系统发送到期提醒
                NotificationHelper.ExpandableMessage deadlineMessage = new NotificationHelper.ExpandableMessage(
                    "⚠️ 任务到期提醒",
                    summary,
                    fullContent.toString(),
                    "deadline_warning"
                );

                mainHandler.post(() -> {
                    notificationHelper.showExpandableNotification(deadlineMessage);
                });

                Log.d(TAG, "发送任务即将超时提醒，任务数: " + newDeadlineTasks.size());
            }

            // 清理过期的提醒记录（保留最近3天）
            cleanupWarningSet(sentDeadlineWarnings, date);

        } catch (Exception e) {
            Log.e(TAG, "检查任务即将超时提醒失败", e);
        }
    }

    /**
     * 检查逾期任务提醒
     */
    private void checkOverdueTasks(String date, int hour) {
        try {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "跳过逾期任务检查：用户ID为空");
                return;
            }

            Log.d(TAG, "开始检查逾期任务提醒，用户ID: " + currentUserId);

            // 获取逾期任务
            String overdueTasksJson = supabaseInterface.getOverdueTasks(currentUserId);
            Log.d(TAG, "获取逾期任务结果: " + overdueTasksJson);

            java.util.List<SupabaseInterface.SimpleMessage> overdueTasks =
                supabaseInterface.parseTasksAsMessages(overdueTasksJson);

            Log.d(TAG, "解析后的逾期任务数量: " + overdueTasks.size());

            if (!overdueTasks.isEmpty()) {
                // 收集新的逾期任务
                java.util.List<SupabaseInterface.SimpleMessage> newOverdueTasks = new java.util.ArrayList<>();

                for (SupabaseInterface.SimpleMessage task : overdueTasks) {
                    String overdueKey = date + "-" + task.id + "-overdue";

                    // 避免重复发送同一天的同一任务逾期提醒
                    if (!sentOverdueWarnings.contains(overdueKey)) {
                        sentOverdueWarnings.add(overdueKey);
                        newOverdueTasks.add(task);
                        Log.d(TAG, "新增逾期任务提醒: " + task.title);
                    } else {
                        Log.d(TAG, "跳过重复逾期提醒: " + task.title + " (key: " + overdueKey + ")");
                    }
                }

                if (!newOverdueTasks.isEmpty()) {
                    String summary;
                    StringBuilder fullContent = new StringBuilder();

                    if (newOverdueTasks.size() == 1) {
                        SupabaseInterface.SimpleMessage task = newOverdueTasks.get(0);
                        summary = task.title + " 已逾期，请尽快处理";

                        fullContent.append("🚨 任务逾期提醒\n\n")
                                  .append("❌ 逾期任务：").append(task.title).append("\n");

                        if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                            fullContent.append("💡 描述：").append(task.content).append("\n");
                        }

                        fullContent.append("\n🔥 该任务已逾期，请尽快处理！");
                    } else {
                        summary = String.format("共 %d 项任务已逾期，请优先处理", overdueTasks.size());

                        fullContent.append("🚨 多项任务逾期提醒\n\n")
                                  .append("📊 逾期任务总数：").append(overdueTasks.size()).append(" 项\n")
                                  .append("🆕 新增逾期任务：").append(newOverdueTasks.size()).append(" 项\n\n");

                        for (int i = 0; i < newOverdueTasks.size(); i++) {
                            SupabaseInterface.SimpleMessage task = newOverdueTasks.get(i);
                            fullContent.append(i + 1).append(". ❌ ").append(task.title);

                            if (task.content != null && !task.content.isEmpty() && !"null".equals(task.content)) {
                                fullContent.append("\n   💡 ").append(task.content);
                            }
                            fullContent.append("\n\n");
                        }

                        fullContent.append("🔥 以上任务均已逾期，请立即优先处理！");
                    }

                    // 使用新的折叠通知系统发送逾期提醒
                    NotificationHelper.ExpandableMessage overdueMessage = new NotificationHelper.ExpandableMessage(
                        "🚨 逾期任务提醒",
                        summary,
                        fullContent.toString(),
                        "overdue_warning"
                    );

                    mainHandler.post(() -> {
                        notificationHelper.showExpandableNotification(overdueMessage);
                    });

                    Log.d(TAG, "发送逾期任务提醒，新增任务数: " + newOverdueTasks.size());
                } else {
                    Log.d(TAG, "所有逾期任务今日已提醒过，跳过发送");
                }
            } else {
                Log.d(TAG, "当前无逾期任务");
            }

            // 清理过期的提醒记录（保留最近3天）
            cleanupWarningSet(sentOverdueWarnings, date);

        } catch (Exception e) {
            Log.e(TAG, "检查逾期任务提醒失败", e);
        }
    }

    /**
     * 清理提醒记录集合，保留最近几天的记录
     */
    private void cleanupWarningSet(java.util.Set<String> warningSet, String currentDate) {
        try {
            if (warningSet.size() > 100) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                java.util.Date current = sdf.parse(currentDate);
                long threeDaysAgo = current.getTime() - (3 * 24 * 60 * 60 * 1000);

                java.util.Iterator<String> iterator = warningSet.iterator();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    try {
                        String dateStr = key.split("-")[0];
                        java.util.Date keyDate = sdf.parse(dateStr);
                        if (keyDate.getTime() < threeDaysAgo) {
                            iterator.remove();
                        }
                    } catch (Exception e) {
                        // 如果解析失败，删除该记录
                        iterator.remove();
                    }
                }

                Log.d(TAG, "清理提醒记录，保留 " + warningSet.size() + " 条记录");
            }
        } catch (Exception e) {
            Log.e(TAG, "清理提醒记录失败", e);
        }
    }

    private void checkDatabaseMessages() {
        try {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "当前用户ID为空，跳过消息检查");
                return;
            }

            // 从Supabase获取未读消息
            String messagesJson = supabaseInterface.getUnreadMessages(currentUserId);
            List<SupabaseInterface.SimpleMessage> unreadMessages = supabaseInterface.parseMessages(messagesJson);

            Log.d(TAG, "检查到 " + unreadMessages.size() + " 条未读消息");

            for (SupabaseInterface.SimpleMessage message : unreadMessages) {
                // 只显示发送人不是自己的消息（Supabase查询已经排除了自己发送的消息）
                if (!currentUserId.equals(message.senderId)) {
                    // 检查是否已经显示过此消息
                    if (!displayedMessageIds.contains(message.id)) {
                        // 记录已显示的消息ID
                        displayedMessageIds.add(message.id);

                        // 在主线程中显示通知
                        mainHandler.post(() -> {
                            String title = message.title;
                            String content = message.content;

                            if ("task_complete".equals(message.messageType)) {
                                title = "✅ 任务完成";

                                // 处理任务标题，避免显示null
                                String taskTitle = (message.taskTitle != null && !message.taskTitle.isEmpty()) ?
                                    message.taskTitle : "未命名任务";

                                content = "🎉 " + message.senderId + " 完成了：\n📋 " + taskTitle;

                                // 处理完成备注，避免显示null
                                if (message.completionNotes != null &&
                                    !message.completionNotes.isEmpty() &&
                                    !"null".equals(message.completionNotes)) {

                                    // 限制备注长度，避免消息过长
                                    String notes = message.completionNotes.length() > 50 ?
                                        message.completionNotes.substring(0, 50) + "..." : message.completionNotes;
                                    content += "\n💬 " + notes;
                                }
                            }

                            Log.d(TAG, "显示消息通知: " + title + " - " + content + " (ID: " + message.id + ")");
                            notificationHelper.showMessageNotification(message.senderId, content, message.id);

                            // 不自动标记为已读，等待用户点击通知后标记
                        });
                    } else {
                        Log.d(TAG, "消息ID " + message.id + " 已显示过，跳过");
                    }
                }
            }

            // 定期清理已显示消息ID集合，避免内存泄漏
            cleanupDisplayedMessageIds();
        } catch (Exception e) {
            Log.e(TAG, "检查Supabase消息时出错", e);
        }
    }

    // 清理已显示消息ID集合，保留最近1000条记录
    private void cleanupDisplayedMessageIds() {
        try {
            if (displayedMessageIds.size() > 1000) {
                // 清理一半的记录，保留最近的消息
                Set<Long> newSet = new HashSet<>();
                List<Long> sortedIds = new java.util.ArrayList<>(displayedMessageIds);
                java.util.Collections.sort(sortedIds, java.util.Collections.reverseOrder());

                // 保留最新的500条记录
                for (int i = 0; i < Math.min(500, sortedIds.size()); i++) {
                    newSet.add(sortedIds.get(i));
                }

                displayedMessageIds = newSet;
                Log.d(TAG, "已清理显示消息ID集合，保留 " + displayedMessageIds.size() + " 条记录");
            }
        } catch (Exception e) {
            Log.e(TAG, "清理显示消息ID失败", e);
        }
    }

    // 从SharedPreferences获取当前用户ID（通过SupabaseInterface同步的）
    private void getCurrentUserIdFromPreferences() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

            // 从SupabaseInterface同步的用户ID获取
            currentUserId = prefs.getString("current_user_id", null);

            if (currentUserId == null || currentUserId.isEmpty()) {
                // 如果没有设置，使用默认值（实际应用中应该提醒用户设置）
                currentUserId = "默认用户";
                Log.w(TAG, "未找到当前用户ID，使用默认值: " + currentUserId);
            } else {
                Log.d(TAG, "当前用户ID: " + currentUserId);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取用户ID失败", e);
            currentUserId = "未知用户";
        }
    }

    // 更新当前用户ID
    public void updateCurrentUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            currentUserId = userId;

            // 保存到SharedPreferences
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            prefs.edit().putString("current_user_id", userId).apply();

            Log.d(TAG, "用户ID已更新: " + currentUserId);
        }
    }

    /**
     * 手动触发任务提醒检查（用于测试）
     */
    public void triggerTaskReminders() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);

            Log.d(TAG, "手动触发任务提醒检查");
            checkTaskDeadlineWarnings(today, hour);
            checkOverdueTasks(today, hour);
            Log.d(TAG, "手动任务提醒检查完成");
        } catch (Exception e) {
            Log.e(TAG, "手动触发任务提醒失败", e);
        }
    }

    /**
     * 手动触发晚报发送（用于测试和补发）
     */
    public void triggerEveningReport() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

            Log.d(TAG, "手动触发晚报发送");

            // 检查是否今天已经发送过晚报
            if (lastEveningReportDate.equals(today)) {
                Log.d(TAG, "今日晚报已发送过，强制重新发送");
            }

            // 强制发送晚报（不管是否已发送过）
            sendEveningReport(today);
            lastEveningReportDate = today;
            Log.d(TAG, "手动晚报发送完成: " + today);
        } catch (Exception e) {
            Log.e(TAG, "手动触发晚报失败", e);
        }
    }

    /**
     * 手动触发晨报发送（用于测试和补发）
     */
    public void triggerMorningReport() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

            Log.d(TAG, "手动触发晨报发送");

            // 检查是否今天已经发送过晨报
            if (lastMorningReportDate.equals(today)) {
                Log.d(TAG, "今日晨报已发送过，强制重新发送");
            }

            // 强制发送晨报（不管是否已发送过）
            sendMorningReport(today);
            lastMorningReportDate = today;
            Log.d(TAG, "手动晨报发送完成: " + today);
        } catch (Exception e) {
            Log.e(TAG, "手动触发晨报失败", e);
        }
    }

    /**
     * 手动触发所有报告检查（晨报+晚报+任务提醒）
     */
    public void triggerAllReports() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);

            Log.d(TAG, "手动触发所有报告检查");

            // 检查是否需要发送晨报
            if (!lastMorningReportDate.equals(today)) {
                Log.d(TAG, "检测到今日晨报未发送，补发晨报");
                sendMorningReport(today);
                lastMorningReportDate = today;
            }

            // 检查是否需要发送晚报
            if (!lastEveningReportDate.equals(today)) {
                Log.d(TAG, "检测到今日晚报未发送，补发晚报");
                sendEveningReport(today);
                lastEveningReportDate = today;
            }

            // 触发任务提醒检查
            checkTaskDeadlineWarnings(today, hour);
            checkOverdueTasks(today, hour);

            Log.d(TAG, "所有报告检查完成");
        } catch (Exception e) {
            Log.e(TAG, "手动触发所有报告失败", e);
        }
    }

    /**
     * 手动触发每日待办任务生成（用户保存配置后调用）
     */
    public void triggerDailyTodoGeneration() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

            Log.d(TAG, "手动触发每日待办任务生成");

            // 强制生成今日任务（不检查lastAddedDate）
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("daily_todo_enabled", true);
            boolean skipHolidays = prefs.getBoolean("daily_todo_skip_holidays", true);
            String template = prefs.getString("daily_todo_template", "");

            if (!enabled || template == null || template.trim().isEmpty()) {
                Log.d(TAG, "每日待办未启用或模板为空，跳过生成");
                return;
            }

            // 如果启用了跳过节假日，检查今天是否为节假日
            if (skipHolidays && isHoliday(new java.util.Date())) {
                Log.d(TAG, "今天是节假日，跳过每日待办任务生成");
                return;
            }

            // 生成今日任务
            generateDailyTodos(template, today);

            // 更新最后添加日期
            prefs.edit().putString("daily_todo_last_added_date", today).apply();
            Log.d(TAG, "手动触发每日待办任务生成完成");

        } catch (Exception e) {
            Log.e(TAG, "手动触发每日待办任务生成失败", e);
        }
    }

    /**
     * 检查今天是否已经创建过每日任务
     * @param today 今天的日期字符串 (格式: yyyy-MM-dd)
     * @return true表示今天已经创建过每日任务，false表示还未创建
     */
    private boolean checkTaskExistsToday(String today) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整，无法检查每日任务是否存在");
                return false;
            }

            // 构建查询URL：查询今天创建的、标记为每日待办的任务
            // created_at.gte=今天开始时间&created_at.lt=明天开始时间&is_daily_todo=eq.true&user_id=eq.用户ID
            String todayStart = today + "T00:00:00";
            String tomorrowStart = getNextDay(today) + "T00:00:00";

            String queryUrl = supabaseUrl + "/rest/v1/tasks?" +
                "user_id=eq." + supabaseUserId +
                "&is_daily_todo=eq.true" +
                "&created_at=gte." + todayStart +
                "&created_at=lt." + tomorrowStart +
                "&select=id";

            java.net.URL url = new java.net.URL(queryUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", supabaseAnonKey);
            connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // 解析响应，检查是否有任务
                String jsonResponse = response.toString();
                org.json.JSONArray tasksArray = new org.json.JSONArray(jsonResponse);
                boolean exists = tasksArray.length() > 0;

                if (exists) {
                    Log.d(TAG, "今天已存在每日任务，数量: " + tasksArray.length());
                }

                return exists;
            } else {
                Log.w(TAG, "检查每日任务是否存在失败，响应码: " + responseCode);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "检查每日任务是否存在异常", e);
            return false;
        }
    }

    /**
     * 获取下一天的日期字符串
     * @param dateStr 日期字符串 (格式: yyyy-MM-dd)
     * @return 下一天的日期字符串 (格式: yyyy-MM-dd)
     */
    private String getNextDay(String dateStr) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(dateStr);
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            Log.e(TAG, "获取下一天日期失败", e);
            return dateStr;
        }
    }

    /**
     * 检查并生成每日待办任务
     */
    private void checkAndGenerateDailyTodos(String today) {
        try {
            if (currentUserId == null || currentUserId.isEmpty()) {
                Log.w(TAG, "用户ID为空，跳过每日待办任务生成");
                return;
            }

            // 获取每日待办配置
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("daily_todo_enabled", true);
            boolean skipHolidays = prefs.getBoolean("daily_todo_skip_holidays", true);
            String template = prefs.getString("daily_todo_template", "上班打卡|high|work|09:50\n下班打卡|high|work|19:00");
            String lastAddedDate = prefs.getString("daily_todo_last_added_date", "");

            // 如果未启用或模板为空，跳过（不打印日志，避免频繁输出）
            if (!enabled || template == null || template.trim().isEmpty()) {
                return;
            }

            // 通过查询数据库检查今天是否已经创建过每日任务
            if (checkTaskExistsToday(today)) {
                // 今天已经创建过每日任务，跳过（不打印日志，避免频繁输出）
                return;
            }

            // 执行到这里说明需要生成任务，打印详细日志
            Log.d(TAG, "检测到需要生成每日待办任务 - 配置: enabled=" + enabled + ", skipHolidays=" + skipHolidays + ", today=" + today);

            // 如果启用了跳过节假日，检查今天是否为节假日
            if (skipHolidays && isHoliday(new java.util.Date())) {
                Log.d(TAG, "今天是节假日，跳过每日待办任务生成");
                return;
            }

            // 生成今日任务
            generateDailyTodos(template, today);
            Log.d(TAG, "每日待办任务生成完成，日期: " + today);

        } catch (Exception e) {
            Log.e(TAG, "检查并生成每日待办任务失败", e);
        }
    }

    /**
     * 生成每日待办任务
     */
    private void generateDailyTodos(String template, String date) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            String supabaseUrl = prefs.getString("supabase_url", "");
            String supabaseAnonKey = prefs.getString("supabase_anon_key", "");
            String supabaseUserId = prefs.getString("supabase_user_id", "");

            if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty() || supabaseUserId.isEmpty()) {
                Log.w(TAG, "Supabase配置不完整，无法生成每日待办任务");
                return;
            }

            // 解析模板
            String[] lines = template.split("\n");
            int createdCount = 0;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 解析任务配置：标题|优先级|分类|时间|完成人
                String[] parts = line.split("\\|");
                if (parts.length < 1) continue;

                String title = parts[0].trim();
                String priority = parts.length > 1 ? parts[1].trim() : "medium";
                String category = parts.length > 2 ? parts[2].trim() : "other";
                String timeStr = parts.length > 3 ? parts[3].trim() : "23:59";
                String assignee = parts.length > 4 ? parts[4].trim() : currentUserId;

                // 构建截止时间
                String deadline = buildDeadlineTime(date, timeStr);

                // 创建任务
                if (createDailyTask(supabaseUrl, supabaseAnonKey, supabaseUserId, title, priority, category, deadline, assignee)) {
                    createdCount++;
                    Log.d(TAG, "创建每日待办任务成功: " + title);
                }
            }

            Log.d(TAG, "共创建 " + createdCount + " 个每日待办任务");

        } catch (Exception e) {
            Log.e(TAG, "生成每日待办任务失败", e);
        }
    }

    /**
     * 构建截止时间
     */
    private String buildDeadlineTime(String date, String timeStr) {
        try {
            // 解析时间字符串
            String[] timeParts = timeStr.split(":");
            int hours = timeParts.length > 0 ? Integer.parseInt(timeParts[0].trim()) : 23;
            int minutes = timeParts.length > 1 ? Integer.parseInt(timeParts[1].trim()) : 59;

            // 构建完整的截止时间字符串
            return String.format("%sT%02d:%02d:00", date, hours, minutes);
        } catch (Exception e) {
            Log.e(TAG, "构建截止时间失败，使用默认值", e);
            return date + "T23:59:00";
        }
    }

    /**
     * 创建每日任务到Supabase
     */
    private boolean createDailyTask(String supabaseUrl, String supabaseAnonKey, String supabaseUserId,
                                    String title, String priority, String category, String deadline, String assignee) {
        try {
            String createUrl = supabaseUrl + "/rest/v1/tasks";

            java.net.URL url = new java.net.URL(createUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("apikey", supabaseAnonKey);
            connection.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "return=minimal");
            connection.setDoOutput(true);

            // 构建任务数据
            org.json.JSONObject taskData = new org.json.JSONObject();
            taskData.put("user_id", supabaseUserId);
            taskData.put("title", title);
            taskData.put("description", "");
            taskData.put("priority", priority);
            taskData.put("category", category);
            taskData.put("deadline", deadline);
            taskData.put("assignee", assignee);
            // 使用本地时间格式，不使用UTC标识'Z'
            taskData.put("created_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date()));
            taskData.put("completed", false);
            taskData.put("status", "pending");
            taskData.put("is_daily_todo", true); // 标记为每日待办

            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(connection.getOutputStream());
            writer.write(taskData.toString());
            writer.flush();
            writer.close();

            int responseCode = connection.getResponseCode();
            boolean success = responseCode == java.net.HttpURLConnection.HTTP_OK ||
                            responseCode == java.net.HttpURLConnection.HTTP_CREATED ||
                            responseCode == java.net.HttpURLConnection.HTTP_NO_CONTENT;

            if (!success) {
                Log.e(TAG, "创建任务失败，响应码: " + responseCode);
            }

            return success;
        } catch (Exception e) {
            Log.e(TAG, "创建每日任务异常", e);
            return false;
        }
    }

    /**
     * 判断是否为节假日（包含周末和法定节假日，排除调休上班日）
     */
    private boolean isHoliday(java.util.Date date) {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);

            int year = calendar.get(java.util.Calendar.YEAR);
            int month = calendar.get(java.util.Calendar.MONTH) + 1; // 月份从0开始
            int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
            int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK); // 1=周日, 2=周一, ..., 7=周六

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

            // 检查是否为周末（周六或周日，排除了上面的调休上班日）
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MessageListenerService 销毁");

        if (messageTimer != null) {
            messageTimer.cancel();
            messageTimer = null;
        }

        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
            keepAliveTimer = null;
        }

        // 注销广播接收器
        try {
            unregisterReceiver(newMessageReceiver);
        } catch (Exception e) {
            Log.e(TAG, "注销广播接收器失败", e);
        }

        // 释放Supabase接口资源
        if (supabaseInterface != null) {
            supabaseInterface.destroy();
        }

        // 服务被销毁时尝试重启
        Intent restartIntent = new Intent(this, MessageListenerService.class);
        startService(restartIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}