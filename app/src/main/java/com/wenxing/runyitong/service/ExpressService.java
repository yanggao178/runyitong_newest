package com.wenxing.runyitong.service;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wenxing.runyitong.api.ApiClient;
import com.wenxing.runyitong.api.ApiResponse;
import com.wenxing.runyitong.api.ApiService;
import com.wenxing.runyitong.model.ExpressCompany;
import com.wenxing.runyitong.model.ExpressOrderResult;
import com.wenxing.runyitong.model.TrackingInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 快递服务类，封装快递相关的业务逻辑
 */
public class ExpressService {
    private static final String TAG = "ExpressService";
    private static ExpressService instance;
    private ApiService apiService;
    private Gson gson;
    private Map<String, ExpressCompany> expressCompaniesCache;

    private ExpressService(Context context) {
        this.apiService = ApiClient.getApiService();
        this.gson = new Gson();
        this.expressCompaniesCache = new HashMap<>();
    }

    /**
     * 获取快递服务单例
     */
    public static synchronized ExpressService getInstance(Context context) {
        if (instance == null) {
            instance = new ExpressService(context);
        }
        return instance;
    }

    /**
     * 获取快递公司列表
     */
    public void getExpressCompanies(final ApiCallback<Map<String, ExpressCompany>> callback) {
        // 如果有缓存且不为空，直接返回缓存数据
        if (!expressCompaniesCache.isEmpty()) {
            callback.onSuccess(expressCompaniesCache);
            return;
        }

        Call<ApiResponse<Map<String, ExpressCompany>>> call = apiService.getExpressCompanies();
        call.enqueue(new Callback<ApiResponse<Map<String, ExpressCompany>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, ExpressCompany>>> call,
                                   Response<ApiResponse<Map<String, ExpressCompany>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Map<String, ExpressCompany>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        expressCompaniesCache.putAll(apiResponse.getData());
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        callback.onError(apiResponse.getMessage() != null ? apiResponse.getMessage() : "获取快递公司列表失败");
                    }
                } else {
                    String errorMessage = "网络请求失败";
                    try {
                        if (response.errorBody() != null) {
                            errorMessage = response.errorBody().string();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, ExpressCompany>>> call, Throwable t) {
                Log.e(TAG, "Get express companies failed", t);
                // 网络请求失败时，提供默认的常用物流公司
                Map<String, ExpressCompany> defaultCompanies = getDefaultExpressCompanies();
                expressCompaniesCache.putAll(defaultCompanies);
                callback.onSuccess(defaultCompanies);
                // 依然显示错误消息，但不中断流程
                Log.e(TAG, "获取快递公司列表失败: " + t.getMessage());
            }
        });
    }

    /**
     * 查询物流轨迹
     */
    public void trackExpressOrder(String orderSn, String shipperCode, String senderPhone, final ApiCallback<TrackingInfo> callback) {
        try {
            Call<ApiResponse<TrackingInfo>> call = apiService.trackExpressOrder(orderSn, shipperCode, senderPhone);
            call.enqueue(new Callback<ApiResponse<TrackingInfo>>() {
                @Override
                public void onResponse(Call<ApiResponse<TrackingInfo>> call, Response<ApiResponse<TrackingInfo>> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            ApiResponse<TrackingInfo> apiResponse = response.body();
                            if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                                callback.onSuccess(apiResponse.getData());
                            } else {
                                callback.onError(apiResponse.getMessage() != null ? apiResponse.getMessage() : "查询物流轨迹失败");
                            }
                        } else {
                            String errorMessage = "网络请求失败";  
                            try {
                                if (response.errorBody() != null) {
                                    errorMessage = response.errorBody().string();
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Error parsing error response", e);
                            }
                            callback.onError(errorMessage);
                        }
                    } catch (Exception e) {
                        // 捕获JSON解析异常或其他处理响应时出现的异常
                        Log.e(TAG, "Error processing tracking response", e);
                        callback.onError("解析物流数据失败: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<TrackingInfo>> call, Throwable t) {
                    Log.e(TAG, "Track express order failed", t);
                    callback.onError("查询物流轨迹失败: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            // 捕获请求创建过程中可能出现的异常
            Log.e(TAG, "Error creating tracking request", e);
            callback.onError("创建物流查询请求失败: " + e.getMessage());
        }
    }

    /**
     * 为订单创建快递单
     */
    public void createShippingForOrder(String orderId, Map<String, Object> shippingInfo,
                                      final ApiCallback<ExpressOrderResult> callback) {
        // 构造JSON请求体
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, Object> entry : shippingInfo.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                jsonObject.addProperty(entry.getKey(), (String) value);
            } else if (value instanceof Number) {
                jsonObject.addProperty(entry.getKey(), (Number) value);
            } else if (value instanceof Boolean) {
                jsonObject.addProperty(entry.getKey(), (Boolean) value);
            }
        }

        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonObject.toString()
        );

        Call<ApiResponse<ExpressOrderResult>> call = apiService.createShippingForOrder(orderId, requestBody);
        call.enqueue(new Callback<ApiResponse<ExpressOrderResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<ExpressOrderResult>> call,
                                   Response<ApiResponse<ExpressOrderResult>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<ExpressOrderResult> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        callback.onSuccess(apiResponse.getData());
                    } else {
                        callback.onError(apiResponse.getMessage() != null ? apiResponse.getMessage() : "创建快递单失败");
                    }
                } else {
                    String errorMessage = "网络请求失败";
                    try {
                        if (response.errorBody() != null) {
                            errorMessage = response.errorBody().string();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ExpressOrderResult>> call, Throwable t) {
                Log.e(TAG, "Create shipping failed", t);
                callback.onError("创建快递单失败: " + t.getMessage());
            }
        });
    }

    /**
     * 构造快递信息参数
     */
    public Map<String, Object> buildShippingInfo(String shipperCode, String receiverName,
                                               String receiverPhone, String receiverProvince,
                                               String receiverCity, String receiverDistrict,
                                               String receiverAddress) {
        Map<String, Object> shippingInfo = new HashMap<>();
        shippingInfo.put("shipper_code", shipperCode);
        shippingInfo.put("receiver_name", receiverName);
        shippingInfo.put("receiver_phone", receiverPhone);
        shippingInfo.put("receiver_province", receiverProvince);
        shippingInfo.put("receiver_city", receiverCity);
        shippingInfo.put("receiver_district", receiverDistrict);
        shippingInfo.put("receiver_address", receiverAddress);
        return shippingInfo;
    }

    /**
     * 获取默认的常用物流公司列表
     * 当网络请求失败或无响应时使用
     */
    private Map<String, ExpressCompany> getDefaultExpressCompanies() {
        Map<String, ExpressCompany> defaultCompanies = new HashMap<>();
        
        // 添加常用快递公司
        defaultCompanies.put("SF", new ExpressCompany("顺丰速运", "SF"));
        defaultCompanies.put("YZPY", new ExpressCompany("中国邮政", "YZPY"));
        defaultCompanies.put("JD", new ExpressCompany("京东物流", "JD"));
        defaultCompanies.put("ZTO", new ExpressCompany("中通快递", "ZTO"));
        defaultCompanies.put("YTO", new ExpressCompany("圆通速递", "YTO"));
        defaultCompanies.put("YD", new ExpressCompany("韵达快递", "YD"));
        defaultCompanies.put("STO", new ExpressCompany("申通快递", "STO"));
        defaultCompanies.put("HTKY", new ExpressCompany("百世快递", "HTKY"));
        defaultCompanies.put("TTT", new ExpressCompany("天天快递", "TTT"));
        defaultCompanies.put("ZJS", new ExpressCompany("宅急送", "ZJS"));
        defaultCompanies.put("JT", new ExpressCompany("极兔速递", "JTSD"));
        defaultCompanies.put("YMDD", new ExpressCompany("壹米滴答", "YMDD"));
        return defaultCompanies;
    }
    
    /**
     * API回调接口
     */
    public interface ApiCallback<T> {
        void onSuccess(T data);
        void onError(String errorMessage);
    }
}