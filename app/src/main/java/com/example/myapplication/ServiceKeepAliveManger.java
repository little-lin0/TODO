package com.example.myapplication;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * 服务保活管理器
 * 处理应用后台运行的各种限制和优化
 */
public class ServiceKeepAliveManger {

    private static final String TAG = "ServiceKeepAlive";
    private Context context;

    public ServiceKeepAliveManger(Context context) {
        this.context = context;
    }

    /**
     * 请求忽略电池优化
     */
    @TargetApi(Build.VERSION_CODES.M)
    public void requestIgnoreBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                String packageName = context.getPackageName();

                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Log.d(TAG, "请求忽略电池优化");
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } else {
                    Log.d(TAG, "应用已在电池优化白名单中");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "请求电池优化权限失败", e);
            // 如果直接请求失败，引导用户手动设置
            openBatteryOptimizationSettings();
        }
    }

    /**
     * 打开电池优化设置页面
     */
    public void openBatteryOptimizationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开电池优化设置失败", e);
        }
    }

    /**
     * 检查自启动权限
     */
    public void checkAutoStartPermission() {
        try {
            // 不同厂商的自启动管理页面
            String[] autoStartIntents = {
                "com.miui.securitycenter.autostart.AutoStartManagementActivity", // 小米
                "com.letv.android.letvsafe.AutobootManageActivity", // 乐视
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity", // 华为
                "com.huawei.systemmanager.optimize.process.ProtectActivity", // 华为
                "com.coloros.safecenter.permission.startup.StartupAppListActivity", // OPPO
                "com.coloros.safecenter.startupapp.StartupAppListActivity", // OPPO
                "com.oppo.safe.permission.startup.StartupAppListActivity", // OPPO
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity", // VIVO
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager", // VIVO
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity", // VIVO
                "com.samsung.android.lool.disableapp.activity.DisableAppActivity", // 三星
                "com.samsung.android.sm.ui.battery.BatteryActivity" // 三星
            };

            for (String intentStr : autoStartIntents) {
                try {
                    Intent intent = new Intent();
                    intent.setClassName("android", intentStr);
                    if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        Log.d(TAG, "找到自启动管理页面: " + intentStr);
                        // 这里可以引导用户去设置，但不自动跳转，避免打扰用户
                        break;
                    }
                } catch (Exception e) {
                    // 继续尝试下一个
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查自启动权限失败", e);
        }
    }

    /**
     * 设置定时唤醒机制
     */
    public void scheduleServiceWakeup() {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent("com.example.myapplication.SERVICE_WAKEUP");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 每5分钟检查一次服务状态（更频繁的检查）
            long intervalMillis = 5 * 60 * 1000; // 5分钟
            long triggerAtMillis = System.currentTimeMillis() + intervalMillis;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ 使用精确闹钟
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ 需要检查精确闹钟权限
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtMillis, intervalMillis, pendingIntent);
            }

            Log.d(TAG, "服务唤醒定时器已设置");
        } catch (Exception e) {
            Log.e(TAG, "设置服务唤醒失败", e);
        }
    }

    /**
     * 检查通知权限
     */
    public boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Android 13以下默认有通知权限
    }

    /**
     * 显示用户引导对话框
     */
    public void showKeepAliveGuide() {
        // 这个方法可以在MainActivity中调用，向用户说明为什么需要这些权限
        Log.d(TAG, "建议用户进行以下设置：");
        Log.d(TAG, "1. 将应用加入电池优化白名单");
        Log.d(TAG, "2. 允许应用自启动");
        Log.d(TAG, "3. 允许应用后台运行");
        Log.d(TAG, "4. 开启通知权限");
    }
}