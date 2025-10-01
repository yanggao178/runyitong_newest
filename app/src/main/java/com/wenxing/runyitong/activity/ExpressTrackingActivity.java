package com.wenxing.runyitong.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wenxing.runyitong.R;
import com.wenxing.runyitong.adapter.TrackingInfoAdapter;
import com.wenxing.runyitong.model.ExpressCompany;
import com.wenxing.runyitong.model.TrackingInfo;
import com.wenxing.runyitong.service.ExpressService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpressTrackingActivity extends AppCompatActivity {

    private static final String EXTRA_ORDER_ID = "order_id";
    private static final String EXTRA_LOGISTIC_CODE = "logistic_code";
    private static final String EXTRA_SHIPPER_CODE = "shipper_code";
    private static final String EXTRA_SHIPPER_NAME = "shipper_name";

    private Spinner shipperSpinner;
    private EditText trackingNumberEditText;
    private EditText senderPhoneEditText;
    private Button queryButton;
    private ProgressBar progressBar;
    private TextView trackingNumberTextView;
    private TextView shipperNameTextView;
    private TextView statusTextView;
    private RecyclerView trackingRecyclerView;
    private TextView responseContentTextView; // 新增：显示服务器响应内容的TextView
    private ScrollView responseScrollView; // 新增：用于滚动查看响应内容的ScrollView

    private TrackingInfoAdapter trackingAdapter;
    private List<TrackingInfo.TraceInfo> traceList = new ArrayList<>();
    private List<ExpressCompany> expressCompanyList = new ArrayList<>();
    private Map<String, ExpressCompany> expressCompaniesMap;

    private String orderId;
    private String prefilledTrackingNumber;
    private String prefilledShipperCode;
    private String prefilledShipperName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_express_tracking);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();
        getOrderInfoFromIntent();
        loadExpressCompanies();
    }

    private void initViews() {
        shipperSpinner = findViewById(R.id.shipper_spinner);
        trackingNumberEditText = findViewById(R.id.tracking_number_edittext);
        senderPhoneEditText = findViewById(R.id.sender_phone_edittext);
        queryButton = findViewById(R.id.query_button);
        progressBar = findViewById(R.id.progress_bar);
        trackingNumberTextView = findViewById(R.id.tracking_number_textview);
        shipperNameTextView = findViewById(R.id.shipper_name_textview);
        statusTextView = findViewById(R.id.status_textview);
        trackingRecyclerView = findViewById(R.id.tracking_recyclerview);
        
        // 初始化响应内容显示控件
        try {
            responseScrollView = findViewById(R.id.response_scrollview);
            responseContentTextView = findViewById(R.id.response_content_textview);
        } catch (Exception e) {
            // 如果布局中没有这些控件，记录日志但不崩溃
            Log.w("ExpressTrackingActivity", "响应内容显示控件未找到，需要在布局文件中添加", e);
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("物流查询");
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        trackingAdapter = new TrackingInfoAdapter(this, traceList);
        trackingRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        trackingRecyclerView.setAdapter(trackingAdapter);
    }
    
    private void setupListeners() {
        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryTrackingInfo();
            }
        });
    }

    private void getOrderInfoFromIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            orderId = intent.getStringExtra(EXTRA_ORDER_ID);
            prefilledTrackingNumber = intent.getStringExtra(EXTRA_LOGISTIC_CODE);
            prefilledShipperCode = intent.getStringExtra(EXTRA_SHIPPER_CODE);
            prefilledShipperName = intent.getStringExtra(EXTRA_SHIPPER_NAME);

            // 预填充物流单号
            if (prefilledTrackingNumber != null) {
                trackingNumberEditText.setText(prefilledTrackingNumber);
            }
        }
    }

    private void loadExpressCompanies() {
        // 初始化默认的常用物流公司列表，确保即使网络请求失败也能显示选项
        initializeDefaultExpressCompanies();
        
        ExpressService.getInstance(this).getExpressCompanies(new ExpressService.ApiCallback<Map<String, ExpressCompany>>() {
            @Override
            public void onSuccess(Map<String, ExpressCompany> companies) {
                expressCompaniesMap = companies;
                expressCompanyList.clear();
                expressCompanyList.addAll(companies.values());

                // 创建适配器并设置给Spinner
                List<String> companyNames = new ArrayList<>();
                for (ExpressCompany company : expressCompanyList) {
                    companyNames.add(company.getName());
                }

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                        ExpressTrackingActivity.this,
                        android.R.layout.simple_spinner_item,
                        companyNames
                );
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                shipperSpinner.setAdapter(spinnerAdapter);

                // 如果有预填充的快递公司，选择对应的项
                if (prefilledShipperCode != null && expressCompaniesMap != null) {
                    ExpressCompany company = expressCompaniesMap.get(prefilledShipperCode);
                    if (company != null) {
                        int position = expressCompanyList.indexOf(company);
                        if (position >= 0) {
                            shipperSpinner.setSelection(position);
                        }
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(ExpressTrackingActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                // 如果网络请求失败，但我们已经有默认的物流公司列表，确保Spinner已设置
                if (!expressCompanyList.isEmpty() && shipperSpinner.getAdapter() == null) {
                    setupSpinnerAdapter();
                }
            }
        });
    }

    private void queryTrackingInfo() {
        try {
            String trackingNumber = trackingNumberEditText.getText().toString().trim();
            String senderPhone = senderPhoneEditText.getText().toString().trim();
            if (trackingNumber.isEmpty()) {
                Toast.makeText(this, "请输入物流单号", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedPosition = shipperSpinner.getSelectedItemPosition();
            if (selectedPosition < 0 || selectedPosition >= expressCompanyList.size()) {
                Toast.makeText(this, "请选择快递公司", Toast.LENGTH_SHORT).show();
                return;
            }

            ExpressCompany selectedCompany = expressCompanyList.get(selectedPosition);
            String shipperCode = selectedCompany.getCode();

            showLoading(true);
            // 清空之前的响应内容
            showResponseContent(null);
            
            ExpressService.getInstance(this).trackExpressOrder(trackingNumber, shipperCode, senderPhone, new ExpressService.ApiCallback<TrackingInfo>() {
                @Override
                public void onSuccess(TrackingInfo trackingInfo) {
                    try {
                        showLoading(false);
                        updateUI(trackingInfo);
                        // 显示成功响应的基本信息
                        if (trackingInfo != null) {
                            StringBuilder responseInfoBuilder = new StringBuilder();
                            responseInfoBuilder.append("服务器响应: 成功\n");
                            responseInfoBuilder.append("物流公司: " + (trackingInfo.getShipperName() != null ? trackingInfo.getShipperName() : "未知") + "\n");
                            responseInfoBuilder.append("物流单号: " + (trackingInfo.getOrderSn() != null ? trackingInfo.getOrderSn() : "未知") + "\n");
                            responseInfoBuilder.append("状态: " + (trackingInfo.getStatus() != null ? trackingInfo.getStatus() : "未知") + "\n");
                            responseInfoBuilder.append("轨迹数量: " + (trackingInfo.getTraces() != null ? trackingInfo.getTraces().size() : 0) + "\n\n");
                            
                            // 添加轨迹数据
                            if (trackingInfo.getTraces() != null && !trackingInfo.getTraces().isEmpty()) {
                                responseInfoBuilder.append("物流轨迹详情:\n");
                                for (int i = 0; i < trackingInfo.getTraces().size(); i++) {
                                    TrackingInfo.TraceInfo trace = trackingInfo.getTraces().get(i);
                                    if (trace != null) {
                                        responseInfoBuilder.append("[" + (i + 1) + "] ");
                                        if (trace.getTime() != null) {
                                            responseInfoBuilder.append(trace.getTime() + " - ");
                                        }
                                        String content = trace.getContent();
                                        if (content != null && !content.isEmpty()) {
                                            responseInfoBuilder.append(content + "\n");
                                        } else {
                                            responseInfoBuilder.append("无轨迹描述\n");
                                        }
                                    }
                                }
                            } else {
                                responseInfoBuilder.append("暂无轨迹数据\n");
                            }
                            
                            showResponseContent(responseInfoBuilder.toString());
                        }
                    } catch (Exception e) {
                        // 捕获onSuccess处理过程中可能出现的异常
                        showLoading(false);
                        // 隐藏所有物流信息相关控件
                        trackingRecyclerView.setVisibility(View.GONE);
                        trackingNumberTextView.setVisibility(View.GONE);
                        shipperNameTextView.setVisibility(View.GONE);
                        statusTextView.setVisibility(View.GONE);
                        showResponseContent("处理物流数据时发生异常: " + e.getMessage());
                        Toast.makeText(ExpressTrackingActivity.this, "处理物流数据时发生异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    try {
                        showLoading(false);
                        String displayError = errorMessage != null ? errorMessage : "查询物流信息失败，请重试";
                        Toast.makeText(ExpressTrackingActivity.this, displayError, Toast.LENGTH_SHORT).show();
                        // 隐藏所有物流信息相关控件
                        trackingRecyclerView.setVisibility(View.GONE);
                        trackingNumberTextView.setVisibility(View.GONE);
                        shipperNameTextView.setVisibility(View.GONE);
                        statusTextView.setVisibility(View.GONE);
                        // 显示错误响应信息
                        showResponseContent("服务器响应: 失败\n错误信息: " + displayError);
                    } catch (Exception e) {
                        // 即使在处理错误时也确保UI状态正确
                        try {
                            showLoading(false);
                            // 隐藏所有物流信息相关控件
                            trackingRecyclerView.setVisibility(View.GONE);
                            trackingNumberTextView.setVisibility(View.GONE);
                            shipperNameTextView.setVisibility(View.GONE);
                            statusTextView.setVisibility(View.GONE);
                            showResponseContent("处理错误时发生异常: " + e.getMessage());
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        } catch (Exception e) {
            // 捕获查询逻辑中可能出现的所有异常
            showLoading(false);
            // 隐藏所有物流信息相关控件
            trackingRecyclerView.setVisibility(View.GONE);
            trackingNumberTextView.setVisibility(View.GONE);
            shipperNameTextView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.GONE);
            showResponseContent("发起查询时发生异常: " + e.getMessage());
            Toast.makeText(this, "发起查询时发生异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(TrackingInfo trackingInfo) {
        try {
            if (trackingInfo != null && trackingInfo.isSuccess() && trackingInfo.getTraces() != null && !trackingInfo.getTraces().isEmpty()) {
                // 更新物流信息显示，确保所有字段都不为null
                String orderSn = trackingInfo.getOrderSn() != null ? trackingInfo.getOrderSn() : "未知单号";
                String shipperName = trackingInfo.getShipperName() != null ? trackingInfo.getShipperName() : "未知快递公司";
                String status = trackingInfo.getStatus() != null ? trackingInfo.getStatus() : "未知状态";
                
                trackingNumberTextView.setText("物流单号: " + orderSn);
                shipperNameTextView.setText("快递公司: " + shipperName);
                statusTextView.setText("物流状态: " + status);

                // 更新物流轨迹列表，确保每个轨迹项都有效
                traceList.clear();
                for (TrackingInfo.TraceInfo trace : trackingInfo.getTraces()) {
                    if (trace != null) {
                        traceList.add(trace);
                    }
                }
                
                trackingAdapter.notifyDataSetChanged();
                // 显示物流信息相关控件
                trackingRecyclerView.setVisibility(View.VISIBLE);
                trackingNumberTextView.setVisibility(View.VISIBLE);
                shipperNameTextView.setVisibility(View.VISIBLE);
                statusTextView.setVisibility(View.VISIBLE);
            } else {
                // 隐藏所有物流信息相关控件
                trackingRecyclerView.setVisibility(View.GONE);
                trackingNumberTextView.setVisibility(View.GONE);
                shipperNameTextView.setVisibility(View.GONE);
                statusTextView.setVisibility(View.GONE);
                if (trackingInfo != null && !trackingInfo.isSuccess()) {
                    String errorMsg = trackingInfo.getErrorMsg() != null ? trackingInfo.getErrorMsg() : "查询失败";
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                } else if (trackingInfo == null) {
                    Toast.makeText(this, "未获取到物流信息", Toast.LENGTH_SHORT).show();
                } else if (trackingInfo.getTraces() == null || trackingInfo.getTraces().isEmpty()) {
                    Toast.makeText(this, "暂无物流轨迹记录", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            // 捕获UI更新过程中可能出现的异常
            Log.e("ExpressTrackingActivity", "Error updating UI", e);
            // 隐藏所有物流信息相关控件
            trackingRecyclerView.setVisibility(View.GONE);
            trackingNumberTextView.setVisibility(View.GONE);
            shipperNameTextView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.GONE);
            Toast.makeText(this, "更新物流信息失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 新增：显示服务器响应内容的方法
    private void showResponseContent(String content) {
        try {
            if (responseContentTextView != null && responseScrollView != null) {
                if (content != null) {
                    responseContentTextView.setText(content);
                    responseScrollView.setVisibility(View.VISIBLE);
                } else {
                    responseContentTextView.setText("");
                    responseScrollView.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            Log.w("ExpressTrackingActivity", "显示响应内容时出错", e);
        }
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        queryButton.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    /**
     * 初始化默认的常用物流公司列表
     */
    private void initializeDefaultExpressCompanies() {
        // 清空现有列表
        expressCompanyList.clear();
        
        // 添加常用物流公司
        expressCompanyList.add(new ExpressCompany("顺丰速运", "SF"));
        expressCompanyList.add(new ExpressCompany("中国邮政", "YZPY"));
        expressCompanyList.add(new ExpressCompany("京东物流", "JD"));
        expressCompanyList.add(new ExpressCompany("中通快递", "ZTO"));
        expressCompanyList.add(new ExpressCompany("圆通速递", "YTO"));
        expressCompanyList.add(new ExpressCompany("韵达快递", "YD"));
        expressCompanyList.add(new ExpressCompany("申通快递", "STO"));
        expressCompanyList.add(new ExpressCompany("百世快递", "HTKY"));
        expressCompanyList.add(new ExpressCompany("天天快递", "TTT"));
        expressCompanyList.add(new ExpressCompany("宅急送", "ZJS"));
        expressCompanyList.add(new ExpressCompany("极兔速递", "JTSD"));
        expressCompanyList.add(new ExpressCompany("壹米滴答", "YMDD"));
        // 初始化物流公司Map
        if (expressCompaniesMap == null) {
            expressCompaniesMap = new HashMap<>();
        }
        
        // 将默认公司添加到Map中
        for (ExpressCompany company : expressCompanyList) {
            expressCompaniesMap.put(company.getCode(), company);
        }
    }
    
    /**
     * 设置Spinner适配器
     */
    private void setupSpinnerAdapter() {
        List<String> companyNames = new ArrayList<>();
        for (ExpressCompany company : expressCompanyList) {
            companyNames.add(company.getName());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                ExpressTrackingActivity.this,
                android.R.layout.simple_spinner_item,
                companyNames
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shipperSpinner.setAdapter(spinnerAdapter);
        
        // 如果有预填充的快递公司，选择对应的项
        if (prefilledShipperCode != null && expressCompaniesMap != null) {
            ExpressCompany company = expressCompaniesMap.get(prefilledShipperCode);
            if (company != null) {
                int position = expressCompanyList.indexOf(company);
                if (position >= 0) {
                    shipperSpinner.setSelection(position);
                }
            }
        }
    }

    /**
     * 启动物流跟踪界面的静态方法
     */
    public static void startExpressTrackingActivity(AppCompatActivity activity, String orderId,
                                                  String logisticCode, String shipperCode, String shipperName) {
        Intent intent = new Intent(activity, ExpressTrackingActivity.class);
        intent.putExtra(EXTRA_ORDER_ID, orderId);
        intent.putExtra(EXTRA_LOGISTIC_CODE, logisticCode);
        intent.putExtra(EXTRA_SHIPPER_CODE, shipperCode);
        intent.putExtra(EXTRA_SHIPPER_NAME, shipperName);
        activity.startActivity(intent);
    }
}