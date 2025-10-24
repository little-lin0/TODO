package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 消息详情Activity - 用于显示完整的通知内容
 */
public class MessageDetailActivity extends AppCompatActivity {

    private static final String TAG = "MessageDetailActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建简单的布局
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setPadding(32, 32, 32, 32);
        textView.setTextSize(16);
        textView.setLineSpacing(8, 1.2f);
        scrollView.addView(textView);
        setContentView(scrollView);

        // 获取传递的消息内容
        Intent intent = getIntent();
        String messageTitle = intent.getStringExtra("message_title");
        String messageContent = intent.getStringExtra("message_content");
        String messageType = intent.getStringExtra("message_type");
        long messageId = intent.getLongExtra("message_id", 0);
        String action = intent.getStringExtra("action");

        Log.d(TAG, "显示消息详情: " + messageTitle + " (类型: " + messageType + ")");

        // 设置Activity标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(messageTitle != null ? messageTitle : "消息详情");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 显示消息内容
        if (messageContent != null) {
            textView.setText(messageContent);
        } else {
            textView.setText("暂无详细内容");
        }

        // 处理特殊操作
        if ("view_all_tasks".equals(action)) {
            handleViewAllTasks(messageType);
        }
    }

    /**
     * 处理查看全部任务操作
     */
    private void handleViewAllTasks(String messageType) {
        // 这里可以根据消息类型加载相应的完整任务列表
        Log.d(TAG, "用户请求查看全部任务，消息类型: " + messageType);

        // 可以调用SupabaseInterface获取完整任务列表
        // 然后在这个Activity中显示
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MessageDetailActivity 销毁");
    }
}