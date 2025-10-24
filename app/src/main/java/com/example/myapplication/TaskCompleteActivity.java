package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TaskCompleteActivity extends AppCompatActivity {
    private static final String TAG = "TaskCompleteActivity";
    private static final int REQUEST_SELECT_IMAGE_1 = 101;
    private static final int REQUEST_SELECT_IMAGE_2 = 102;

    private TextView tvTaskTitle;
    private EditText etCompleteNotes;
    private EditText etAssignee;
    private TextView tvCompletionTime;
    private Button btnConfirm;
    private Button btnCancel;
    private ImageButton btnBack;
    private LinearLayout llImage1Container;
    private LinearLayout llImage2Container;
    private ImageView ivPreview1;
    private ImageView ivPreview2;
    private Button btnRemoveImage1;
    private Button btnRemoveImage2;

    private String taskId;
    private String taskTitle;
    private String taskData;
    private Uri selectedImage1;
    private Uri selectedImage2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_complete);

        // 初始化视图
        initViews();

        // 获取传递的任务数据
        Intent intent = getIntent();
        taskId = intent.getStringExtra("task_id");
        taskTitle = intent.getStringExtra("task_title");
        taskData = intent.getStringExtra("task_data");

        // 显示任务标题和完成时间
        if (taskTitle != null) {
            tvTaskTitle.setText(taskTitle);
        }
        updateCompletionTime();

        // 设置监听器
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTaskTitle = findViewById(R.id.tvTaskTitle);
        etCompleteNotes = findViewById(R.id.etCompleteNotes);
        etAssignee = findViewById(R.id.etAssignee);
        tvCompletionTime = findViewById(R.id.tvCompletionTime);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);
        llImage1Container = findViewById(R.id.llImage1Container);
        llImage2Container = findViewById(R.id.llImage2Container);
        ivPreview1 = findViewById(R.id.ivPreview1);
        ivPreview2 = findViewById(R.id.ivPreview2);
        btnRemoveImage1 = findViewById(R.id.btnRemoveImage1);
        btnRemoveImage2 = findViewById(R.id.btnRemoveImage2);
    }

    private void updateCompletionTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        String currentTime = sdf.format(new Date());
        tvCompletionTime.setText("🕐 完成时间：" + currentTime);
    }

    private void setupListeners() {
        // 返回按钮
        btnBack.setOnClickListener(v -> finish());

        // 取消按钮
        btnCancel.setOnClickListener(v -> finish());

        // 确认完成按钮
        btnConfirm.setOnClickListener(v -> completeTask());

        // 图片1选择
        llImage1Container.setOnClickListener(v -> selectImage(1));

        // 图片2选择
        llImage2Container.setOnClickListener(v -> selectImage(2));

        // 移除图片1
        btnRemoveImage1.setOnClickListener(v -> removeImage(1));

        // 移除图片2
        btnRemoveImage2.setOnClickListener(v -> removeImage(2));
    }

    private void selectImage(int imageNumber) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        int requestCode = (imageNumber == 1) ? REQUEST_SELECT_IMAGE_1 : REQUEST_SELECT_IMAGE_2;
        startActivityForResult(intent, requestCode);
    }

    private void removeImage(int imageNumber) {
        if (imageNumber == 1) {
            selectedImage1 = null;
            ivPreview1.setImageDrawable(null);
            ivPreview1.setVisibility(View.GONE);
            btnRemoveImage1.setVisibility(View.GONE);
            findViewById(R.id.tvUploadPlaceholder1).setVisibility(View.VISIBLE);
        } else {
            selectedImage2 = null;
            ivPreview2.setImageDrawable(null);
            ivPreview2.setVisibility(View.GONE);
            btnRemoveImage2.setVisibility(View.GONE);
            findViewById(R.id.tvUploadPlaceholder2).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();

            if (requestCode == REQUEST_SELECT_IMAGE_1) {
                selectedImage1 = imageUri;
                ivPreview1.setImageURI(imageUri);
                ivPreview1.setVisibility(View.VISIBLE);
                btnRemoveImage1.setVisibility(View.VISIBLE);
                findViewById(R.id.tvUploadPlaceholder1).setVisibility(View.GONE);
                Toast.makeText(this, "图片1已选择", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_SELECT_IMAGE_2) {
                selectedImage2 = imageUri;
                ivPreview2.setImageURI(imageUri);
                ivPreview2.setVisibility(View.VISIBLE);
                btnRemoveImage2.setVisibility(View.VISIBLE);
                findViewById(R.id.tvUploadPlaceholder2).setVisibility(View.GONE);
                Toast.makeText(this, "图片2已选择", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void completeTask() {
        // 获取表单数据
        String notes = etCompleteNotes.getText().toString().trim();
        String assignee = etAssignee.getText().toString().trim();

        try {
            // 构建完成数据JSON
            JSONObject completeObj = new JSONObject();
            if (taskId != null) {
                completeObj.put("task_id", taskId);
            }
            completeObj.put("notes", notes);
            completeObj.put("assignee", assignee);
            completeObj.put("completion_time", tvCompletionTime.getText().toString());

            // 处理图片
            JSONArray imagesArray = new JSONArray();
            if (selectedImage1 != null) {
                imagesArray.put(selectedImage1.toString());
            }
            if (selectedImage2 != null) {
                imagesArray.put(selectedImage2.toString());
            }
            if (imagesArray.length() > 0) {
                completeObj.put("images", imagesArray);
            }

            // 返回结果
            Intent resultIntent = new Intent();
            resultIntent.putExtra("complete_data", completeObj.toString());
            setResult(RESULT_OK, resultIntent);

            Toast.makeText(this, "任务已完成", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Log.e(TAG, "完成任务失败", e);
            Toast.makeText(this, "完成任务失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
