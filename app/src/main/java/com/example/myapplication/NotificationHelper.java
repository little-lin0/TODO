package com.example.myapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "message_channel";
    private static final String CHANNEL_NAME = "消息通知";
    private static final String CHANNEL_DESC = "接收新消息通知";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private NotificationManager notificationManager;

    /**
     * 可折叠消息数据结构
     */
    public static class ExpandableMessage {
        public String title;           // 消息标题
        public String summary;         // 简短摘要（折叠时显示）
        public String fullContent;     // 完整内容（展开后显示）
        public String messageType;     // 消息类型：morning_report, evening_report, deadline_warning, overdue_warning
        public long messageId;         // 消息ID
        public String senderName;      // 发送者名称

        public ExpandableMessage(String title, String summary, String fullContent, String messageType) {
            this.title = title;
            this.summary = summary;
            this.fullContent = fullContent;
            this.messageType = messageType;
            this.messageId = System.currentTimeMillis();
            this.senderName = "系统通知";
        }
    }

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            notificationManager.createNotificationChannel(channel);
        }
    }

    public void showNotification(String title, String message, String senderName) {
        showNotification(title, message, senderName, -1); // 默认无消息ID
    }

    public void showNotification(String title, String message, String senderName, long messageId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 如果有消息ID，添加到Intent中用于点击后标记已读
        if (messageId > 0) {
            intent.putExtra("message_id", messageId);
            intent.putExtra("mark_as_read", true);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            (int) (messageId > 0 ? messageId : System.currentTimeMillis()),
            intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // 使用应用图标
            .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(message)
            .setSubText(senderName)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(new long[]{0, 300, 300, 300})
            .setLights(0xff00ff00, 300, 1000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        // 使用消息ID作为通知ID，避免通知被覆盖
        int notificationId = messageId > 0 ? (int) messageId : NOTIFICATION_ID;
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    public void showMessageNotification(String senderName, String messageContent) {
        showMessageNotification(senderName, messageContent, -1);
    }

    public void showMessageNotification(String senderName, String messageContent, long messageId) {
        showNotification("新消息", messageContent, senderName, messageId);
    }

    /**
     * 显示可折叠的系统通知
     */
    public void showExpandableNotification(ExpandableMessage message) {
        Intent expandIntent = new Intent(context, MessageDetailActivity.class);
        expandIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        expandIntent.putExtra("message_title", message.title);
        expandIntent.putExtra("message_content", message.fullContent);
        expandIntent.putExtra("message_type", message.messageType);
        expandIntent.putExtra("message_id", message.messageId);

        PendingIntent expandPendingIntent = PendingIntent.getActivity(
            context,
            (int) message.messageId,
            expandIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // 创建可展开的大文本通知
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
            .bigText(message.fullContent)
            .setBigContentTitle(message.title)
            .setSummaryText("点击查看详情");

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // 使用应用图标
            .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
            .setContentTitle(message.title)
            .setContentText(message.summary + " (点击查看详情)")
            .setSubText(message.senderName)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(new long[]{0, 300, 300, 300})
            .setLights(0xff00ff00, 300, 1000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(expandPendingIntent)
            .setStyle(bigTextStyle);

        // 添加快速操作按钮
        if ("morning_report".equals(message.messageType) || "evening_report".equals(message.messageType)) {
            Intent viewAllIntent = new Intent(context, MessageDetailActivity.class);
            viewAllIntent.putExtra("action", "view_all_tasks");
            viewAllIntent.putExtra("message_type", message.messageType);
            PendingIntent viewAllPendingIntent = PendingIntent.getActivity(
                context, (int) (message.messageId + 1), viewAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            notificationBuilder.addAction(R.mipmap.ic_launcher, "查看全部", viewAllPendingIntent);
        }

        notificationManager.notify((int) message.messageId, notificationBuilder.build());
    }

    public void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public void cancelAllNotifications() {
        notificationManager.cancelAll();
    }
}