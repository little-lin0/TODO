package com.example.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.Calendar;

public class CleanupService extends Service {

    private static final String TAG = "CleanupService";
    private static final String ACTION_DAILY_CLEANUP = "com.example.myapplication.DAILY_CLEANUP";
    private static final int CLEANUP_REQUEST_CODE = 2000;
    private static final int CLEANUP_DAYS_OLD = 1; // 清理1天前的已读消息

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CleanupService 创建");
        scheduleDailyCleanupAlarm();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "CleanupService 启动");

        if (intent != null && ACTION_DAILY_CLEANUP.equals(intent.getAction())) {
            // 执行清理任务
            performCleanup();
        }

        return START_STICKY;
    }

    private void scheduleDailyCleanupAlarm() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            Intent intent = new Intent(this, CleanupBroadcastReceiver.class);
            intent.setAction(ACTION_DAILY_CLEANUP);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                CLEANUP_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 设置为每天0点执行
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // 如果当前时间已经过了今天的0点，则设置为明天0点
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }

            // 设置重复闹钟，每天0点执行
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            );

            Log.d(TAG, "每日清理闹钟已设置，下次执行时间: " + calendar.getTime());
        } catch (Exception e) {
            Log.e(TAG, "设置每日清理闹钟失败", e);
        }
    }

    private void performCleanup() {
        try {
            Log.d(TAG, "开始执行每日消息清理任务");

            SupabaseInterface supabaseInterface = new SupabaseInterface(this);

            // 在后台线程执行清理
            new Thread(() -> {
                try {
                    boolean success = supabaseInterface.cleanupReadMessages(CLEANUP_DAYS_OLD);

                    if (success) {
                        Log.d(TAG, "每日消息清理任务执行成功");
                    } else {
                        Log.w(TAG, "每日消息清理任务执行失败");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "执行清理任务时发生异常", e);
                } finally {
                    // 释放资源
                    supabaseInterface.destroy();
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "执行每日清理任务失败", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CleanupService 销毁");
    }

    /**
     * 广播接收器，用于接收定时清理广播
     */
    public static class CleanupBroadcastReceiver extends BroadcastReceiver {

        private static final String TAG = "CleanupBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DAILY_CLEANUP.equals(intent.getAction())) {
                Log.d(TAG, "收到每日清理广播");

                // 启动清理服务
                Intent serviceIntent = new Intent(context, CleanupService.class);
                serviceIntent.setAction(ACTION_DAILY_CLEANUP);
                context.startService(serviceIntent);
            }
        }
    }
}