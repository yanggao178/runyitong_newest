package com.wenxing.runyitong.utils;

/**
 * 应用常量类
 * 存储应用中使用的各种常量值
 */
public class Constants {
    // API相关常量
    public static final String BASE_URL = "http://localhost:8001/api/v1/";
    
    // SharedPreferences相关常量
    public static final String PREF_NAME = "RunYiTongPrefs";
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_TOKEN = "token";
    
    // 支付相关常量
    public static final String PAYMENT_TYPE_ALIPAY = "alipay";
    public static final String PAYMENT_TYPE_WECHAT = "wechat";
    
    // 订单状态常量
    public static final String ORDER_STATUS_PENDING_PAYMENT = "待支付";
    public static final String ORDER_STATUS_PAID = "已支付";
    public static final String ORDER_STATUS_PENDING_SHIPMENT = "待发货";
    public static final String ORDER_STATUS_SHIPPED = "待收货";
    public static final String ORDER_STATUS_COMPLETED = "已完成";
    public static final String ORDER_STATUS_CANCELLED = "已取消";
    
    // 错误码常量
    public static final int ERROR_NETWORK = 1001;
    public static final int ERROR_SERVER = 1002;
    public static final int ERROR_PAYMENT = 1003;
    
    // 其他常量
    public static final int MAX_UPLOAD_SIZE = 10 * 1024 * 1024; // 10MB
}