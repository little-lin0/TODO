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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TaskEditActivity extends AppCompatActivity {
    private static final String TAG = "TaskEditActivity";
    private static final int REQUEST_SELECT_IMAGES = 100;

    private EditText etTaskTitle;
    private EditText etTaskNotes;
    private Spinner spPriority;
    private Spinner spCategory;
    private EditText etDeadline;
    private EditText etAssignee;
    private Button btnSave;
    private Button btnCancel;
    private ImageButton btnBack;
    private Button btnSelectImages;

    private String taskId;
    private String taskData;
    private ArrayList<Uri> selectedImages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);

        // 初始化视图
        initViews();

        // 获取传递的任务数据
        Intent intent = getIntent();
        taskId = intent.getStringExtra("task_id");
        taskData = intent.getStringExtra("task_data");

        // 加载任务数据
        if (taskData != null) {
            loadTaskData(taskData);
        }

        // 设置监听器
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etTaskTitle = findViewById(R.id.etTaskTitle);
        etTaskNotes = findViewById(R.id.etTaskNotes);
        spPriority = findViewById(R.id.spPriority);
        spCategory = findViewById(R.id.spCategory);
        etDeadline = findViewById(R.id.etDeadline);
        etAssignee = findViewById(R.id.etAssignee);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        btnSelectImages = findViewById(R.id.btnSelectImages);

        // 设置优先级下拉框
        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(
                this, R.array.priority_array, android.R.layout.simple_spinner_item);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPriority.setAdapter(priorityAdapter);

        // 设置分类下拉框
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this, R.array.category_array, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);
    }

    private void loadTaskData(String jsonData) {
        try {
            JSONObject taskObj = new JSONObject(jsonData);

            // 加载任务标题
            if (taskObj.has("title")) {
                etTaskTitle.setText(taskObj.getString("title"));
            }

            // 加载任务备注
            if (taskObj.has("notes")) {
                etTaskNotes.setText(taskObj.getString("notes"));
            }

            // 加载优先级
            if (taskObj.has("priority")) {
                String priority = taskObj.getString("priority");
                int position = getPositionFromPriority(priority);
                spPriority.setSelection(position);
            }

            // 加载分类
            if (taskObj.has("category")) {
                String category = taskObj.getString("category");
                int position = getPositionFromCategory(category);
                spCategory.setSelection(position);
            }

            // 加载截止时间
            if (taskObj.has("deadline")) {
                etDeadline.setText(taskObj.getString("deadline"));
            }

            // 加载完成人
            if (taskObj.has("assignee")) {
                etAssignee.setText(taskObj.getString("assignee"));
            }

        } catch (Exception e) {
            Log.e(TAG, "加载任务数据失败", e);
            Toast.makeText(this, "加载任务数据失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int getPositionFromPriority(String priority) {
        switch (priority.toLowerCase()) {
            case "high":
                return 0;
            case "medium":
                return 1;
            case "low":
                return 2;
            default:
                return 0;
        }
    }

    private int getPositionFromCategory(String category) {
        switch (category.toLowerCase()) {
            case "work":
                return 0;
            case "life":
                return 1;
            case "study":
                return 2;
            case "other":
                return 3;
            default:
                return 0;
        }
    }

    private String getPriorityFromPosition(int position) {
        switch (position) {
            case 0:
                return "high";
            case 1:
                return "medium";
            case 2:
                return "low";
            default:
                return "high";
        }
    }

    private String getCategoryFromPosition(int position) {
        switch (position) {
            case 0:
                return "work";
            case 1:
                return "life";
            case 2:
                return "study";
            case 3:
                return "other";
            default:
                return "work";
        }
    }

    private void setupListeners() {
        // 返回按钮
        btnBack.setOnClickListener(v -> finish());

        // 取消按钮
        btnCancel.setOnClickListener(v -> finish());

        // 选择图片按钮
        btnSelectImages.setOnClickListener(v -> selectImages());

        // 保存按钮
        btnSave.setOnClickListener(v -> saveTask());
    }

    private void selectImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_SELECT_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_IMAGES && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                if (data.getClipData() != null) {
                    // 多张图片
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        selectedImages.add(imageUri);
                    }
                } else if (data.getData() != null) {
                    // 单张图片
                    selectedImages.add(data.getData());
                }
                Toast.makeText(this, "已选择 " + selectedImages.size() + " 张图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveTask() {
        // 获取表单数据
        String title = etTaskTitle.getText().toString().trim();
        String notes = etTaskNotes.getText().toString().trim();
        String priority = getPriorityFromPosition(spPriority.getSelectedItemPosition());
        String category = getCategoryFromPosition(spCategory.getSelectedItemPosition());
        String deadline = etDeadline.getText().toString().trim();
        String assignee = etAssignee.getText().toString().trim();

        // 验证必填项
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "请输入任务标题", Toast.LENGTH_SHORT).show();
            etTaskTitle.requestFocus();
            return;
        }

        try {
            // 构建任务数据JSON
            JSONObject taskObj = new JSONObject();
            if (taskId != null) {
                taskObj.put("id", taskId);
            }
            taskObj.put("title", title);
            taskObj.put("notes", notes);
            taskObj.put("priority", priority);
            taskObj.put("category", category);
            taskObj.put("deadline", deadline);
            taskObj.put("assignee", assignee);

            // 处理图片
            if (!selectedImages.isEmpty()) {
                JSONArray imagesArray = new JSONArray();
                for (Uri uri : selectedImages) {
                    imagesArray.put(uri.toString());
                }
                taskObj.put("images", imagesArray);
            }

            // 返回结果
            Intent resultIntent = new Intent();
            resultIntent.putExtra("task_data", taskObj.toString());
            setResult(RESULT_OK, resultIntent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "保存任务失败", e);
            Toast.makeText(this, "保存任务失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
