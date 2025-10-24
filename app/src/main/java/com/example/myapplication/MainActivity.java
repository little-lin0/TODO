package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private final int REQUEST_SELECT_FILE = 100;
    private final int PERMISSION_REQUEST_CODE = 200;
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        setupWebView();

        // 请求权限
        requestPermissions();
        // 检查并申请通知权限
        checkNotificationPermission();
        // 设置后台服务保活
        setupServiceKeepAlive();
        // 启动消息监听服务
        startMessageService();
        // 启动数据清理服务
        startCleanupService();
        // 检查并生成每日待办任务
        checkAndGenerateDailyTasks();
        // 处理从通知点击进入的情况
        handleNotificationClick(getIntent());



    }

    // 以下方法已废弃，因为现在使用BigTextStyle在通知栏中直接展开显示详情
    /*
    private void handleNotificationClick(Intent intent) {
        if (intent != null && "VIEW_DETAIL".equals(intent.getAction())) {
            String title = intent.getStringExtra("notification_title");
            String body = intent.getStringExtra("notification_body");
            String data = intent.getStringExtra("notification_data");

            // 显示详情对话框
            showNotificationDetailDialog(title, body, data);
        }
    }

    private void showNotificationDetailDialog(String title, String body, String data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // 创建详情视图
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView titleView = new TextView(this);
        titleView.setText("标题: " + title);
        titleView.setTextSize(18);
        titleView.setTypeface(null, Typeface.BOLD);
        layout.addView(titleView);

        TextView bodyView = new TextView(this);
        bodyView.setText("内容: " + body);
        bodyView.setTextSize(16);
        bodyView.setPadding(0, 20, 0, 20);
        layout.addView(bodyView);

        builder.setView(layout)
                .setPositiveButton("确定", (dialog, which) -> dialog.dismiss())
                .setTitle("通知详情")
                .show();
    }
    */

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void startMessageService() {
        Intent serviceIntent = new Intent(this, MessageListenerService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "消息监听服务已启动", Toast.LENGTH_SHORT).show();

        // 同步用户设置到服务
        syncUserSettingsToService();

        // 请求电池优化设置（首次启动时）
        requestBatteryOptimizationSettings();
    }

    /**
     * 请求用户将应用加入电池优化白名单
     */
    private void requestBatteryOptimizationSettings() {
        try {
            ServiceKeepAliveManger keepAliveManager = new ServiceKeepAliveManger(this);

            // 检查是否已经在白名单中
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                String packageName = getPackageName();

                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    // 显示提示对话框，引导用户设置
                    showBatteryOptimizationDialog(keepAliveManager);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "请求电池优化设置失败", e);
        }
    }

    /**
     * 显示电池优化设置对话框
     */
    private void showBatteryOptimizationDialog(ServiceKeepAliveManger keepAliveManager) {
        new AlertDialog.Builder(this)
            .setTitle("后台消息提醒设置")
            .setMessage("为了确保您能及时收到消息通知，建议将应用加入电池优化白名单。\n\n这样可以保证应用在后台正常运行，不会被系统清理。")
            .setPositiveButton("去设置", (dialog, which) -> {
                keepAliveManager.requestIgnoreBatteryOptimizations();
            })
            .setNegativeButton("暂不设置", (dialog, which) -> {
                // 用户选择不设置，记录日志
                Log.d("MainActivity", "用户选择不设置电池优化");
            })
            .setCancelable(false)
            .show();
    }

    private void startCleanupService() {
        Intent cleanupIntent = new Intent(this, CleanupService.class);
        startService(cleanupIntent);
        Log.d("MainActivity", "数据清理服务已启动");
    }

    // 检查并生成每日待办任务（首次启动时触发一次，之后由MessageListenerService每分钟监听检查）
    private void checkAndGenerateDailyTasks() {
        try {
            Log.d("MainActivity", "开始检查并生成每日待办任务（静默模式，应用启动时）");

            // 使用SupabaseInterface来触发每日待办任务生成，传入WebView引用以便刷新任务列表
            SupabaseInterface supabaseInterface = new SupabaseInterface(this, webView);

            // 在后台线程执行任务生成，使用静默模式（不显示Toast）
            new Thread(() -> {
                try {
                    // 调用triggerDailyTodoGenerationInternal方法，传入false表示不显示Toast
                    supabaseInterface.triggerDailyTodoGenerationInternal(false);
                    Log.d("MainActivity", "每日待办任务生成触发成功（应用启动时）");
                } catch (Exception e) {
                    Log.e("MainActivity", "触发每日待办任务生成失败", e);
                } finally {
                    // 注意：不要在这里立即销毁，因为triggerDailyTodoGenerationInternal内部使用了executorService
                    // supabaseInterface.destroy();
                }
            }).start();

        } catch (Exception e) {
            Log.e("MainActivity", "检查并生成每日待办任务失败", e);
        }
    }

    // 处理通知点击事件
    private void handleNotificationClick(Intent intent) {
        try {
            if (intent != null && intent.getBooleanExtra("mark_as_read", false)) {
                long messageId = intent.getLongExtra("message_id", -1);
                if (messageId > 0) {
                    Log.d("MainActivity", "处理通知点击，标记消息为已读: " + messageId);
                    markMessageAsRead(messageId);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "处理通知点击失败", e);
        }
    }

    // 标记消息为已读
    private void markMessageAsRead(long messageId) {
        try {
            // 直接使用SupabaseInterface标记消息为已读
            SupabaseInterface supabaseInterface = new SupabaseInterface(this);

            // 异步标记消息为已读
            supabaseInterface.markMessageAsReadAsync(messageId).thenAccept(success -> {
                if (success) {
                    Log.d("MainActivity", "消息已标记为已读: " + messageId);
                } else {
                    Log.w("MainActivity", "标记消息为已读失败: " + messageId);
                }
                // 在异步操作完成后释放资源
                supabaseInterface.destroy();
            }).exceptionally(throwable -> {
                Log.e("MainActivity", "标记消息为已读异常: " + messageId, throwable);
                // 发生异常时也要释放资源
                supabaseInterface.destroy();
                return null;
            });

        } catch (Exception e) {
            Log.e("MainActivity", "标记消息为已读失败", e);
        }
    }

    // 设置后台服务保活
    private void setupServiceKeepAlive() {
        try {
            ServiceKeepAliveManger keepAliveManager = new ServiceKeepAliveManger(this);

            // 请求忽略电池优化（用户可选）
            keepAliveManager.requestIgnoreBatteryOptimizations();

            // 检查自启动权限
            keepAliveManager.checkAutoStartPermission();

            Log.d("MainActivity", "后台服务保活设置完成");
        } catch (Exception e) {
            Log.e("MainActivity", "设置后台服务保活失败", e);
        }
    }

    // 同步用户设置到消息服务
    private void syncUserSettingsToService() {
        try {
            // 这里可以通过广播或其他方式通知服务更新用户ID
            // 现在简化处理，在服务中直接读取SharedPreferences
            Log.d("MainActivity", "用户设置已同步到消息服务");
        } catch (Exception e) {
            Log.e("MainActivity", "同步用户设置失败", e);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                // 权限被拒绝
                Toast.makeText(this, "通知权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "默认通知";
            String description = "应用默认通知渠道";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel("default_channel", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    class NotificationInterface {
        private Context context;

        public NotificationInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void showNotification(String title, String optionsJson) {
            ((Activity) context).runOnUiThread(() -> {
                try {
                    NotificationManager manager = (NotificationManager)
                            context.getSystemService(Context.NOTIFICATION_SERVICE);

                    String channelId = "web_notifications";

                    // 创建通知渠道
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel channel = new NotificationChannel(
                                channelId,
                                "Web Notifications",
                                NotificationManager.IMPORTANCE_HIGH
                        );
                        manager.createNotificationChannel(channel);
                    }

                    // 解析通知内容
                    String body = "点击查看详情";
                    String bigText = optionsJson; // 用于展开显示的完整文本
                    try {
                        JSONObject options = new JSONObject(optionsJson);
                        if (options.has("body")) {
                            body = options.getString("body");
                            bigText = body; // 使用body作为展开文本
                        }
                    } catch (Exception e) {
                        // 如果解析失败，使用原始JSON作为展开文本
                        bigText = optionsJson;
                    }

                    // 创建主点击Intent（点击通知主体时打开应用）
                    Intent mainIntent = new Intent(context, context.getClass());
                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent mainPendingIntent = PendingIntent.getActivity(
                            context,
                            (int) System.currentTimeMillis(),
                            mainIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    // 使用BigTextStyle创建可展开的通知
                    NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                            .bigText(bigText)
                            .setBigContentTitle(title)
                            .setSummaryText("点击展开查看完整内容");

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                            .setContentTitle(title)
                            .setContentText(body.length() > 50 ? body.substring(0, 50) + "..." : body) // 折叠时显示摘要
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(mainPendingIntent)
                            .setStyle(bigTextStyle) // 设置大文本样式
                            .setDefaults(NotificationCompat.DEFAULT_ALL);

                    manager.notify((int) System.currentTimeMillis(), builder.build());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }


    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // 处理通知点击事件
        handleNotificationClick(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 确保WebView在应用恢复时正常工作
        if (webView != null) {
            webView.onResume();
            // 检查WebView是否需要重新加载
            webView.evaluateJavascript("document.readyState", value -> {
                if (value == null || value.equals("null") || value.equals("\"\"")) {
                    // 如果页面没有正确加载，重新加载
                    webView.loadUrl("file:///android_asset/index.html");
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            webView.destroyDrawingCache();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        // 启用JavaScript
        webSettings.setJavaScriptEnabled(true);

        // 启用DOM存储
        webSettings.setDomStorageEnabled(true);
        // 启用混合内容和文件访问
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);


        // Android 14 特殊配置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            // 处理文件选择
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {

                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }

                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            // 处理JavaScript的alert调用，防止窗口泄漏
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                // 使用Toast替代Alert对话框，避免窗口泄漏
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
                result.confirm();
                return true;
            }

        });



        // 添加接口
        webView.addJavascriptInterface(new NotificationInterface(this), "AndroidNotification");
        webView.addJavascriptInterface(new SupabaseInterface(this, webView), "AndroidDatabase");




        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // 注入Notification API
                String js = "if (!window.Notification) {" +
                        "  window.Notification = function(title, options) {" +
                        "    AndroidNotification.showNotification(title, JSON.stringify(options));" +
                        "  };" +
                        "  window.Notification.permission = 'granted';" +
                        "  window.Notification.requestPermission = function() {" +
                        "    return Promise.resolve('granted');" +
                        "  };" +
                        "}";
                view.evaluateJavascript(js, null);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");

    }

    // 请求运行时权限
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            String[] permissions = {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.CAMERA
            };

            List<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsToRequest.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            // Android 12及以下
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null) return;

            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else {
                    ClipData clipData = intent.getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    }
                }
            }

            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }
}
