package com.wenxing.runyitong.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.adapter.BookPageAdapter;
import com.wenxing.runyitong.adapter.DownloadedBookAdapter;
import com.wenxing.runyitong.api.ApiClient;
import com.wenxing.runyitong.model.Book;
import com.wenxing.runyitong.model.BookPage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

// iTextG 5.x imports
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import retrofit2.Call;
import retrofit2.Response;
import okhttp3.ResponseBody;
import java.io.InputStream;

import com.wenxing.runyitong.api.ApiService;
import okhttp3.Callback;

public class BookDetailActivity extends AppCompatActivity {
    private static final String TAG = "BookDetailActivity";
    private static final int PAGES_PER_REQUEST = 10;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1001;
    
    private Book book;
    private List<BookPage> bookPages;
    private BookPageAdapter pageAdapter;
    private List<File> downloadedBooks;
    private DownloadedBookAdapter downloadedBookAdapter;
    private ApiService apiService;
    
    private ImageView bookCoverImageView;
    private TextView bookTitleTextView;
    private TextView bookAuthorTextView;
    private TextView bookDescriptionTextView;
    private RecyclerView pagesRecyclerView;
    private ProgressBar loadingProgressBar;
    private Button loadMoreButton;
    private Button downloadButton;
    private TextView pageInfoTextView;
    
    private int currentPage = 1;
    private int totalPages = 0;
    private boolean isLoading = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);
        
        Intent intent = getIntent();
        if (intent.hasExtra("book")) {
            book = (Book) intent.getSerializableExtra("book");
        } else if (intent.hasExtra("book_id")) {
            // 从HealthFragment传递的参数构建完整的Book对象
            book = new Book();
            book.setId(intent.getIntExtra("book_id", -1));
            book.setName(intent.getStringExtra("book_name"));
            book.setAuthor(intent.getStringExtra("book_author"));
            book.setDescription(intent.getStringExtra("book_description"));
            book.setCoverUrl(intent.getStringExtra("book_cover_url"));
            
            // 添加完整参数
            book.setCategory(intent.getStringExtra("book_category"));
            
            // 设置日期信息
            Date currentDate = new Date();
            book.setPublishDate(currentDate);
            book.setCreatedTime(currentDate);
            book.setUpdatedTime(currentDate);
            
            // 设置PDF文件路径和大小
            if (intent.hasExtra("book_pdf_file_path")) {
                book.setPdfFilePath(intent.getStringExtra("book_pdf_file_path"));
            } else {
                // 默认PDF路径
                book.setPdfFilePath("/books/" + intent.getStringExtra("book_name") + ".pdf");
            }
            
            if (intent.hasExtra("book_file_size")) {
                book.setFileSize(intent.getIntExtra("book_file_size", 0));
            } else {
                // 默认文件大小：5MB
                book.setFileSize(5242880);
            }
        } else {
            // 创建完整的测试书籍
            book = new Book();
            book.setId(-1);
            book.setName("中医基础理论");
            book.setAuthor("张三");
            book.setCategory("中医理论");
            book.setDescription("本书系统介绍中医基础理论，包括阴阳五行、脏腑经络、气血津液等核心概念，适合中医初学者阅读。内容涵盖中医诊断方法、辨证论治原则以及常见病症的中医治疗思路。");
            book.setCoverUrl("https://example.com/covers/tcm_basic_theory.jpg");
            book.setPublishDate(new Date());
            book.setCreatedTime(new Date());
            book.setUpdatedTime(new Date());
            book.setPdfFilePath("/books/sample_book.pdf");
            book.setFileSize(10485760); // 10MB
        }
        
        initViews();
        setupRecyclerView();
        displayBookInfo();
        loadDownloadedBooks();
        
        // 初始化API服务
        apiService = ApiClient.getApiService();
        
        // 如果没有已下载的书籍，创建示例书籍
        if (downloadedBooks.isEmpty()) {
            copyExampleBooks();
        }
    }
    
    private void initViews() {
        bookCoverImageView = findViewById(R.id.book_cover_detail);
        bookTitleTextView = findViewById(R.id.book_title_detail);
        bookAuthorTextView = findViewById(R.id.book_author_detail);
        bookDescriptionTextView = findViewById(R.id.book_description_detail);
        pagesRecyclerView = findViewById(R.id.pages_recycler_view);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);
        loadMoreButton = findViewById(R.id.load_more_button);
        downloadButton = findViewById(R.id.download_button);
        pageInfoTextView = findViewById(R.id.page_info_text);
        
        loadMoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadNextPage();
            }
        });
        
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload();
            }
        });
    }
    
    private void setupRecyclerView() {
        bookPages = new ArrayList<>();
        pageAdapter = new BookPageAdapter(this, bookPages);
        
        downloadedBooks = new ArrayList<>();
        downloadedBookAdapter = new DownloadedBookAdapter(this, downloadedBooks);
        
        // 设置下载书籍的点击监听器
        downloadedBookAdapter.setOnBookClickListener(new DownloadedBookAdapter.OnBookClickListener() {
            @Override
            public void onBookClick(File bookFile) {
                try {
                    if (bookFile == null) {
                        Log.e(TAG, "Book file is null");
                        Toast.makeText(BookDetailActivity.this, "书籍文件无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Log.d(TAG, "Book clicked: " + bookFile.getAbsolutePath());
                    
                    // 点击已下载的书籍时，打开PDF阅读器
                    String fileName = bookFile.getName();
                    String bookTitle = fileName;
                    if (fileName.endsWith(".pdf")) {
                        bookTitle = fileName.substring(0, fileName.length() - 4);
                    }
                    
                    Log.d(TAG, "Opening PDF with title: " + bookTitle);
                    openPDFReader(bookFile.getAbsolutePath(), bookTitle);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error handling book click", e);
                    Toast.makeText(BookDetailActivity.this, "打开书籍时出错：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            
            @Override
            public void onBookDelete(File bookFile, int position) {
                try {
                    if (bookFile == null) {
                        Log.e(TAG, "Cannot delete null book file");
                        Toast.makeText(BookDetailActivity.this, "无法删除无效的书籍文件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Log.d(TAG, "Delete requested for book: " + bookFile.getName());
                    showDeleteConfirmDialog(bookFile, position);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error handling book delete", e);
                    Toast.makeText(BookDetailActivity.this, "删除书籍时出错：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        
        pagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pagesRecyclerView.setAdapter(downloadedBookAdapter);
    }
    
    private void displayBookInfo() {
        if (book != null) {
            // 设置书籍标题
            String title = book.getName();
            if (title == null || title.trim().isEmpty()) {
                title = "未知书籍";
            }
            bookTitleTextView.setText(title);
            
            // 设置作者信息
            String author = book.getAuthor();
            if (author == null || author.trim().isEmpty()) {
                author = "未知作者";
            }
            bookAuthorTextView.setText("作者：" + author);
            
            // 设置书籍描述
            String description = book.getDescription();
            if (description == null || description.trim().isEmpty()) {
                description = "暂无描述信息";
            }
            bookDescriptionTextView.setText(description);
            
            // 加载书籍封面
            if (book.getCoverUrl() != null && !book.getCoverUrl().isEmpty()) {
                // 处理封面URL，确保包含IP地址和端口号，避免出现双斜杠
                String coverUrl = getCompleteCoverUrl(book.getCoverUrl());
                Log.d(TAG, "Loading book cover with URL: " + coverUrl);
                
                Glide.with(this)
                    .load(coverUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .into(bookCoverImageView);
            } else {
                // 设置默认封面
                bookCoverImageView.setImageResource(R.drawable.ic_book_placeholder);
            }
        }
    }
    
    /**
     * 确保URL格式正确，包含必要的协议前缀、IP地址和端口号，避免出现双斜杠问题
     */
    private String getCompleteCoverUrl(String originalUrl) {
        // 检查URL是否为空
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return null;
        }
        
        String trimmedUrl = originalUrl.trim();
        
        // 检查URL是否已经是完整的URL（包含协议、IP/域名和可能的端口）
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return trimmedUrl;
        }
        
        // 使用ApiClient中的getBaseUrl()方法获取基础URL（包含IP地址和端口号）
        String baseUrl = ApiClient.getBaseUrl();
        
        // 优化URL拼接逻辑，避免出现双斜杠问题
        if (baseUrl.endsWith("/")) {
            // 如果baseUrl以斜杠结尾
            if (trimmedUrl.startsWith("/")) {
                // 如果trimmedUrl也以斜杠开头，去掉trimmedUrl的斜杠
                return baseUrl + trimmedUrl.substring(1);
            } else {
                // 否则直接拼接
                return baseUrl + trimmedUrl;
            }
        } else {
            // 如果baseUrl不以斜杠结尾
            if (trimmedUrl.startsWith("/")) {
                // 如果trimmedUrl以斜杠开头，直接拼接
                return baseUrl + trimmedUrl;
            } else {
                // 否则添加斜杠后拼接
                return baseUrl + "/" + trimmedUrl;
            }
        }
    }
    
    private void loadNextPage() {
        if (!isLoading) {
            loadBookPages(currentPage);
        }
    }
    
    private void loadBookPages(int page) {
        // 简化的页面加载逻辑
        Toast.makeText(this, "页面加载功能暂未实现", Toast.LENGTH_SHORT).show();
    }
    
    private void startDownload() {
        Log.d(TAG, "startDownload method called");
        if (book == null) {
            Toast.makeText(this, "书籍信息不完整", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 检查存储权限
        if (!checkStoragePermission()) {
            requestStoragePermission();
            return;
        }
        
        performDownload();
    }
    
    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "请授予文件管理权限后重试", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
        }
    }
    
    private void performDownload() {
        // 检查网络连接状态
        if (!isNetworkConnected()) {
            showNetworkErrorDialog();
            return;
        }
        
        // 创建并显示美化的下载进度对话框
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_download_progress, null);
        
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        TextView progressText = dialogView.findViewById(R.id.progress_text);
        TextView statusMessage = dialogView.findViewById(R.id.status_message);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        final AlertDialog progressDialog = builder.create();
        
        // 设置取消按钮点击事件
        final boolean[] isCancelled = {false};
        cancelButton.setOnClickListener(v -> {
            isCancelled[0] = true;
            progressDialog.dismiss();
            downloadButton.setEnabled(true);
            downloadButton.setText("下载并阅读PDF");
            Toast.makeText(BookDetailActivity.this, "下载已取消", Toast.LENGTH_SHORT).show();
        });
        
        progressDialog.show();
        
        // 禁用下载按钮
        downloadButton.setEnabled(false);
        downloadButton.setText("下载中...");
        
        // 更新对话框状态
        statusMessage.setText("正在创建下载目录...");
        
        // 创建PDF文件
        final String fileName;
        if (book.getName().startsWith("未知")) {
            fileName = "书籍_" + System.currentTimeMillis() + ".pdf";
        } else {
            fileName = book.getName() + ".pdf";
        }
        
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "books");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        final File pdfFile = new File(downloadDir, fileName);
        
        // 更新对话框状态
        statusMessage.setText("正在从服务器下载PDF文件...");
        
        // 首先尝试从服务器下载PDF文件
        attemptDownloadWithRetry(book.getId(), pdfFile, fileName, progressDialog, statusMessage, progressBar, progressText, downloadButton, isCancelled, 0);
    }
    
    // 检查网络连接状态
    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
    
    // 显示网络错误对话框
    private void showNetworkErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("网络连接错误")
                .setMessage("请检查您的网络连接后重试")
                .setPositiveButton("确定", null)
                .show();
    }
    
    private void attemptDownloadWithRetry(final int bookId, final File pdfFile, final String fileName, 
                                         final AlertDialog progressDialog, final TextView statusMessage, 
                                         final ProgressBar progressBar, final TextView progressText, 
                                         final Button downloadButton, final boolean[] isCancelled, 
                                         final int retryCount) {
        final int MAX_RETRIES = 2;
        
        if (retryCount > 0) {
            Log.i(TAG, "Attempting download retry " + retryCount + " of " + MAX_RETRIES);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isCancelled[0]) {
                        statusMessage.setText("连接中断，正在尝试重新下载...(" + retryCount + "/" + MAX_RETRIES + ")");
                    }
                }
            });
            
            // 添加短暂延迟后重试
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Call<ResponseBody> call = apiService.downloadBookPdf(bookId);
        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (isCancelled[0]) return;
                
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        // 从服务器下载PDF文件
                        final ResponseBody responseBody = response.body();
                        final long contentLength = responseBody.contentLength();
                        
                        // 检查文件大小是否合理，避免无效下载
                        if (contentLength <= 0) {
                            Log.w(TAG, "Invalid content length: " + contentLength);
                            responseBody.close(); // 确保及时关闭响应体
                            if (retryCount < MAX_RETRIES) {
                                attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                        progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                            } else {
                                createLocalPDF(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                                        progressText, downloadButton, isCancelled);
                            }
                            return;
                        }
                        
                        // 创建后台线程执行文件下载操作
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // 优先使用较小的缓冲区，减少内存占用
                                byte[] buffer = new byte[1024]; // 减小缓冲区大小到1KB
                                int bytesRead;
                                long totalRead = 0;
                                long lastUpdateTime = System.currentTimeMillis();
                                long lastGCTime = System.currentTimeMillis();
                                
                                // 确保responseBody不为null
                                InputStream inputStream = null;
                                FileOutputStream outputStream = null;
                                
                                try {
                                    // 安全地获取输入流
                                    if (responseBody != null) {
                                        inputStream = responseBody.byteStream();
                                        outputStream = new FileOutputStream(pdfFile);
                                    } else {
                                        Log.e(TAG, "ResponseBody is null when trying to get input stream");
                                        throw new IOException("Response body is null");
                                    }
                                    
                                    // 严格的流式处理，避免一次性读取大量数据
                                    while (inputStream != null && !isCancelled[0]) {
                                        try {
                                            // 先检查inputStream是否仍然有效
                                            if (inputStream.available() < 0) {
                                                Log.e(TAG, "Input stream is not available");
                                                isCancelled[0] = true;
                                                break;
                                            }
                                            
                                            // 在读取前检查流的状态
                                            if (inputStream.markSupported()) {
                                                inputStream.mark(1);
                                                int testRead = inputStream.read();
                                                if (testRead == -1) {
                                                    Log.d(TAG, "Reached end of input stream, download completed successfully");
                                                    break;
                                                } else {
                                                    inputStream.reset();
                                                }
                                            }
                                            
                                            // 安全地读取数据，避免单次读取过多
                                            bytesRead = inputStream.read(buffer, 0, buffer.length);
                                            
                                            // 检查是否到达流末尾
                                            if (bytesRead == -1) {
                                                Log.d(TAG, "Reached end of input stream, download completed successfully");
                                                break;
                                            }

                                            // 检查读取的数据量是否正常
                                            if (bytesRead > 0) {
                                                // 确保outputStream不为null再写入
                                                if (outputStream != null) {
                                                    outputStream.write(buffer, 0, bytesRead);
                                                    outputStream.flush(); // 定期刷新缓冲区
                                                    totalRead += bytesRead;
                                                     
                                                    Log.v(TAG, "Successfully read and wrote " + bytesRead + " bytes, total: " + totalRead);
                                                } else {
                                                    Log.e(TAG, "Output stream is null during write operation");
                                                    isCancelled[0] = true;
                                                    break;
                                                }
                                            }
                                            
                                            // 定期建议垃圾回收，每处理1MB数据尝试一次
                                            long currentTime = System.currentTimeMillis();
                                            if (currentTime - lastGCTime >= 5000) { // 每5秒尝试一次
                                                lastGCTime = currentTime;
                                                System.gc();
                                            }
                                            
                                            // 进一步限制UI更新频率，减少内存压力
                                            if (currentTime - lastUpdateTime >= 200 || totalRead >= contentLength) {
                                                lastUpdateTime = currentTime;
                                                final int progress = (int) (totalRead * 50 / contentLength);
                                                final long currentProgress = totalRead;
                                                final long totalSize = contentLength;
                                                
                                                // 避免在UI线程中执行过多计算
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (!isCancelled[0]) {
                                                            progressBar.setProgress(progress);
                                                            progressText.setText(progress + "%");
                                                            // 使用StringBuilder减少字符串对象创建
                                                            StringBuilder sb = new StringBuilder();
                                                            sb.append("正在从服务器下载PDF文件... ")
                                                              .append(formatFileSize(currentProgress))
                                                              .append("/")
                                                              .append(formatFileSize(totalSize));
                                                            statusMessage.setText(sb.toString());
                                                        }
                                                    }
                                                });
                                            }
                                            
                                            // 增加额外的检查，防止在内存受限设备上出现问题
                                            if (Thread.interrupted()) {
                                                throw new InterruptedException("Download operation interrupted");
                                            }
                                        } catch (IOException e) {
                                            // 捕获read操作可能出现的异常
                                            Log.e(TAG, "Error reading from input stream: " + e.getMessage(), e);
                                             
                                            // 检查异常类型，区分网络错误和其他IO错误
                                            if (e instanceof java.net.SocketTimeoutException) {
                                                Log.e(TAG, "Network timeout occurred during download");
                                            } else if (e instanceof java.net.SocketException) {
                                                Log.e(TAG, "Socket exception occurred, possibly network connection lost");
                                            } else if (e instanceof java.net.UnknownHostException) {
                                                Log.e(TAG, "Unknown host exception, check network connectivity");
                                            }
                                            
                                            // 尝试重新建立连接并继续下载
                                            if (retryCount < MAX_RETRIES) {
                                                Log.i(TAG, "Attempting to retry download after IO exception");
                                                isCancelled[0] = true; // 退出当前下载循环
                                                break;
                                            } else {
                                                isCancelled[0] = true;
                                                break;
                                            }
                                        } catch (NullPointerException e) {
                                            // 捕获空指针异常，可能是因为inputStream已被关闭
                                            Log.e(TAG, "NullPointerException during read operation, inputStream may be closed", e);
                                            isCancelled[0] = true;
                                            break;
                                        } catch (IllegalStateException e) {
                                            // 捕获非法状态异常，可能是因为流已被关闭或处于无效状态
                                            Log.e(TAG, "IllegalStateException during read operation, stream may be in invalid state", e);
                                            isCancelled[0] = true;
                                            break;
                                        } catch (Exception e) {
                                            // 捕获其他所有异常
                                            Log.e(TAG, "Unexpected exception during read operation: " + e.getMessage(), e);
                                            isCancelled[0] = true;
                                            break;
                                        }
                                    }
                                } catch (IOException e) {
                                    Log.e(TAG, "Error during file download: " + e.getMessage(), e);
                                    isCancelled[0] = true;
                                } finally {
                                    // 确保所有资源都被正确关闭
                                    try {
                                        if (outputStream != null) {
                                            outputStream.close();
                                        }
                                        if (inputStream != null) {
                                            inputStream.close();
                                        }
                                        // 确保响应体关闭
                                        if (responseBody != null) {
                                            responseBody.close();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error closing resources: " + e.getMessage());
                                    }
                                }
                                
                                if (!isCancelled[0]) {
                                    // 验证下载的文件是否有效
                                    if (pdfFile.exists() && pdfFile.length() > 0) {
                                        Log.d(TAG, "Successfully downloaded PDF from server: " + fileName + ", size: " + pdfFile.length());
                                        
                                        // 调用内存友好的PDF文件处理方法
                                        handleDownloadedPDF(pdfFile, fileName, progressDialog, statusMessage, 
                                                progressBar, progressText, downloadButton, isCancelled);
                                    } else {
                                        Log.w(TAG, "Downloaded file is invalid or empty");
                                        if (retryCount < MAX_RETRIES) {
                                            attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                                    progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                                        } else {
                                            createLocalPDF(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                                                    progressText, downloadButton, isCancelled);
                                        }
                                    }
                                } else {
                                    // 如果取消了下载，检查是否是因为异常需要重试
                                    if (retryCount < MAX_RETRIES) {
                                        Log.i(TAG, "Download cancelled, attempting retry " + (retryCount + 1));
                                        attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                                progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                                    } else {
                                        // 如果取消了下载，删除文件
                                        if (pdfFile.exists()) {
                                            pdfFile.delete();
                                        }
                                        // 重新启用下载按钮
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                downloadButton.setEnabled(true);
                                                downloadButton.setText("下载并阅读PDF");
                                                progressDialog.dismiss();
                                                Toast.makeText(BookDetailActivity.this, "下载失败，请检查网络连接后重试", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                            }
                        }).start(); // 启动后台线程
                    } else if (response.code() == 404) {
                        // 服务器返回404，资源不存在
                        Log.w(TAG, "PDF resource not found on server (404): " + fileName);
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    progressDialog.dismiss();
                                    downloadButton.setEnabled(true);
                                    showResourceNotFoundDialog();
                                }
                            }
                        });
                    } else {
                        // 其他错误响应
                        Log.w(TAG, "Server returned error code: " + response.code() + ", message: " + response.message());
                        if (retryCount < MAX_RETRIES) {
                            attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                    progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                        } else {
                            createLocalPDF(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                                    progressText, downloadButton, isCancelled);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing PDF download: " + e.getMessage(), e);
                    if (retryCount < MAX_RETRIES) {
                        attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                    } else {
                        createLocalPDF(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                                progressText, downloadButton, isCancelled);
                    }
                }
            }
            
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (isCancelled[0]) return;
                
                // 详细记录失败原因
                Log.e(TAG, "Failed to download PDF from server: " + t.getMessage(), t);
                
                // 检查是否是连接被中止的错误，如果是则尝试重试
                if (t instanceof java.net.SocketException) {
                    String errorMessage = t.getMessage();
                    Log.e(TAG, "SocketException details: " + errorMessage);
                    
                    // 针对不同类型的SocketException提供更具体的重试策略
                    // 特别关注大文件下载导致的连接中止错误
                    boolean isConnectionAbort = errorMessage != null && 
                            (errorMessage.contains("Software caused connection abort") || 
                             errorMessage.contains("connection abort"));
                    
                    boolean isTimeout = errorMessage != null && errorMessage.contains("timeout");
                    
                    if (isConnectionAbort && retryCount < MAX_RETRIES) {
                        // 连接被中止错误（大文件常见），进行重试
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    statusMessage.setText("大文件下载连接中断，正在尝试重新连接...");
                                }
                            }
                        });
                        
                        // 大文件下载失败后需要更长的延迟来确保网络状态稳定
                        try {
                            Thread.sleep(3000); // 增加到3秒延迟
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                    } else if (isTimeout && retryCount < MAX_RETRIES) {
                        // 超时错误，增加延迟后重试
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    statusMessage.setText("连接超时，正在尝试重新连接...");
                                }
                            }
                        });
                        
                        try {
                            Thread.sleep(2500); // 超时错误使用较长的延迟
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                    } else {
                        // 其他SocketException，也尝试重试一次
                        if (retryCount < MAX_RETRIES) {
                            Log.d(TAG, "Other SocketException, attempting retry " + (retryCount + 1));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isCancelled[0]) {
                                        statusMessage.setText("网络连接异常，正在尝试重新连接...");
                                    }
                                }
                            });
                            
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                                    progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                        } else {
                            // 所有重试都失败，创建本地PDF
                            createLocalPDF(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                                    progressText, downloadButton, isCancelled);
                        }
                    }
                } else if (retryCount < MAX_RETRIES) {
                    // 其他类型的错误，也尝试重试
                    Log.d(TAG, "Non-SocketException, attempting retry " + (retryCount + 1));
                    attemptDownloadWithRetry(bookId, pdfFile, fileName, progressDialog, statusMessage, 
                            progressBar, progressText, downloadButton, isCancelled, retryCount + 1);
                } else {
                    // 所有重试都失败，创建本地PDF
                    createLocalPDF(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                            progressText, downloadButton, isCancelled);
                }
            }
        });
    }
    
    /**
     * 格式化文件大小显示
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private void openPDFReader(String pdfFilePath, String bookTitle) {
        // 验证PDF文件是否存在和有效
        if (!isValidPDFFile(pdfFilePath)) {
            showResourceNotFoundDialog();
            return;
        }
        
        try {
            File pdfFile = new File(pdfFilePath);
            
            // 使用FileProvider获取安全的URI
            Uri pdfUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                pdfFile
            );
            
            // 创建Intent使用系统默认PDF阅读器
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // 检查是否有应用可以处理PDF文件
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                Log.d(TAG, "Opening PDF with system reader: " + pdfFilePath + ", title: " + bookTitle);
            } else {
                // 如果没有PDF阅读器，提示用户安装
                Toast.makeText(this, "未找到PDF阅读器，请安装PDF阅读应用", Toast.LENGTH_LONG).show();
                Log.w(TAG, "No PDF reader app found on device");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open PDF with system reader", e);
            Toast.makeText(this, "无法打开PDF文件：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String getBasicBookContent(String bookName) {
        switch (bookName) {
            case "伤寒杂病论":
                return "《伤寒杂病论》" + System.lineSeparator() + System.lineSeparator() +
                        "作者：张仲景" + System.lineSeparator() + System.lineSeparator() +
                        "简介：" + System.lineSeparator() +
                        "《伤寒杂病论》是东汉末年张仲景所著的中医经典著作，被誉为'医圣之书'。" + System.lineSeparator() +
                        "本书系统地阐述了外感热病和内科杂病的辨证论治规律，奠定了中医临床医学的基础。" + System.lineSeparator() + System.lineSeparator() +
                        "主要内容：" + System.lineSeparator() +
                        "1. 六经辨证体系：太阳病、阳明病、少阳病、太阴病、少阴病、厥阴病" + System.lineSeparator() +
                        "2. 经典方剂：桂枝汤、麻黄汤、白虎汤、承气汤类方、小柴胡汤等" + System.lineSeparator() +
                        "3. 杂病论治：脏腑辨证、妇科病证等" + System.lineSeparator() + System.lineSeparator() +
                        "注：完整内容请在应用内查看或通过在线资源获取。";
            
            case "黄帝内经":
                return "《黄帝内经》" + System.lineSeparator() + System.lineSeparator() +
                        "简介：" + System.lineSeparator() +
                        "《黄帝内经》是中国最早的医学典籍，奠定了中医学的理论基础。" + System.lineSeparator() +
                        "全书分为《素问》和《灵枢》两部分。" + System.lineSeparator() + System.lineSeparator() +
                        "主要内容：" + System.lineSeparator() +
                        "1. 阴阳五行学说：中医理论的核心" + System.lineSeparator() +
                        "2. 脏腑经络：心、肺、肝、脾、肾等五脏六腑的生理功能和病理变化" + System.lineSeparator() +
                        "3. 病因病机：疾病发生发展的基本规律" + System.lineSeparator() +
                        "4. 诊法治则：望、闻、问、切四诊合参" + System.lineSeparator() + System.lineSeparator() +
                        "注：完整内容请在应用内查看或通过在线资源获取。";
            
            case "神农本草经":
                return "《神农本草经》" + System.lineSeparator() + System.lineSeparator() +
                        "简介：" + System.lineSeparator() +
                        "《神农本草经》是中国现存最早的药学专著，记载了365种药物，分为上、中、下三品。" + System.lineSeparator() + System.lineSeparator() +
                        "主要内容：" + System.lineSeparator() +
                        "1. 药物分类：上品（120种）、中品（120种）、下品（125种）" + System.lineSeparator() +
                        "2. 药性理论：四气（寒、热、温、凉）、五味（酸、苦、甘、辛、咸）" + System.lineSeparator() +
                        "3. 配伍规律：七情和合、君臣佐使" + System.lineSeparator() + System.lineSeparator() +
                        "注：完整内容请在应用内查看或通过在线资源获取。";
            
            default:
                return bookName + System.lineSeparator() + System.lineSeparator() +
                        "简介：" + System.lineSeparator() +
                        "这是一本中医相关书籍，包含理论知识和实践经验。" + System.lineSeparator() + System.lineSeparator() +
                        "内容概要：" + System.lineSeparator() +
                        "- 中医基础理论" + System.lineSeparator() +
                        "- 临床辨证论治" + System.lineSeparator() +
                        "- 中药方剂应用" + System.lineSeparator() + System.lineSeparator() +
                        "注：完整内容请在应用内查看。";
        }
    }
    
    private boolean isValidPDFFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            Log.e(TAG, "PDF file path is null or empty");
            return false;
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "PDF file does not exist: " + filePath);
            return false;
        }
        
        if (!file.isFile()) {
            Log.e(TAG, "Path is not a file: " + filePath);
            return false;
        }
        
        if (file.length() == 0) {
            Log.e(TAG, "PDF file is empty: " + filePath);
            return false;
        }
        
        if (!file.canRead()) {
            Log.e(TAG, "Cannot read PDF file: " + filePath);
            return false;
        }
        
        // 检查文件扩展名
        if (!filePath.toLowerCase().endsWith(".pdf")) {
            Log.e(TAG, "File is not a PDF: " + filePath);
            return false;
        }
        
        Log.d(TAG, "PDF file validation passed: " + filePath);
        return true;
    }
    
    private void showResourceNotFoundDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_resource_not_found, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        Button btnOk = dialogView.findViewById(R.id.btn_ok);
        Button btnRetry = dialogView.findViewById(R.id.btn_retry);
        
        btnOk.setOnClickListener(v -> dialog.dismiss());
        
        btnRetry.setOnClickListener(v -> {
            dialog.dismiss();
            // 重新触发下载
            performDownload();
        });
        
        dialog.show();
    }
    
    private void loadDownloadedBooks() {
        Log.d(TAG, "Loading downloaded books");
        
        try {
            // 获取下载目录
            File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "books");
            
            Log.d(TAG, "Using download directory path: " + downloadDir.getAbsolutePath());
            
            // 确保目录存在
            if (!downloadDir.exists()) {
                Log.d(TAG, "Creating download directory");
                if (!downloadDir.mkdirs()) {
                    Log.e(TAG, "Failed to create download directory");
                    Toast.makeText(this, "无法创建下载目录", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 检查目录权限
            if (!downloadDir.canRead()) {
                Log.e(TAG, "Cannot read download directory");
                Toast.makeText(this, "无法读取下载目录，请检查权限", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 清空现有数据
            downloadedBooks.clear();
            
            // 扫描PDF文件
            File[] allFiles = downloadDir.listFiles();
            if (allFiles != null) {
                for (File file : allFiles) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".pdf")) {
                        // 验证PDF文件有效性
                        if (isValidPDFFile(file.getAbsolutePath())) {
                            downloadedBooks.add(file);
                            Log.d(TAG, "Added valid book: " + file.getName() + " (" + file.length() + " bytes)");
                        } else {
                            Log.w(TAG, "Skipped invalid PDF file: " + file.getName());
                        }
                    }
                }
            } else {
                Log.w(TAG, "No files found in download directory or directory is not accessible");
            }
            
            Log.d(TAG, "Found " + downloadedBooks.size() + " valid books");
            
            // 更新UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    downloadedBookAdapter.notifyDataSetChanged();
                    
                    if (downloadedBooks.size() > 0) {
                        pageInfoTextView.setText(String.format("共找到 %d 本已下载的书籍", downloadedBooks.size()));
                    } else {
                        pageInfoTextView.setText("暂无已下载的书籍");
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading downloaded books", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(BookDetailActivity.this, "加载已下载书籍时出错：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    pageInfoTextView.setText("加载书籍列表失败");
                }
            });
        }
    }
    
    private void copyExampleBooks() {
        Log.d(TAG, "Creating example books");
        
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "books");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        String[] bookNames = {
            "中医基础理论",
            "黄帝内经",
            "伤寒论",
            "本草纲目"
        };
        
        for (String bookName : bookNames) {
            String fileName = bookName + ".pdf";
            File bookFile = new File(downloadDir, fileName);
            if (!bookFile.exists()) {
                try {
                    // 创建真正的PDF文件
                    Document document = new Document();
                    PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(bookFile));
                    document.open();
                    
                    try {
                        // 创建字体
                        Font titleFont = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD);
                        Font normalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);
                        
                        // 添加标题
                        Paragraph title = new Paragraph(bookName, titleFont);
                        title.setAlignment(Element.ALIGN_CENTER);
                        document.add(title);
                        
                        // 添加空行
                        document.add(new Paragraph(System.lineSeparator() + System.lineSeparator()));
                        
                        // 添加内容
                        String contentText = "这是 " + bookName + " 的示例内容。" + System.lineSeparator() + System.lineSeparator() +
                                "本书是一本关于中医的专业书籍，包含了丰富的理论知识和实践经验。" + System.lineSeparator() + System.lineSeparator() +
                                "主要内容包括：" + System.lineSeparator() +
                                "• 中医基础理论" + System.lineSeparator() +
                                "• 诊断方法" + System.lineSeparator() +
                                "• 治疗原则" + System.lineSeparator() +
                                "• 药物应用" + System.lineSeparator() +
                                "• 临床实践" + System.lineSeparator() + System.lineSeparator() +
                                "创建时间: " + new java.util.Date().toString();
                        Paragraph content = new Paragraph(contentText, normalFont);
                        document.add(content);
                        
                    } catch (DocumentException e) {
                        Log.e(TAG, "PDF creation failed for " + bookName, e);
                        throw new IOException("PDF creation failed", e);
                    } finally {
                        document.close();
                    }
                    
                    Log.d(TAG, "Created example book: " + fileName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create example book: " + fileName, e);
                }
            }
        }
        
        // 重新加载已下载的书籍
        loadDownloadedBooks();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performDownload();
            } else {
                Toast.makeText(this, "需要存储权限才能下载文件", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void showDeleteConfirmDialog(File bookFile, int position) {
        // 获取文件名（去掉.pdf后缀）
        String fileName = bookFile.getName();
        if (fileName.endsWith(".pdf")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📚 删除书籍")
                .setMessage("您确定要删除《" + fileName + "》吗？\n\n⚠️ 此操作无法撤销，文件将被永久删除。")
                .setPositiveButton("🗑️ 删除", (dialogInterface, which) -> {
                    deleteBook(bookFile, position);
                })
                .setNegativeButton("❌ 取消", (dialogInterface, which) -> {
                    dialogInterface.dismiss();
                })
                .setCancelable(true)
                .create();
        
        // 美化对话框样式
        dialog.show();
        
        // 设置按钮颜色
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        }
    }
    
    private void deleteBook(File bookFile, int position) {
        try {
            if (bookFile.delete()) {
                downloadedBooks.remove(position);
                downloadedBookAdapter.notifyItemRemoved(position);
                downloadedBookAdapter.notifyItemRangeChanged(position, downloadedBooks.size());
                
                // 更新页面信息
                if (downloadedBooks.size() > 0) {
                    pageInfoTextView.setText(String.format("共找到 %d 本已下载的书籍", downloadedBooks.size()));
                } else {
                    pageInfoTextView.setText("暂无已下载的书籍");
                }
                
                Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Delete book failed", e);
            Toast.makeText(this, "删除失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void finishDownload(File pdfFile, String fileName, final AlertDialog progressDialog, TextView statusMessage, ProgressBar progressBar, TextView progressText, Button downloadButton) {
        // 更新UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 更新对话框状态
                statusMessage.setText("下载完成，正在打开PDF阅读器...");
                progressBar.setProgress(100);
                progressText.setText("100%");
                
                // 延迟一下让用户看到完成消息
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // 关闭进度对话框
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    
                    downloadButton.setEnabled(true);
                    downloadButton.setText("下载并阅读PDF");
                    
                    Toast.makeText(BookDetailActivity.this, "下载完成：" + fileName, Toast.LENGTH_LONG).show();
                    
                    // 设置书籍的PDF文件路径
                    book.setPdfFilePath(pdfFile.getAbsolutePath());
                    
                    // 重新加载已下载书籍列表
                    loadDownloadedBooks();
                    
                    // 打开PDF阅读器
                    openPDFReader(pdfFile.getAbsolutePath(), book.getName());
                }}, 1000); // 延迟1秒
            }
        });
    }
    
    private void createLocalPDF(File pdfFile, String fileName, final AlertDialog progressDialog, TextView statusMessage, ProgressBar progressBar, TextView progressText, Button downloadButton, boolean[] isCancelled) {
        // 更新对话框状态
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isCancelled[0]) {
                    statusMessage.setText("服务器下载失败，正在创建本地PDF文件...");
                    progressBar.setProgress(50);
                    progressText.setText("50%");
                }
            }
        });
        
        Log.d(TAG, "Starting local PDF creation for: " + fileName);
        Document document = new Document();
        PdfWriter writer = null;
        FileOutputStream fos = null;
        
        try {
            fos = new FileOutputStream(pdfFile);
            writer = PdfWriter.getInstance(document, fos);
            document.open();
            Log.d(TAG, "PDF document opened successfully");
            
            // 创建字体
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);
            Font authorFont = new Font(Font.FontFamily.HELVETICA, 14, Font.NORMAL);
            Log.d(TAG, "Fonts created successfully");
            
            // 添加标题
            Paragraph title = new Paragraph(book.getName(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(System.lineSeparator()));
            Log.d(TAG, "Title added to PDF");
            
            // 添加作者信息
            Paragraph author = new Paragraph("作者: " + book.getAuthor(), authorFont);
            document.add(author);
            document.add(new Paragraph("\n"));
            Log.d(TAG, "Author added to PDF");
            
            // 更新进度 - 60%
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isCancelled[0]) {
                        progressBar.setProgress(60);
                        progressText.setText("60%");
                        statusMessage.setText("正在准备PDF内容...");
                    }
                }
            });
            
            // 根据书名添加具体内容（使用精简版内容）
            String fullContent = getBasicBookContent(book.getName());
            Log.d(TAG, "Got book content, length: " + fullContent.length());
            
            // 更新进度 - 70%
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isCancelled[0]) {
                        progressBar.setProgress(70);
                        progressText.setText("70%");
                        statusMessage.setText("正在添加PDF内容...");
                    }
                }
            });
            
            // 分段添加内容，提供更好的进度反馈
            String[] paragraphs = fullContent.split("\\n");
            int totalParagraphs = paragraphs.length;
            int lastProgressUpdate = 70;
            
            for (int i = 0; i < totalParagraphs && !isCancelled[0]; i++) {
                // 跳过空段落
                if (paragraphs[i].trim().isEmpty()) {
                    continue;
                }
                
                Paragraph paragraph = new Paragraph(paragraphs[i], normalFont);
                paragraph.setSpacingAfter(5);
                document.add(paragraph);
                
                // 每添加10%的段落更新一次进度
                if (i % (totalParagraphs / 10 + 1) == 0) {
                    final int progress = 70 + (i * 20 / totalParagraphs); // 70-90%范围内
                    final int currentProgress = Math.min(progress, 90);
                    final int currentParagraphIndex = i;
                    final int paragraphsCount = totalParagraphs;
                    
                    // 减少UI更新频率
                    if (currentProgress >= lastProgressUpdate + 10) {
                        lastProgressUpdate = currentProgress;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    progressBar.setProgress(currentProgress);
                                    progressText.setText(currentProgress + "%");
                                    statusMessage.setText("正在添加PDF内容... " + (currentParagraphIndex * 100 / paragraphsCount) + "%");
                                }
                            }
                        });
                    }
                }
                
                // 每添加10个段落刷新一次文档，释放内存
                if (i % 10 == 0) {
                    // iText的Document类没有flush方法，移除这个调用
                    System.gc(); // 提示垃圾回收
                }
            }
            
            Log.d(TAG, "Content added to PDF");
            
            // 更新进度 - 95%
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isCancelled[0]) {
                        progressBar.setProgress(95);
                        progressText.setText("95%");
                        statusMessage.setText("正在完成PDF...");
                    }
                }
            });
        } catch (DocumentException e) {
            Log.e(TAG, "PDF DocumentException", e);
            try {
                throw new IOException("PDF creation failed: " + e.getMessage(), e);
            } catch (IOException ex) {
                Log.e(TAG, "Error wrapping DocumentException", ex);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        downloadButton.setEnabled(true);
                        Toast.makeText(BookDetailActivity.this, "PDF创建失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "PDF creation general exception", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.dismiss();
                    downloadButton.setEnabled(true);
                    Toast.makeText(BookDetailActivity.this, "PDF创建失败", Toast.LENGTH_SHORT).show();
                }
            });
        } finally {
            try {
                if (document != null && document.isOpen()) {
                    document.close();
                    Log.d(TAG, "PDF document closed");
                }
                if (fos != null) {
                    fos.close();
                    Log.d(TAG, "FileOutputStream closed");
                }
                
                // 如果PDF创建成功且未取消，则完成下载流程
                if (!isCancelled[0]) {
                    Log.d(TAG, "Created local PDF with full content: " + fileName + ", size: " + pdfFile.length() + " bytes");
                    finishDownload(pdfFile, fileName, progressDialog, statusMessage, progressBar, 
                            progressText, downloadButton);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing PDF resources", e);
            }
        }
    }
    /**
     * 处理已下载的PDF文件，以内存友好的方式验证并完成下载流程
     */
    private void handleDownloadedPDF(final File pdfFile, final String fileName, final AlertDialog progressDialog, 
                                    final TextView statusMessage, final ProgressBar progressBar, 
                                    final TextView progressText, final Button downloadButton, 
                                    final boolean[] isCancelled) {
        // 避免在UI线程中执行文件操作
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 更新进度到60%
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCancelled[0]) {
                                progressBar.setProgress(60);
                                progressText.setText("60%");
                                statusMessage.setText("正在验证PDF文件...");
                            }
                        }
                    });
                    
                    // 进行轻量级文件验证，避免加载整个文件到内存
                    boolean isValid = false;
                    if (pdfFile.exists() && pdfFile.length() > 100) { // 检查文件大小是否合理
                        // 仅读取文件头部验证是否为有效的PDF文件
                        try (FileInputStream fis = new FileInputStream(pdfFile)) {
                            byte[] header = new byte[100]; // 只读取前100字节
                            int read = fis.read(header);
                            if (read >= 4) {
                                // PDF文件头部标识: %PDF
                                isValid = (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F');
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error validating PDF file header: " + e.getMessage());
                        }
                    }
                    
                    final boolean finalIsValid = isValid;
                    final long fileSize = pdfFile.length();
                    
                    // 更新进度和状态
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCancelled[0]) {
                                progressBar.setProgress(80);
                                progressText.setText("80%");
                                statusMessage.setText("PDF文件验证" + (finalIsValid ? "通过" : "失败") + 
                                        "，大小：" + formatFileSize(fileSize));
                            }
                        }
                    });
                    
                    // 如果文件有效且未取消，则完成下载
                    if (finalIsValid && !isCancelled[0]) {
                        // 尝试释放一些内存
                        System.gc();
                        
                        // 更新最终进度
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    progressBar.setProgress(95);
                                    progressText.setText("95%");
                                    statusMessage.setText("准备完成下载...");
                                }
                            }
                        });
                        
                        // 短暂延迟让用户看到进度
                        Thread.sleep(500);
                        
                        // 完成下载流程
                        finishDownload(pdfFile, fileName, progressDialog, statusMessage, 
                                progressBar, progressText, downloadButton);
                    } else {
                        // 文件无效，记录日志
                        Log.e(TAG, "Downloaded PDF file is invalid: " + fileName + ", size: " + fileSize + ", valid: " + finalIsValid);
                        
                        // 在UI线程中处理无效文件情况
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    statusMessage.setText("PDF文件验证失败");
                                }
                            }
                        });
                        
                        // 清理无效文件
                        if (pdfFile.exists()) {
                            pdfFile.delete();
                        }
                        
                        // 退出流程
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCancelled[0]) {
                                    progressDialog.dismiss();
                                    downloadButton.setEnabled(true);
                                    Toast.makeText(BookDetailActivity.this, "PDF文件无效，请重新下载", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "PDF handling interrupted: " + e.getMessage());
                    isCancelled[0] = true;
                    
                    // 清理并退出
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCancelled[0]) {
                                progressDialog.dismiss();
                                downloadButton.setEnabled(true);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error handling downloaded PDF: " + e.getMessage(), e);
                    
                    // 错误处理
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCancelled[0]) {
                                progressDialog.dismiss();
                                downloadButton.setEnabled(true);
                                Toast.makeText(BookDetailActivity.this, "处理PDF文件时出错", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}