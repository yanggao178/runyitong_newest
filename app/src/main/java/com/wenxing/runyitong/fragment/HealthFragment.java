package com.wenxing.runyitong.fragment;

import com.wenxing.runyitong.adapter.VideoAdapter;
import com.wenxing.runyitong.activity.VideoPlayerActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.Button;
import android.view.View;
import android.content.Intent;
import com.wenxing.runyitong.model.Video;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wenxing.runyitong.R;
import com.wenxing.runyitong.adapter.BookshelfAdapter;

import com.wenxing.runyitong.model.Book;
import com.wenxing.runyitong.api.ApiClient;
import com.wenxing.runyitong.api.ApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.ImageView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;
import java.util.Locale;

public class HealthFragment extends Fragment {

    private RecyclerView chineseMedicineBooksRecyclerView;
    private RecyclerView westernMedicineBooksRecyclerView;
    private BookshelfAdapter chineseMedicineBookAdapter;
    private BookshelfAdapter westernMedicineBookAdapter;
    private List<Book> chineseMedicineBooks;
    private List<Book> westernMedicineBooks;
    private Call<ApiResponse<List<Book>>> chineseBooksCall; // 用于取消请求
    private Call<ApiResponse<List<Book>>> westernBooksCall; // 用于取消请求
    private boolean isDataLoaded = false; // 标记数据是否已加载
    
    // 视频选项卡相关组件
    private Button tabSelectedWorks; // 精选选项卡
    private Button tabBeginnerWorks; // 入门选项卡
    private RecyclerView videoRecyclerView; // 视频列表
    private List<Video> videos; // 视频数据列表
    private List<Video> originalVideos; // 原始视频数据（用于筛选）
    private String currentTab = "精选"; // 默认选中精选选项卡
    private Call<ApiResponse<List<Video>>> videosCall; // 用于取消视频请求
    
    // 搜索相关组件
    private EditText chineseMedicineSearchEditText;
    private EditText westernMedicineSearchEditText;
    private ImageView chineseMedicineClearSearch;
    private ImageView westernMedicineClearSearch;
    
    // 原始数据列表（用于搜索过滤）
    private List<Book> originalChineseMedicineBooks;
    private List<Book> originalWesternMedicineBooks;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("HealthFragment", "onCreateView called");
        
        try {
            View view = inflater.inflate(R.layout.fragment_health, container, false);
            if (view == null) {
                Log.e("HealthFragment", "Failed to inflate layout");
                return null;
            }
            
            Log.d("HealthFragment", "Layout inflated successfully");
            initViews(view);
            initData();
            Log.d("HealthFragment", "Fragment initialization completed");
            return view;
        } catch (Exception e) {
            Log.e("HealthFragment", "Error in onCreateView", e);
            return null;
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Log.d("HealthFragment", "onResume called");
        // 在Fragment恢复时检查是否需要加载数据
        if (!isDataLoaded) {
            loadBooksData();
        }
    }
    
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        Log.d("HealthFragment", "onHiddenChanged called, hidden=" + hidden);
        if (!hidden && !isDataLoaded) {
            // Fragment变为可见且数据未加载时，加载数据
            loadBooksData();
        }
    }
    
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        Log.d("HealthFragment", "setUserVisibleHint called, isVisibleToUser=" + isVisibleToUser);
        if (isVisibleToUser && !isDataLoaded) {
            // Fragment变为可见且数据未加载时，加载数据
            loadBooksData();
        }
    }

    private void initViews(View view) {
        chineseMedicineBooksRecyclerView = view.findViewById(R.id.chinese_medicine_books);
        westernMedicineBooksRecyclerView = view.findViewById(R.id.western_medicine_books);
        
        // 初始化视频相关组件
        videoRecyclerView = view.findViewById(R.id.video_recycler_view);
        tabSelectedWorks = view.findViewById(R.id.tab_selected_works);
        tabBeginnerWorks = view.findViewById(R.id.tab_beginner_works);
        
        // 初始化搜索组件
        chineseMedicineSearchEditText = view.findViewById(R.id.chinese_medicine_search);
        westernMedicineSearchEditText = view.findViewById(R.id.western_medicine_search);
        chineseMedicineClearSearch = view.findViewById(R.id.chinese_medicine_clear_search);
        westernMedicineClearSearch = view.findViewById(R.id.western_medicine_clear_search);
        
        // 设置网格布局管理器
        if (chineseMedicineBooksRecyclerView != null) {
            chineseMedicineBooksRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        }
        if (westernMedicineBooksRecyclerView != null) {
            westernMedicineBooksRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        }
        
        // 设置视频RecyclerView的布局管理器
        if (videoRecyclerView != null) {
            videoRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        }
        
        // 设置搜索功能
        setupSearchFunctionality();
        
        // 设置视频选项卡点击事件
        setupVideoTabClickListeners();
    }

    private void initData() {
        // 初始化空列表
        chineseMedicineBooks = new ArrayList<>();
        westernMedicineBooks = new ArrayList<>();
        originalChineseMedicineBooks = new ArrayList<>();
        originalWesternMedicineBooks = new ArrayList<>();
        
        // 初始化视频数据列表
        videos = new ArrayList<>();
        originalVideos = new ArrayList<>();
        
        // 立即创建书架适配器
        if (getContext() != null) {
            chineseMedicineBookAdapter = new BookshelfAdapter(getContext(), chineseMedicineBooks);
            westernMedicineBookAdapter = new BookshelfAdapter(getContext(), westernMedicineBooks);
            
            // 设置书籍点击监听器
            chineseMedicineBookAdapter.setOnBookClickListener(book -> {
                // 处理中医书籍点击事件
                handleBookClick(book, "中医古籍");
            });
            
            westernMedicineBookAdapter.setOnBookClickListener(book -> {
                // 处理西医书籍点击事件
                handleBookClick(book, "西医经典");
            });
            
            // 设置适配器
            if (chineseMedicineBooksRecyclerView != null) {
                chineseMedicineBooksRecyclerView.setAdapter(chineseMedicineBookAdapter);
            }
            if (westernMedicineBooksRecyclerView != null) {
                westernMedicineBooksRecyclerView.setAdapter(westernMedicineBookAdapter);
            }
            
            // 创建视频适配器
            VideoAdapter videoAdapter = new VideoAdapter(getContext(), videos);
            videoAdapter.setOnVideoClickListener(video -> {
                // 处理视频点击事件，跳转到播放界面
                Intent intent = new Intent(getActivity(), VideoPlayerActivity.class);
                intent.putExtra("video_url", video.getUrl());
                intent.putExtra("video_title", video.getTitle());
                startActivity(intent);
            });
            
            // 设置视频适配器
            if (videoRecyclerView != null) {
                videoRecyclerView.setAdapter(videoAdapter);
            }
        }
    }
    
    private void loadBooksData() {
        if (isDataLoaded) {
            Log.d("HealthFragment", "数据已加载，跳过重复加载");
            return;
        }
        
        Log.d("HealthFragment", "开始加载书籍数据");
        loadChineseMedicineBooksFromApi();
        loadWesternMedicineBooksFromApi();
        loadVideosFromApi(); // 加载视频数据
    }

    private void loadChineseMedicineBooksFromApi() {
        Log.d("HealthFragment", "开始请求中医书籍数据");

        // 取消之前的请求（避免重复请求）
        if (chineseBooksCall != null && !chineseBooksCall.isCanceled()) {
            chineseBooksCall.cancel();
        }

        chineseBooksCall = ApiClient.getApiService().getChineseMedicineBooks();
        Log.d("HealthFragment", "请求URL: " + chineseBooksCall.request().url());

        chineseBooksCall.enqueue(new Callback<ApiResponse<List<Book>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Book>>> call, Response<ApiResponse<List<Book>>> response) {
                Log.d("HealthFragment", "中医书籍API响应: " + response.code());

                // 1. 检查Fragment是否已分离
                if (!isFragmentActive()) {
                    Log.w("HealthFragment", "Fragment detached, ignoring response");
                    return;
                }

                // 2. 处理HTTP层面错误
                if (!response.isSuccessful()) {
                    handleHttpError(response);
                    return;
                }

                // 3. 检查响应体
                if (response.body() == null) {
                    Log.e("HealthFragment", "Empty response body");
                    showMessage("服务器返回空数据");
                    return;
                }

                ApiResponse<List<Book>> apiResponse = response.body();

                // 4. 处理业务逻辑错误
                if (!apiResponse.isSuccess()) {
                    Log.e("HealthFragment", "API Error: " + apiResponse.getMessage());
                    showMessage(apiResponse.getMessage() != null ?
                            apiResponse.getMessage() : "获取数据失败");
                    return;
                }

                // 5. 处理数据
                if (apiResponse.getData() == null || apiResponse.getData().isEmpty()) {
                    Log.w("HealthFragment", "No Chinese medicine books data");
                    showMessage("暂无中医古籍数据");
                    return;
                }

                handleChineseMedicineSuccessResponse(apiResponse.getData());
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Book>>> call, Throwable t) {
                Log.e("HealthFragment", "中医书籍API请求失败", t);

                if (!isFragmentActive()) {
                    return;
                }

                // 处理网络失败
                handleNetworkError(t);
            }
        });
    }
    
    private void loadWesternMedicineBooksFromApi() {
        Log.d("HealthFragment", "开始请求西医书籍数据");

        // 取消之前的请求（避免重复请求）
        if (westernBooksCall != null && !westernBooksCall.isCanceled()) {
            westernBooksCall.cancel();
        }

        westernBooksCall = ApiClient.getApiService().getWesternMedicineBooks();
        Log.d("HealthFragment", "请求URL: " + westernBooksCall.request().url());

        westernBooksCall.enqueue(new Callback<ApiResponse<List<Book>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Book>>> call, Response<ApiResponse<List<Book>>> response) {
                Log.d("HealthFragment", "西医书籍API响应: " + response.code());

                if (!isFragmentActive()) {
                    Log.w("HealthFragment", "Fragment detached, ignoring response");
                    return;
                }

                if (!response.isSuccessful()) {
                    handleHttpError(response);
                    return;
                }

                if (response.body() == null) {
                    Log.e("HealthFragment", "Empty response body");
                    showMessage("服务器返回空数据");
                    return;
                }

                ApiResponse<List<Book>> apiResponse = response.body();

                if (!apiResponse.isSuccess()) {
                    Log.e("HealthFragment", "API Error: " + apiResponse.getMessage());
                    showMessage(apiResponse.getMessage() != null ?
                            apiResponse.getMessage() : "获取数据失败");
                    return;
                }

                if (apiResponse.getData() == null || apiResponse.getData().isEmpty()) {
                    Log.w("HealthFragment", "No Western medicine books data");
                    showMessage("暂无西医经典数据");
                    return;
                }

                handleWesternMedicineSuccessResponse(apiResponse.getData());
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Book>>> call, Throwable t) {
                Log.e("HealthFragment", "西医书籍API请求失败", t);

                if (!isFragmentActive()) {
                    return;
                }

                handleNetworkError(t);
            }
        });
    }

// === 辅助方法 ===

    private boolean isFragmentActive() {
        return isAdded() && getActivity() != null && !getActivity().isFinishing();
    }

    private <T> void handleHttpError(Response<ApiResponse<T>> response) {
        try {
            String errorBody = response.errorBody() != null ?
                    response.errorBody().string() : "无错误详情";

            Log.e("HealthFragment", "HTTP Error: " + response.code() + ", Body: " + errorBody);

            switch (response.code()) {
                case 401:
                    showMessage("认证失败，请重新登录");
                    break;
                case 403:
                    showMessage("无权访问此资源");
                    break;
                case 404:
                    showMessage("资源不存在");
                    break;
                case 500:
                    showMessage("服务器内部错误");
                    break;
                default:
                    showMessage("请求失败，状态码: " + response.code());
            }
        } catch (IOException e) {
            Log.e("HealthFragment", "Error parsing error body", e);
            showMessage("网络请求失败");
        }
    }

    private void handleNetworkError(Throwable t) {
        String errorMsg = "加载失败，请检查网络";

        if (t instanceof SocketTimeoutException) {
            errorMsg = "请求超时，请重试";
        } else if (t instanceof ConnectException) {
            errorMsg = "无法连接服务器";
        } else if (t instanceof SSLHandshakeException) {
            errorMsg = "安全连接失败";
        } else if (t instanceof IOException) {
            errorMsg = "网络异常";
        }

        Log.e("HealthFragment", "Network Error: " + t.getClass().getSimpleName(), t);
        showMessage(errorMsg);
    }

    private void handleChineseMedicineSuccessResponse(List<Book> books) {
        Log.d("HealthFragment", "收到中医书籍数据: " + books.size() + " 本");
        for (int i = 0; i < Math.min(books.size(), 3); i++) {
            Book book = books.get(i);
            Log.d("HealthFragment", "书籍 " + i + ": " + book.getName() + " - " + book.getAuthor());
        }
        
        // 保存原始数据
        originalChineseMedicineBooks.clear();
        originalChineseMedicineBooks.addAll(books);
        
        // 更新显示数据
        chineseMedicineBooks.clear();
        chineseMedicineBooks.addAll(books);
        Log.d("HealthFragment", "中医书籍列表大小: " + chineseMedicineBooks.size());

        // 更新UI
        if (getActivity() != null && !getActivity().isFinishing()) {
            getActivity().runOnUiThread(() -> {
                if (chineseMedicineBookAdapter != null) {
                    chineseMedicineBookAdapter.notifyDataSetChanged();
                    Log.d("HealthFragment", "中医书籍适配器数据已更新");
                }
            });
        }

        showMessage(String.format(Locale.getDefault(), "已加载 %d 本中医古籍", books.size()));
    }
    
    private void handleWesternMedicineSuccessResponse(List<Book> books) {
        Log.d("HealthFragment", "收到西医书籍数据: " + books.size() + " 本");
        for (int i = 0; i < Math.min(books.size(), 3); i++) {
            Book book = books.get(i);
            Log.d("HealthFragment", "书籍 " + i + ": " + book.getName() + " - " + book.getAuthor());
        }
        
        // 保存原始数据
        originalWesternMedicineBooks.clear();
        originalWesternMedicineBooks.addAll(books);
        
        // 更新显示数据
        westernMedicineBooks.clear();
        westernMedicineBooks.addAll(books);
        Log.d("HealthFragment", "西医书籍列表大小: " + westernMedicineBooks.size());

        // 更新UI
        if (getActivity() != null && !getActivity().isFinishing()) {
            getActivity().runOnUiThread(() -> {
                if (westernMedicineBookAdapter != null) {
                    westernMedicineBookAdapter.notifyDataSetChanged();
                    Log.d("HealthFragment", "西医书籍适配器数据已更新");
                }
            });
        }

        showMessage(String.format(Locale.getDefault(), "已加载 %d 本西医经典", books.size()));
        
        // 标记数据已加载
        isDataLoaded = true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 取消网络请求
        if (chineseBooksCall != null) {
            chineseBooksCall.cancel();
        }
        if (westernBooksCall != null) {
            westernBooksCall.cancel();
        }
        // 重置数据加载状态
        isDataLoaded = false;
    }
    
    private void showMessage(String message) {
        Log.d("HealthFragment", message);
        if (getActivity() != null && !getActivity().isFinishing() && isAdded()) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    // === 搜索功能实现 ===
    
    private void setupSearchFunctionality() {
        // 设置中医搜索功能
        setupChineseMedicineSearch();
        
        // 设置西医搜索功能
        setupWesternMedicineSearch();
    }
    
    private void setupChineseMedicineSearch() {
        if (chineseMedicineSearchEditText == null || chineseMedicineClearSearch == null) return;
        
        // 搜索文本变化监听
        chineseMedicineSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                filterChineseMedicineBooks(query);
                
                // 显示/隐藏清除按钮
                chineseMedicineClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // 搜索按钮点击监听
        chineseMedicineSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = chineseMedicineSearchEditText.getText().toString().trim();
                filterChineseMedicineBooks(query);
                return true;
            }
            return false;
        });
        
        // 清除按钮点击监听
        chineseMedicineClearSearch.setOnClickListener(v -> {
            chineseMedicineSearchEditText.setText("");
            filterChineseMedicineBooks("");
            chineseMedicineClearSearch.setVisibility(View.GONE);
        });
    }
    
    private void setupWesternMedicineSearch() {
        if (westernMedicineSearchEditText == null || westernMedicineClearSearch == null) return;
        
        // 搜索文本变化监听
        westernMedicineSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                filterWesternMedicineBooks(query);
                
                // 显示/隐藏清除按钮
                westernMedicineClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // 搜索按钮点击监听
        westernMedicineSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = westernMedicineSearchEditText.getText().toString().trim();
                filterWesternMedicineBooks(query);
                return true;
            }
            return false;
        });
        
        // 清除按钮点击监听
        westernMedicineClearSearch.setOnClickListener(v -> {
            westernMedicineSearchEditText.setText("");
            filterWesternMedicineBooks("");
            westernMedicineClearSearch.setVisibility(View.GONE);
        });
    }
    
    private void filterChineseMedicineBooks(String query) {
        if (originalChineseMedicineBooks == null) return;
        
        List<Book> filteredBooks;
        if (query.isEmpty()) {
            // 如果搜索词为空，显示所有书籍
            filteredBooks = new ArrayList<>(originalChineseMedicineBooks);
        } else {
            // 过滤书籍
            filteredBooks = originalChineseMedicineBooks.stream()
                    .filter(book -> book.getName().toLowerCase().contains(query.toLowerCase()) ||
                                   book.getAuthor().toLowerCase().contains(query.toLowerCase()) ||
                                   (book.getDescription() != null && 
                                    book.getDescription().toLowerCase().contains(query.toLowerCase())))
                    .collect(Collectors.toList());
        }
        
        // 更新显示列表
        chineseMedicineBooks.clear();
        chineseMedicineBooks.addAll(filteredBooks);
        
        // 更新UI
        if (chineseMedicineBookAdapter != null) {
            chineseMedicineBookAdapter.notifyDataSetChanged();
        }
        
        Log.d("HealthFragment", "中医书籍搜索: '" + query + "', 结果: " + filteredBooks.size() + " 本");
    }
    
    private void filterWesternMedicineBooks(String query) {
        if (originalWesternMedicineBooks == null) return;
        
        List<Book> filteredBooks;
        if (query.isEmpty()) {
            // 如果搜索词为空，显示所有书籍
            filteredBooks = new ArrayList<>(originalWesternMedicineBooks);
        } else {
            // 过滤书籍
            filteredBooks = originalWesternMedicineBooks.stream()
                    .filter(book -> book.getName().toLowerCase().contains(query.toLowerCase()) ||
                                   book.getAuthor().toLowerCase().contains(query.toLowerCase()) ||
                                   (book.getDescription() != null && 
                                    book.getDescription().toLowerCase().contains(query.toLowerCase())))
                    .collect(Collectors.toList());
        }
        
        // 更新显示列表
        westernMedicineBooks.clear();
        westernMedicineBooks.addAll(filteredBooks);
        
        // 更新UI
        if (westernMedicineBookAdapter != null) {
            westernMedicineBookAdapter.notifyDataSetChanged();
        }
        
        Log.d("HealthFragment", "西医书籍搜索: '" + query + "', 结果: " + filteredBooks.size() + " 本");
    }
    
    /**
     * 从API加载视频数据
     */
    private void loadVideosFromApi() {
        Log.d("HealthFragment", "开始请求视频数据");

        // 取消之前的请求（避免重复请求）
        if (videosCall != null && !videosCall.isCanceled()) {
            videosCall.cancel();
        }

        videosCall = ApiClient.getApiService().getVideos();
        Log.d("HealthFragment", "请求视频URL: " + videosCall.request().url());

        videosCall.enqueue(new Callback<ApiResponse<List<Video>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Video>>> call, Response<ApiResponse<List<Video>>> response) {
                Log.d("HealthFragment", "视频API响应: " + response.code());

                if (!isFragmentActive()) {
                    Log.w("HealthFragment", "Fragment detached, ignoring response");
                    return;
                }

                if (!response.isSuccessful()) {
                    handleHttpError(response);
                    return;
                }

                if (response.body() == null) {
                    Log.e("HealthFragment", "Empty response body");
                    showMessage("服务器返回空数据");
                    return;
                }

                ApiResponse<List<Video>> apiResponse = response.body();

                if (!apiResponse.isSuccess()) {
                    Log.e("HealthFragment", "API Error: " + apiResponse.getMessage());
                    showMessage(apiResponse.getMessage() != null ?
                            apiResponse.getMessage() : "获取数据失败");
                    return;
                }

                if (apiResponse.getData() == null || apiResponse.getData().isEmpty()) {
                    Log.w("HealthFragment", "No videos data");
                    showMessage("暂无视频数据");
                    return;
                }

                handleVideosSuccessResponse(apiResponse.getData());
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Video>>> call, Throwable t) {
                Log.e("HealthFragment", "视频API请求失败", t);

                if (!isFragmentActive()) {
                    return;
                }

                handleNetworkError(t);
            }
        });
    }

    /**
     * 处理视频数据成功响应
     */
    private void handleVideosSuccessResponse(List<Video> videoList) {
        Log.d("HealthFragment", "收到视频数据: " + videoList.size() + " 个");
        
        // 保存原始数据
        originalVideos.clear();
        originalVideos.addAll(videoList);
        
        // 初始化时默认显示精选视频
        videos.clear();
        videos.addAll(videoList);
        Log.d("HealthFragment", "视频列表大小: " + videos.size());

        // 更新UI
        if (getActivity() != null && !getActivity().isFinishing()) {
            getActivity().runOnUiThread(() -> {
                if (videoRecyclerView != null && videoRecyclerView.getAdapter() != null) {
                    videoRecyclerView.getAdapter().notifyDataSetChanged();
                    Log.d("HealthFragment", "视频适配器数据已更新");
                    
                    // 强制RecyclerView重新测量高度
                    videoRecyclerView.post(() -> {
                        videoRecyclerView.invalidate();
                        videoRecyclerView.requestLayout();
                    });
                }
            });
        }

        // 默认选中精选选项卡
        setActiveTab("精选");
    }

    /**
     * 设置视频选项卡点击事件监听器
     */
    private void setupVideoTabClickListeners() {
        if (tabSelectedWorks != null) {
            tabSelectedWorks.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setActiveTab("精选");
                    filterVideosByTab("精选");
                }
            });
        }
        
        if (tabBeginnerWorks != null) {
            tabBeginnerWorks.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setActiveTab("入门");
                    filterVideosByTab("入门");
                }
            });
        }
    }

    /**
     * 设置当前活动选项卡的样式
     */
    private void setActiveTab(String tabName) {
        currentTab = tabName;
        
        if (tabSelectedWorks != null && tabBeginnerWorks != null) {
            if ("精选".equals(tabName)) {
                // 设置精选选项卡为激活状态
                tabSelectedWorks.setBackgroundResource(R.drawable.tab_button_selected);
                tabSelectedWorks.setTextColor(Color.WHITE);
                tabSelectedWorks.setElevation(0);
                
                // 设置入门选项卡为非激活状态
                tabBeginnerWorks.setBackgroundResource(android.R.color.transparent);
                tabBeginnerWorks.setTextColor(getResources().getColor(R.color.primary_color));
                tabBeginnerWorks.setElevation(0);
            } else if ("入门".equals(tabName)) {
                // 设置入门选项卡为激活状态
                tabBeginnerWorks.setBackgroundResource(R.drawable.tab_button_selected);
                tabBeginnerWorks.setTextColor(Color.WHITE);
                tabBeginnerWorks.setElevation(0);
                
                // 设置精选选项卡为非激活状态
                tabSelectedWorks.setBackgroundResource(android.R.color.transparent);
                tabSelectedWorks.setTextColor(getResources().getColor(R.color.primary_color));
                tabSelectedWorks.setElevation(0);
            }
        }
    }

    /**
     * 根据选项卡筛选视频
     */
    private void filterVideosByTab(String tabName) {
        Log.d("HealthFragment", "切换到视频选项卡: " + tabName);
        
        if (originalVideos != null && !originalVideos.isEmpty()) {
            List<Video> filteredVideos = new ArrayList<>();
            
            for (Video video : originalVideos) {
                // 根据视频的标签或属性进行筛选
                if ("入门".equals(tabName) && (video.getTitle().contains("基础") || 
                                              video.getTitle().contains("入门") ||
                                              video.getTags() != null && video.getTags().contains("入门"))) {
                    filteredVideos.add(video);
                } else if ("精选".equals(tabName) && !video.getTitle().contains("基础") && 
                           !video.getTitle().contains("入门") &&
                           (video.getTags() == null || !video.getTags().contains("入门"))) {
                    filteredVideos.add(video);
                } else if ("精选".equals(tabName) && filteredVideos.isEmpty()) {
                    // 如果精选筛选结果为空，则显示所有视频
                    filteredVideos = new ArrayList<>(originalVideos);
                    break;
                }
            }
            
            videos.clear();
            videos.addAll(filteredVideos);
            
            // 更新UI
            if (videoRecyclerView != null && videoRecyclerView.getAdapter() != null) {
                videoRecyclerView.getAdapter().notifyDataSetChanged();
                // 强制RecyclerView重新测量高度
                videoRecyclerView.post(() -> {
                    videoRecyclerView.invalidate();
                    videoRecyclerView.requestLayout();
                });
            }
        }
    }
    
    /**
     * 处理书籍点击事件
     * @param book 被点击的书籍
     * @param category 书籍分类
     */
    private void handleBookClick(Book book, String category) {
        Log.d("HealthFragment", "书籍点击: " + book.getName() + " (" + category + ")");
        
        // 设置书籍分类
        if (book.getCategory() == null || book.getCategory().isEmpty()) {
            book.setCategory(category);
        }
        
        // 跳转到书籍详情页面，直接传递完整的Book对象
        Intent intent = new Intent(getActivity(), com.wenxing.runyitong.activity.BookDetailActivity.class);
        intent.putExtra("book", book);
        
        startActivity(intent);
        
        Log.d("HealthFragment", "启动书籍详情页面，书籍ID: " + book.getId());
    }
}