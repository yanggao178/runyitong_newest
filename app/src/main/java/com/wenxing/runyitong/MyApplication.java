package com.wenxing.runyitong;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.wenxing.runyitong.utils.CrashHandler;
import com.wenxing.runyitong.api.ApiClient;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;

/**
 * 自定义Application类
 * 用于全局初始化和配置
 */
public class MyApplication extends Application {
    
    private static final String TAG = "MyApplication";
    private static MyApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        instance = this;
        
        Log.d(TAG, "Application onCreate started");
        
        try {
            // 初始化ApiClient，传入ApplicationContext
            ApiClient.initialize(this);
            Log.d(TAG, "ApiClient initialized");

            // 初始化全局异常处理器
            CrashHandler.getInstance().init(this);
            Log.d(TAG, "CrashHandler initialized");

            // 初始化深色模式设置
            initializeDarkModeSetting();
            Log.d(TAG, "Dark mode setting initialized");

            // 记录应用启动信息
            logAppStartInfo();

            // 初始化百度语音识别SDK
            initializeBaiduSpeechSDK();

        } catch (Exception e) {
            Log.e(TAG, "Error in Application onCreate", e);
        }
        
        Log.d(TAG, "Application onCreate completed");
    }
    
    /**
     * 获取Application实例
     */
    public static MyApplication getInstance() {
        return instance;
    }
    
    /**
     * 初始化深色模式设置
     */
    private void initializeDarkModeSetting() {
        // 从SharedPreferences加载深色模式设置
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean darkModeEnabled = prefs.getBoolean("dark_mode_enabled", false);
        
        // 应用深色模式设置
        if (darkModeEnabled) {
            // 启用护眼模式（深色模式）
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            // 禁用护眼模式（浅色模式）
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    /**
     * 初始化百度语音识别SDK
     */
    private void initializeBaiduSpeechSDK() {
        try {
            Log.d(TAG, "Initializing Baidu Speech SDK");
            
            // 获取语音识别全局事件管理器
            EventManager asr = EventManagerFactory.create(this, "asr");
            
            // 注册一个简单的监听器来确认初始化成功
            asr.registerListener(new EventListener() {
                @Override
                public void onEvent(String name, String params, byte[] data, int offset, int length) {
                    // 这个监听器主要用于验证SDK是否正确初始化
                    Log.d(TAG, "Baidu Speech SDK event received: " + name);
                }
            });
            
            // 由于EventManagerFactory已经通过Manifest中的meta-data读取认证信息
            // 这里不需要额外设置认证参数
            
            Log.d(TAG, "Baidu Speech SDK initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Baidu Speech SDK", e);
        }
    }

    /**
     * 记录应用启动信息
     */
    private void logAppStartInfo() {
        try {
            // 记录内存信息
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            Log.d(TAG, "App Start Memory Info:");
            Log.d(TAG, "Used: " + (usedMemory / 1024 / 1024) + "MB");
            Log.d(TAG, "Free: " + (freeMemory / 1024 / 1024) + "MB");
            Log.d(TAG, "Total: " + (totalMemory / 1024 / 1024) + "MB");
            Log.d(TAG, "Max: " + (maxMemory / 1024 / 1024) + "MB");
            Log.d(TAG, "Usage: " + String.format("%.1f%%", (usedMemory * 100.0 / maxMemory)));
            
            // 记录设备信息
            Log.d(TAG, "Device: " + android.os.Build.BRAND + " " + android.os.Build.MODEL);
            Log.d(TAG, "Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "Error logging app start info", e);
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Application onLowMemory called");
        
        // 记录低内存时的状态
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        Log.w(TAG, "Low memory - Usage: " + (usedMemory / 1024 / 1024) + "MB / " + (maxMemory / 1024 / 1024) + "MB");
        
        // 强制垃圾回收
        System.gc();
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.w(TAG, "Application onTrimMemory called with level: " + level);
        
        // 根据不同级别采取不同的内存清理策略
        switch (level) {
            case TRIM_MEMORY_UI_HIDDEN:
                Log.d(TAG, "UI hidden, light memory cleanup");
                break;
            case TRIM_MEMORY_RUNNING_MODERATE:
                Log.d(TAG, "Running moderate, moderate memory cleanup");
                System.gc();
                break;
            case TRIM_MEMORY_RUNNING_LOW:
                Log.w(TAG, "Running low, aggressive memory cleanup");
                System.gc();
                break;
            case TRIM_MEMORY_RUNNING_CRITICAL:
                Log.e(TAG, "Running critical, emergency memory cleanup");
                System.gc();
                break;
            default:
                Log.d(TAG, "Memory trim level: " + level);
                break;
        }
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application onTerminate called");
    }


}