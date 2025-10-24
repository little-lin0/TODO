package com.example.myapplication;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * 服务工具类
 * 提供服务状态检查等工具方法
 */
public class ServiceUtils {

    private static final String TAG = "ServiceUtils";

    /**
     * 检查指定服务是否正在运行
     * @param context 上下文
     * @param serviceClass 服务类
     * @return 是否运行中
     */
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

            for (ActivityManager.RunningServiceInfo service : runningServices) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    Log.d(TAG, "服务 " + serviceClass.getSimpleName() + " 正在运行");
                    return true;
                }
            }

            Log.d(TAG, "服务 " + serviceClass.getSimpleName() + " 未运行");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "检查服务状态失败", e);
            return false;
        }
    }

    /**
     * 获取应用进程的重要性
     * @param context 上下文
     * @return 进程重要性级别
     */
    public static int getAppImportance(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = manager.getRunningAppProcesses();

            if (runningProcesses != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                    if (processInfo.processName.equals(context.getPackageName())) {
                        Log.d(TAG, "应用进程重要性级别: " + processInfo.importance);
                        return processInfo.importance;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取应用进程重要性失败", e);
        }
        return ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
    }

    /**
     * 检查应用是否在后台
     * @param context 上下文
     * @return 是否在后台
     */
    public static boolean isAppInBackground(Context context) {
        int importance = getAppImportance(context);
        return importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }
}