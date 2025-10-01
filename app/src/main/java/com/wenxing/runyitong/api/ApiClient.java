package com.wenxing.runyitong.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;
import okhttp3.Protocol;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Locale;
import java.io.IOException;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

public class ApiClient {
    // 根据运行环境选择合适的服务器地址
    private static final String BASE_URL = getBaseUrl();
    private static Context appContext;
    
    public static String getBaseUrl() {
        // 优先使用localhost进行本地测试
        //
        
        // 如果需要根据环境切换，可以使用以下代码：
        
        String fingerprint = android.os.Build.FINGERPRINT;
        // Android Emulator (Android Studio) maps host localhost to 10.0.2.2
        if (fingerprint != null && (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || fingerprint.contains("emu64"))) {
            android.util.Log.i("ApiClient", "Detected Android emulator - using 10.0.2.2 to reach host localhost");
            return "http://10.0.2.2:8000/";
        }

        // Genymotion emulator maps host localhost to 10.0.3.2
        
        // Fallback for real devices on the same LAN. Ensure backend binds to 0.0.0.0 and firewall allows access.
        android.util.Log.i("ApiClient", "Using LAN IP for backend: 192.168.0.5:8000 (ensure this is reachable)");
        return "http://192.168.0.3:8000/";
        // return "http://8.141.123.89:8000/";
    }
    private static Retrofit retrofit = null;
    private static ApiService apiService = null;
    
    /**
     * 初始化ApiClient，需要传入ApplicationContext
     */
    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
    }

    public static Context getAppContext() {
        return appContext;
    }

    /**
     * 从SharedPreferences获取access_token（公开实例方法）
     */
    public String getAccessToken() {
        return getAccessTokenStatic();
    }
    
    /**
     * 从SharedPreferences获取access_token（私有静态方法，供内部使用）
     */
    private static String getAccessTokenStatic() {
        if (appContext == null) {
            Log.w("ApiClient", "App context is null when trying to get access token");
            return null;
        }
        
        try {
            // 直接使用appContext变量，避免额外的方法调用
            SharedPreferences sharedPreferences = appContext.getSharedPreferences("user_login_state", Context.MODE_PRIVATE);
            String strAccessToken = sharedPreferences.getString("access_token", null);
            Log.d("ApiClient", "Access token retrieved: " + (strAccessToken != null ? "available" : "null"));
            return strAccessToken;
        } catch (Exception e) {
            Log.e("ApiClient", "Failed to get access token: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 获取Retrofit实例
     */
    public static Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            // 创建日志拦截器，针对大文件下载进行优化
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            // 默认使用BASIC级别，不会记录响应体内容
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
            
            // 创建针对下载请求的特殊拦截器，防止响应体被加载到内存
            Interceptor downloadInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request request = chain.request();
                    // 检测是否是下载请求
                    boolean isDownloadRequest = request.url().toString().contains("download") || 
                            request.header("Accept") != null && request.header("Accept").contains("application/octet-stream");
                    
                    if (isDownloadRequest) {
                        // 确保添加适当的头部，指示服务器返回二进制流
                        Request downloadRequest = request.newBuilder()
                                .header("Accept", "application/octet-stream")
                                .header("Connection", "keep-alive")
                                .build();
                        
                        return chain.proceed(downloadRequest);
                    }
                    
                    return chain.proceed(request);
                }
            };
            
            // 创建重试拦截器，特别针对大文件下载的连接中断错误进行优化
            Interceptor retryInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request request = chain.request();
                    Response response = null;
                    IOException exception = null;
                    
                    // 针对大文件下载的最大重试次数增加到5次
                    int maxRetries = 5;
                    
                    // 检查是否是下载请求（URL中包含download或返回类型为流）
                    boolean isDownloadRequest = request.url().toString().contains("download") || 
                            request.header("Accept") != null && request.header("Accept").contains("application/octet-stream");
                    
                    for (int i = 0; i < maxRetries; i++) {
                        try {
                            response = chain.proceed(request);
                            
                            // 如果响应成功或者是客户端错误（4xx），不需要重试
                            if (response.isSuccessful() || (response.code() >= 400 && response.code() < 500)) {
                                return response;
                            }
                            
                            // 服务器错误（5xx）或网络错误，进行重试
                            if (i < maxRetries - 1) {
                                Log.w("ApiClient", "Request failed, retrying... (" + (i + 1) + "/" + maxRetries + ")");
                                response.close();
                                
                                // 等待一段时间再重试（指数退避策略）
                                try {
                                    long waitTime = calculateBackoffTime(i, isDownloadRequest);
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("Request interrupted", e);
                                }
                            }
                        } catch (IOException e) {
                            exception = e;
                            
                            // 特别处理大文件下载时的连接中止错误
                            boolean isConnectionAbortError = e.getMessage() != null && 
                                    (e.getMessage().contains("Software caused connection abort") || 
                                     e.getMessage().contains("connection abort"));
                            
                            // 特别处理大文件下载时的错误
                            if (isDownloadRequest && isConnectionAbortError) {
                                Log.e("ApiClient", "Connection aborted during large file download, attempting retry (" + (i + 1) + "/" + maxRetries + ")", e);
                                
                                // 大文件下载连接中断需要更长的恢复时间
                                if (i < maxRetries - 1) {
                                    // 对于大文件下载的连接中断，使用更长的延迟时间
                                    try {
                                        Thread.sleep(3000 + (i * 1000)); // 3秒基础延迟，每次增加1秒
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        throw new IOException("Request interrupted", ie);
                                    }
                                }
                            } else if (i < maxRetries - 1) {
                                Log.w("ApiClient", "Network error, retrying... (" + (i + 1) + "/" + maxRetries + "): " + e.getMessage());
                                
                                // 普通网络错误使用标准退避策略
                                try {
                                    long waitTime = calculateBackoffTime(i, isDownloadRequest);
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("Request interrupted", ie);
                                }
                            }
                        }
                    }
                    
                    // 如果所有重试都失败了
                    if (response != null) {
                        return response;
                    } else if (exception != null) {
                        throw exception;
                    } else {
                        throw new IOException("Unknown error occurred");
                    }
                }
                
                /**
                 * 计算退避时间，针对下载请求使用更长的延迟
                 */
                private long calculateBackoffTime(int retryCount, boolean isDownloadRequest) {
                    // 基础延迟时间：普通请求1秒，下载请求2秒
                    long baseDelay = isDownloadRequest ? 2000 : 1000;
                    // 指数退避，最大延迟限制
                    return Math.min((long) (baseDelay * Math.pow(2, retryCount)), 
                            isDownloadRequest ? 10000 : 5000);
                }
            };
            
            // 创建认证拦截器
            Interceptor authInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request originalRequest = chain.request();
                    
                    // 获取access_token
                    String accessToken = getAccessTokenStatic();
                    
                    // 如果有token，添加Authorization头
                    if (accessToken != null && !accessToken.isEmpty()) {
                        Request authorizedRequest = originalRequest.newBuilder()
                                .header("Authorization", "Bearer " + accessToken)
                                .build();
                        return chain.proceed(authorizedRequest);
                    }
                    
                    // 没有token，直接请求
                    return chain.proceed(originalRequest);
                }
            };
            
            // 配置连接池
            ConnectionPool connectionPool = new ConnectionPool(
                    10,  // 最大空闲连接数
                    5, TimeUnit.MINUTES  // 连接最大空闲时间
            );
            
            // 配置TLS和连接规范，增强安全性和兼容性
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                    .build();
            
            // 创建OkHttpClient，增强网络连接稳定性，特别优化大文件下载
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)  // 添加认证拦截器
                    .addInterceptor(downloadInterceptor)  // 添加下载特殊处理拦截器
                    .addInterceptor(retryInterceptor)
                    .addInterceptor(loggingInterceptor)
                    // 为大文件下载调整超时设置
                    .connectTimeout(300, TimeUnit.SECONDS)  // 增加连接超时时间到5分钟
                    .readTimeout(1800, TimeUnit.SECONDS)    // 增加读取超时时间到30分钟，适合大文件下载
                    .writeTimeout(600, TimeUnit.SECONDS)    // 增加写入超时时间到10分钟
                    // 网络连接优化设置
                    .connectionPool(connectionPool)  // 使用自定义连接池
                    .retryOnConnectionFailure(true)  // 自动重试连接失败的请求
                    .pingInterval(30, TimeUnit.SECONDS)  // 设置ping间隔，保持连接活跃
                    .connectionSpecs(Arrays.asList(spec, ConnectionSpec.CLEARTEXT))  // 配置连接规范
                    // 针对大文件下载的额外优化
                    .followRedirects(true)  // 跟随重定向
                    .followSslRedirects(true)  // 跟随SSL重定向
                    .protocols(Arrays.asList(Protocol.HTTP_1_1))  // 使用HTTP/1.1协议，对大文件更稳定
                    .build();
            
            // 创建自定义Gson实例处理日期格式
            Gson gson = new GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
                    .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, typeOfT, context) -> {
                        try {
                            String dateString = json.getAsString();
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault());
                            return format.parse(dateString);
                        } catch (ParseException e) {
                            try {
                                // 尝试另一种格式
                                String dateString = json.getAsString();
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
                                return format.parse(dateString);
                            } catch (ParseException ex) {
                                return null;
                            }
                        }
                    })
                    .create();
            
            // 创建Retrofit实例
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit;
    }
    
    /**
     * 获取ApiClient实例
     */
    public static ApiClient getInstance() {
        return new ApiClient();
    }
    
    /**
     * 获取API服务实例
     */
    public static ApiService getApiService() {
        if (apiService == null) {
            apiService = getRetrofitInstance().create(ApiService.class);
        }
        return apiService;
    }
    
    /**
     * 获取支付服务实例（与API服务相同）
     */
    public static ApiService getPaymentService() {
        return getApiService();
    }
}