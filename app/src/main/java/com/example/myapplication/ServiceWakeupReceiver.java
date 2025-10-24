package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 服务唤醒接收器
 * 用于定期检查和重启消息监听服务
 */
public class ServiceWakeupReceiver extends BroadcastReceiver {

    private static final String TAG = "ServiceWakeupReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Log.d(TAG, "收到服务唤醒信号，检查消息监听服务状态");

            // 检查服务是否正在运行
            if (!ServiceUtils.isServiceRunning(context, MessageListenerService.class)) {
                Log.d(TAG, "消息监听服务未运行，正在重启...");

                Intent serviceIntent = new Intent(context, MessageListenerService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "消息监听服务正在正常运行");
            }

            // 重新设置下一次唤醒
            ServiceKeepAliveManger keepAliveManager = new ServiceKeepAliveManger(context);
            keepAliveManager.scheduleServiceWakeup();

        } catch (Exception e) {
            Log.e(TAG, "服务唤醒处理失败", e);
        }
    }
}