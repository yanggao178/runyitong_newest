package com.wenxing.runyitong.fragment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.wenxing.runyitong.R;
import com.wenxing.runyitong.activity.LoginActivity;
import com.wenxing.runyitong.activity.OrderActivity;
import com.wenxing.runyitong.activity.SettingsActivity;
import com.wenxing.runyitong.activity.MyAppointmentsActivity;
import com.wenxing.runyitong.activity.MyPrescriptionsActivity;
import com.wenxing.runyitong.activity.HealthRecordActivity;
import com.wenxing.runyitong.activity.MemberRegistrationActivity;

public class ProfileFragment extends Fragment {

    private Button btnExpressOrders, btnLogin, btnLogout, btnBecomeMember;
    private CardView cardSettings, cardMyAppointments, cardMyPrescriptions, cardHealthRecord;
    private TextView tvUsername, tvWelcome;
    private SharedPreferences sharedPreferences;
    
    // SharedPreferences相关常量
    private static final String PREFS_NAME = "user_login_state";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USERNAME = "username";

    private static final String USER_ID = "user_id";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        initViews(view);
        setupClickListeners();
        
        return view;
    }
    
    private void initViews(View view) {
        btnExpressOrders = view.findViewById(R.id.btn_express_orders);
        btnLogin = view.findViewById(R.id.btn_login);
        btnLogout = view.findViewById(R.id.btn_logout);
        btnBecomeMember = view.findViewById(R.id.btn_become_member);
        cardSettings = view.findViewById(R.id.card_settings);
        tvUsername = view.findViewById(R.id.tv_username);
        tvWelcome = view.findViewById(R.id.tv_welcome);
        cardMyAppointments = view.findViewById(R.id.card_my_appointments);
        cardMyPrescriptions = view.findViewById(R.id.card_my_prescriptions);
        cardHealthRecord = view.findViewById(R.id.card_health_record);
        
        // 初始化SharedPreferences
        if (getActivity() != null) {
            sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, getActivity().MODE_PRIVATE);
        }
        
        // 检查关键视图是否找到
        if (btnLogin == null) {
            android.util.Log.e("ProfileFragment", "btnLogin not found in layout");
        }
        if (btnBecomeMember == null) {
            android.util.Log.e("ProfileFragment", "btnBecomeMember not found in layout");
        }
        if (btnExpressOrders == null) {
            android.util.Log.e("ProfileFragment", "btnExpressOrders not found in layout");
        }
        if (cardSettings == null) {
            android.util.Log.e("ProfileFragment", "cardSettings not found in layout");
        }
        if (cardMyAppointments == null) {
            android.util.Log.e("ProfileFragment", "cardMyAppointments not found in layout");
        }
        if (cardMyPrescriptions == null) {
            android.util.Log.e("ProfileFragment", "cardMyPrescriptions not found in layout");
        }
        
        if (cardHealthRecord == null) {
            android.util.Log.e("ProfileFragment", "cardHealthRecord not found in layout");
        }
        
        updateLoginStatus();
        updateMemberButtonVisibility();
    }
    
    private void setupClickListeners() {
        // 登录按钮 - 仅用于跳转到登录页面
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                try {
                    android.util.Log.d("ProfileFragment", "Login button clicked");
                    
                    // 简化的启动逻辑，移除过度的诊断检查
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    
                    // 检查Activity是否可用
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        startActivity(intent);
                        android.util.Log.d("ProfileFragment", "LoginActivity started successfully");
                    } else {
                        android.util.Log.w("ProfileFragment", "Activity not available for starting LoginActivity");
                        Toast.makeText(getContext(), "无法启动登录页面，请重试", Toast.LENGTH_SHORT).show();
                    }
                    
                } catch (ActivityNotFoundException e) {
                    android.util.Log.e("ProfileFragment", "LoginActivity not found", e);
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), "登录页面未找到", Toast.LENGTH_SHORT).show();
                    }
                } catch (SecurityException e) {
                    android.util.Log.e("ProfileFragment", "Security exception when starting LoginActivity", e);
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), "权限不足，无法启动登录页面", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "Unexpected error when starting LoginActivity", e);
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), "启动登录页面时发生错误：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        // 成为会员按钮点击事件
        if (btnBecomeMember != null) {
            btnBecomeMember.setOnClickListener(v -> {
                try {
                    android.util.Log.d("ProfileFragment", "Become member button clicked");
                    
                    // 检查用户是否登录
                    if (!getLoginState()) {
                        showLoginDialog();
                        return;
                    }
                    
                    // 实现会员申请功能
                    showMemberApplication();
                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "打开会员申请页面失败: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "无法打开会员申请页面，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // 退出登录按钮 - 仅用于退出登录
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                if (getActivity() != null && !getActivity().isFinishing()) {
                    try {
                        performLogout();
                    } catch (Exception e) {
                        android.util.Log.e("ProfileFragment", "退出登录失败: " + e.getMessage(), e);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "退出登录失败，请重试", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }
        
        if (btnExpressOrders != null) {
            btnExpressOrders.setOnClickListener(v -> {
                if (getActivity() == null || getActivity().isFinishing()) {
                    return;
                }
                
                if (!getLoginState()) {
                    // 显示登录对话框
                    showLoginDialog();
                    return;
                }
                
                try {
                    Intent intent = new Intent(getActivity(), OrderActivity.class);
                    startActivity(intent);
                    android.util.Log.d("ProfileFragment", "启动OrderActivity");
                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "启动OrderActivity失败: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "无法打开订单页面，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (cardSettings != null) {
            cardSettings.setOnClickListener(v -> {
                if (getActivity() != null && !getActivity().isFinishing()) {
                    try {
                        Intent intent = new Intent(getActivity(), SettingsActivity.class);
                        startActivity(intent);
                        android.util.Log.d("ProfileFragment", "启动SettingsActivity");
                    } catch (Exception e) {
                        android.util.Log.e("ProfileFragment", "启动SettingsActivity失败: " + e.getMessage(), e);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "无法打开设置页面，请重试", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }
        
        // 我的预约点击事件
        if (cardMyAppointments != null) {
            cardMyAppointments.setOnClickListener(v -> {
                if (getActivity() == null || getActivity().isFinishing()) {
                    return;
                }
                
                if (!getLoginState()) {
                    // 显示登录对话框
                    showLoginDialog();
                    return;
                }
                
                try {
                    Intent intent = new Intent(getActivity(), MyAppointmentsActivity.class);
                    startActivity(intent);
                    android.util.Log.d("ProfileFragment", "启动MyAppointmentsActivity");
                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "启动MyAppointmentsActivity失败: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "无法打开预约页面，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // 我的处方点击事件
        if (cardMyPrescriptions != null) {
            cardMyPrescriptions.setOnClickListener(v -> {
                if (getActivity() == null || getActivity().isFinishing()) {
                    return;
                }
                
                if (!getLoginState()) {
                    // 显示登录对话框
                    showLoginDialog();
                    return;
                }
                
                try {
                    Intent intent = new Intent(getActivity(), MyPrescriptionsActivity.class);
                    startActivity(intent);
                    android.util.Log.d("ProfileFragment", "启动MyPrescriptionsActivity");
                    Toast.makeText(getActivity(), "启动我的处方成功", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "启动MyPrescriptionsActivity失败: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "无法打开处方页面，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // 健康档案点击事件
        if (cardHealthRecord != null) {
            cardHealthRecord.setOnClickListener(v -> {
                if (getActivity() == null || getActivity().isFinishing()) {
                    return;
                }
                
                if (!getLoginState()) {
                    // 显示登录对话框
                    showLoginDialog();
                    return;
                }
                
                try {
                    // 实现健康档案功能
                    showHealthRecord();
                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "打开健康档案失败: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "无法打开健康档案页面，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    /**
     * 显示健康档案功能
     */
    private void showHealthRecord() {
        android.util.Log.d("ProfileFragment", "打开健康档案功能");
        
        // 跳转到健康档案Activity
        Intent intent = new Intent(getActivity(), HealthRecordActivity.class);
        startActivity(intent);
    }
    
    /**
     * 显示登录对话框
     */
    private void showLoginDialog() {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        
        // 创建自定义对话框
        AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme)
                .create();
        
        // 确保对话框不为null
        if (dialog == null) {
            return;
        }
        
        // 使用LayoutInflater加载自定义布局
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View dialogView = inflater.inflate(R.layout.custom_login_dialog, null);
        
        // 设置对话框的视图
        dialog.setView(dialogView);
        
        // 设置对话框显示和消失的动画
        if (dialog.getWindow() != null) {
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
            
            // 设置对话框背景透明
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // 设置对话框大小
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(params);
        }
        
        // 获取自定义布局中的控件
        TextView tvTitle = dialogView.findViewById(R.id.dialog_title);
        TextView tvMessage = dialogView.findViewById(R.id.dialog_message);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnLogin = dialogView.findViewById(R.id.btn_login);
        
        // 设置控件内容
        if (tvTitle != null) {
            tvTitle.setText("提示");
        }
        
        if (tvMessage != null) {
            tvMessage.setText("请先登录");
        }
        
        // 设置取消按钮点击事件
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                android.util.Log.d("ProfileFragment", "用户取消登录");
            });
        }
        
        // 设置去登录按钮点击事件
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    startActivity(intent);
                    android.util.Log.d("ProfileFragment", "用户选择去登录");
                    dialog.dismiss();
                } catch (Exception e) {
                    android.util.Log.e("ProfileFragment", "启动LoginActivity失败: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "无法打开登录页面，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // 设置点击对话框外部可以取消
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        
        dialog.show();
        
        // 对话框显示后，设置按钮点击效果
        if (btnCancel != null) {
            btnCancel.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.7f);
                } else if (event.getAction() == MotionEvent.ACTION_UP || 
                           event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1.0f);
                }
                return false;
            });
        }
        
        if (btnLogin != null) {
            btnLogin.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.7f);
                } else if (event.getAction() == MotionEvent.ACTION_UP || 
                           event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1.0f);
                }
                return false;
            });
        }
    }
    
    /**
     * dp转px工具方法
     */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
    
    /**
     * 执行退出登录操作
     */
    private void performLogout() {
        // 清除登录状态
        saveLoginState(false, "");
        saveUserId(-1);
        updateLoginStatus();
        
        if (getActivity() != null) {
            Toast.makeText(getActivity(), "已成功退出登录", Toast.LENGTH_SHORT).show();
        }
        
        android.util.Log.d("ProfileFragment", "用户已退出登录");
    }
    
    /**
     * 保存登录状态到SharedPreferences
     */
    private void saveLoginState(boolean isLoggedIn, String username) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
                    .putString(KEY_USERNAME, username)
                    .apply();
            android.util.Log.d("ProfileFragment", "登录状态已保存: " + isLoggedIn + ", 用户名: " + username);
        }
    }

    /**
     * 保存用户ID到SharedPreferences
     */
    private void saveUserId(int userId) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putInt(USER_ID, userId)
                    .apply();
            android.util.Log.d("ProfileFragment", "用户ID已保存: " + userId);
        }
    }
    
    /**
     * 从SharedPreferences读取登录状态
     */
    private boolean getLoginState() {
        if (sharedPreferences != null) {
            return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        }
        return false;
    }
    
    /**
     * 从SharedPreferences读取用户名
     */
    private String getSavedUsername() {
        if (sharedPreferences != null) {
            return sharedPreferences.getString(KEY_USERNAME, "未登录");
        }
        return "未登录";
    }
    
    /**
     * 更新登录状态UI
     */
    private void updateLoginStatus() {
        boolean isLoggedIn = getLoginState();
        String username = getSavedUsername();
        
        if (isLoggedIn) {
            // 已登录状态
            tvUsername.setText(username);
            tvWelcome.setText("欢迎回来！");
            
            // 显示退出登录按钮，隐藏登录按钮
            if (btnLogin != null) {
                btnLogin.setVisibility(View.GONE);
            }
            if (btnLogout != null) {
                btnLogout.setVisibility(View.VISIBLE);
            }
        } else {
            // 未登录状态
            tvUsername.setText("未登录");
            tvWelcome.setText("请登录以使用完整功能");
            
            // 显示登录按钮，隐藏退出登录按钮
            if (btnLogin != null) {
                btnLogin.setVisibility(View.VISIBLE);
            }
            if (btnLogout != null) {
                btnLogout.setVisibility(View.GONE);
            }
        }
        
        // 检查当前是否为深色模式，并设置合适的文字颜色
        if (isDarkModeEnabled() && tvUsername != null) {
            // 深色模式下使用深色文字
            tvUsername.setTextColor(getResources().getColor(R.color.primary_color));
        } else if (tvUsername != null) {
            // 恢复默认文字颜色
            tvUsername.setTextColor(getResources().getColor(R.color.text_primary));
        }
    }
    
    /**
     * 处理从会员注册Activity返回的结果
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001) { // 与startActivityForResult中的请求码匹配
            if (resultCode == getActivity().RESULT_OK) {
                android.util.Log.d("ProfileFragment", "会员注册成功，更新UI状态");
                // 这里可以添加会员注册成功后的UI更新逻辑
                // 例如：显示会员标识、隐藏"成为会员"按钮等
                Toast.makeText(getContext(), "会员开通成功！", Toast.LENGTH_SHORT).show();
                // 检查并更新会员状态显示
                checkAndUpdateMemberStatus();
            }
        }
    }
    
    /**
     * 更新会员按钮可见性
     */
    private void updateMemberButtonVisibility() {
        if (btnBecomeMember != null) {
            btnBecomeMember.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 检查并更新会员状态显示
     */
    private void checkAndUpdateMemberStatus() {
        // 检查用户是否已经是会员
        boolean isMember = sharedPreferences.getBoolean("is_member", false);
        long expireTime = sharedPreferences.getLong("member_expire_time", 0);
        
        if (btnBecomeMember != null) {
            if (isMember && expireTime > System.currentTimeMillis()) {
                // 用户已是会员且在有效期内
                btnBecomeMember.setText("已开通会员");
                btnBecomeMember.setEnabled(false);
                // 可以添加更多UI更新，比如显示会员有效期等
            } else {
                // 用户不是会员或会员已过期
                btnBecomeMember.setText("成为会员");
                btnBecomeMember.setEnabled(true);
            }
        }
    }
    
    /**
     * 显示会员申请功能
     */
    private void showMemberApplication() {
        android.util.Log.d("ProfileFragment", "打开会员申请功能");
        
        try {
            // 跳转到会员注册Activity
            Intent intent = new Intent(getActivity(), MemberRegistrationActivity.class);
            
            // 检查Activity是否可用
            if (getActivity() != null && !getActivity().isFinishing()) {
                // 使用getActivity().startActivityForResult确保结果能正确返回
                getActivity().startActivityForResult(intent, 1001);
                android.util.Log.d("ProfileFragment", "MemberRegistrationActivity started successfully");
            } else {
                android.util.Log.w("ProfileFragment", "Activity not available for starting MemberRegistrationActivity");
                Toast.makeText(getContext(), "无法启动会员注册页面，请重试", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileFragment", "启动MemberRegistrationActivity失败: " + e.getMessage(), e);
            Toast.makeText(getContext(), "启动会员注册页面时发生错误", Toast.LENGTH_SHORT).show();
        }
    }
    

    
    /**
     * 检查当前是否启用了深色模式
     */
    private boolean isDarkModeEnabled() {
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 刷新登录状态UI
        updateLoginStatus();
        
        // 检查并更新会员状态显示
        checkAndUpdateMemberStatus();
    }
    
    /**
     * 模拟登录成功，保存登录状态
     * 在实际应用中，这个方法应该在LoginActivity登录成功后调用
     */
    public void simulateLoginSuccess(String username) {
        saveLoginState(true, username);
        updateLoginStatus();
        android.util.Log.d("ProfileFragment", "模拟登录成功: " + username);
    }

    /**
     * 模拟登录成功，保存登录状态和用户ID
     * 在实际应用中，这个方法应该在LoginActivity登录成功后调用
     */
    public void simulateLoginSuccess(String username, int userId) {
        saveLoginState(true, username);
        saveUserId(userId);
        updateLoginStatus();
        android.util.Log.d("ProfileFragment", "模拟登录成功: " + username + ", 用户ID: " + userId);
    }
}