package com.wenxing.runyitong;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Handler;
import android.view.LayoutInflater;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import android.graphics.drawable.Drawable;
import com.wenxing.runyitong.api.ApiClient;
import com.wenxing.runyitong.api.ApiResponse;
import com.wenxing.runyitong.model.PaymentOrderRequest;
import com.wenxing.runyitong.model.PaymentOrderResponse;
import com.wenxing.runyitong.model.Order;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.utils.Constants;
import com.google.gson.Gson;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.alipay.sdk.app.PayTask;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import com.alipay.sdk.app.PayTask;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.model.Product;

// 微信支付SDK导入
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import java.util.HashMap;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class ProductDetailActivity extends AppCompatActivity {

    private static final int SDK_PAY_FLAG = 1;
    private static final int WECHAT_PAY_REQUEST_CODE = 2;
    
    // 微信支付API
    private IWXAPI wxApi;
    
    // 原有的单个图片视图被替换为轮播图
    private ViewPager2 productImageSlider;
    private LinearLayout sliderIndicator;
    private TextView imageCountText;
    private TextView productTitle;
    private TextView productPrice;
    private TextView productSpecification;
    private TextView productManufacturer;
    private TextView productPurchaseCount;
    private TextView productDetail;
    private TextView pharmacyName;
    private LinearLayout galleryContainer;
    private Product currentProduct;
    
    // 用于图片轮播的变量
    private List<String> allProductImages = new ArrayList<>(); // 存储所有商品图片
    private ImageSliderAdapter sliderAdapter;
    private Handler sliderHandler = new Handler();
    private static final int SLIDER_DELAY = 5000; // 轮播延迟时间，单位毫秒
    private Runnable sliderRunnable;
    
    // 将Button类型改为ImageButton类型
    private android.widget.ImageButton btnBack;
    private android.widget.ImageButton btnShare;
    private android.widget.ImageButton btnBuy;
    
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @SuppressWarnings("unused")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SDK_PAY_FLAG: {
                    @SuppressWarnings("unchecked")
                    PayResult payResult = new PayResult((Map<String, String>) msg.obj);
                    /**
                     * 对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
                     */
                    String resultInfo = payResult.getResult();// 同步返回需要验证的信息
                    String resultStatus = payResult.getResultStatus();
                    String memo = payResult.getMemo();
                    
                    android.util.Log.d("AlipayResult", "支付结果: " + payResult.toString());
                    
                    // 判断resultStatus 为9000则代表支付成功
                    if (TextUtils.equals(resultStatus, "9000")) {
                        // 该笔订单是否真实支付成功，需要依赖服务端的异步通知。
                        onPaymentSuccess("支付宝支付");
                    } else {
                        // 显示详细的错误信息
                        String errorMessage = "支付失败";
                        if (!TextUtils.isEmpty(memo)) {
                            errorMessage = memo;
                        } else if (TextUtils.equals(resultStatus, "4000")) {
                            errorMessage = "订单支付失败";
                        } else if (TextUtils.equals(resultStatus, "6001")) {
                            errorMessage = "用户中途取消";
                        } else if (TextUtils.equals(resultStatus, "6002")) {
                            errorMessage = "网络连接出错";
                        } else if (TextUtils.equals(resultStatus, "8000")) {
                            errorMessage = "支付结果因为支付渠道原因或者系统原因还在处理中";
                        }
                        
                        Toast.makeText(ProductDetailActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                    break;
                }
                default:
                    break;
            }
        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);
        
        // 初始化微信API
        initWechatAPI();
        
        initViews();
        initData();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 在Activity恢复时重新启动轮播
        if (sliderAdapter != null && allProductImages.size() > 1) {
            startSliderTimer();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 在Activity暂停时停止轮播
        if (sliderRunnable != null) {
            sliderHandler.removeCallbacks(sliderRunnable);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在Activity销毁时清理资源
        if (sliderRunnable != null) {
            sliderHandler.removeCallbacks(sliderRunnable);
        }
        // 清理Glide资源（移除了不兼容的clearMemory调用）
    }
    
    /**
     * 初始化微信API
     */
    private void initWechatAPI() {
        // 微信AppID，需要与后端配置和WXPayEntryActivity保持一致
        String wxAppId = "wx1234567890abcdef";
        wxApi = WXAPIFactory.createWXAPI(this, wxAppId, true);
        wxApi.registerApp(wxAppId);
    }

    private void initViews() {
        // 初始化图片轮播相关组件
        productImageSlider = findViewById(R.id.product_image_slider);
        sliderIndicator = findViewById(R.id.slider_indicator);
        imageCountText = findViewById(R.id.image_count_text);
        
        productTitle = findViewById(R.id.product_title);
        productPrice = findViewById(R.id.product_price);
        productSpecification = findViewById(R.id.product_specification);
        productManufacturer = findViewById(R.id.product_manufacturer);
        productPurchaseCount = findViewById(R.id.product_purchase_count);
        productDetail = findViewById(R.id.product_detail);
        pharmacyName = findViewById(R.id.pharmacy_name);
        
        // 初始化顶部按钮
        btnBack = findViewById(R.id.btn_back);
        btnShare = findViewById(R.id.btn_share_bottom);
        btnBuy = findViewById(R.id.btn_buy_bottom);
        
        Log.d("ProductDetailActivity", "Buttons initialized: back=" + (btnBack != null) + 
              ", share=" + (btnShare != null) + ", buy=" + (btnBuy != null));
        
        // 检查按钮是否正确找到
        if (btnBack == null) {
            Log.e("ProductDetailActivity", "回退按钮未找到");
        }
        if (btnShare == null) {
            Log.e("ProductDetailActivity", "分享按钮未找到");
        }
        if (btnBuy == null) {
            Log.e("ProductDetailActivity", "购买按钮未找到");
        }
        
        // 从XML布局中获取galleryContainer
        galleryContainer = findViewById(R.id.gallery_container);

        // 回退按钮点击事件
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                Log.d("ProductDetailActivity", "回退按钮被点击");
                onBackPressed();
            });
        }

        // 分享按钮点击事件
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                Log.d("ProductDetailActivity", "分享按钮被点击");
                shareProduct();
            });
        }

        // 购买按钮点击事件
        if (btnBuy != null) {
            btnBuy.setOnClickListener(v -> {
                Log.d("ProductDetailActivity", "购买按钮被点击");
                purchaseProduct();
            });
        }
    }

    private void initData() {
        // 获取从上一个页面传递的商品对象
        currentProduct = (Product) getIntent().getSerializableExtra("product");
        if (currentProduct != null) {
            productTitle.setText(currentProduct.getName());
            productPrice.setText(String.format("¥%.2f", currentProduct.getPrice()));
            
            // 使用从后端获取的实际数据
            productSpecification.setText(currentProduct.getSpecification() != null ? currentProduct.getSpecification() : "标准规格");
            productManufacturer.setText(currentProduct.getManufacturer() != null ? currentProduct.getManufacturer() : "健康医药有限公司");
            productPurchaseCount.setText(currentProduct.getPurchaseCount() + "+ 人已购");
            productDetail.setText(currentProduct.getDescription() + "\n\n这是商品的详细描述信息，包含了商品的使用方法、注意事项等。");
            // 显示药店名称
            if (pharmacyName != null) {
                pharmacyName.setText(currentProduct.getPharmacyName() != null ? currentProduct.getPharmacyName() : "健康药店");
            }

            // 收集所有商品图片（主图+图库图片）
            collectProductImages();
            
            // 设置图片轮播
            setupImageSlider();
        }
        
        // 加载其余三张图片到gallery_container
        loadGalleryImages();
    }
    
    /**
     * 收集所有商品图片（主图+图库图片）
     */
    private void collectProductImages() {
        allProductImages.clear();
        
        // 首先添加主图
        if (currentProduct.getFeaturedImageFile() != null && !currentProduct.getFeaturedImageFile().isEmpty()) {
            String imageUrl = currentProduct.getFeaturedImageFile();
            // 确保URL格式正确，如果缺少协议头则添加
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                imageUrl = "http://" + imageUrl; // 假设使用HTTP协议，实际应根据后端配置调整
            }
            allProductImages.add(imageUrl);
        }
        
        // 然后添加图库图片（过滤掉与主图重复的图片）
        if (currentProduct.getGalleryImages() != null && !currentProduct.getGalleryImages().isEmpty()) {
            for (String imageUrl : currentProduct.getGalleryImages()) {
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    // 确保URL格式正确，如果缺少协议头则添加
                    if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                        imageUrl = "http://" + imageUrl;
                    }
                    
                    // 避免重复添加图片（如果图库中包含了主图）
                    if (!allProductImages.contains(imageUrl)) {
                        allProductImages.add(imageUrl);
                    }
                }
            }
        }
    }
    
    /**
     * 加载其余三张图片到gallery_container
     */
    private void loadGalleryImages() {
        if (galleryContainer == null || allProductImages.size() <= 1) {
            return;
        }
        
        // 清空gallery_container
        galleryContainer.removeAllViews();
        
        // 获取屏幕密度，用于计算图片大小
        float density = getResources().getDisplayMetrics().density;
        // 图片宽度设为屏幕宽度减去边距，高度根据原图比例自适应
        int imageWidth = (int) (getResources().getDisplayMetrics().widthPixels - 40 * density); // 屏幕宽度减去边距
        int imageMargin = (int) (12 * density); // 图片间距
        
        // 从第二张图片开始显示（跳过主图），最多显示3张
        int startIndex = 1; // 从第二张图片开始
        int endIndex = Math.min(startIndex + 3, allProductImages.size()); // 最多显示3张图片
        
        for (int i = startIndex; i < endIndex; i++) {
            final String imageUrl = allProductImages.get(i);
            
            // 创建ImageView
            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    imageWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, imageMargin, 0, 0); // 垂直布局，只设置上下边距
            params.gravity = Gravity.CENTER_HORIZONTAL; // 水平居中
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE); // 保持原图比例
            imageView.setAdjustViewBounds(true); // 调整视图边界以保持宽高比
            imageView.setPadding(8, 8, 8, 8);
            
            // 添加圆角和阴影效果
            imageView.setBackgroundResource(R.drawable.circle_background_light);
            imageView.setElevation(4);
            
            // 配置Glide选项
            RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.ic_launcher_background) // 加载中的占位图
                    .error(R.drawable.ic_launcher_background) // 加载失败的占位图
                    .centerInside(); // 保持原图比例居中显示
            
            // 使用Glide加载图片
            Glide.with(this)
                    .load(imageUrl)
                    .apply(options)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e("ProductDetailActivity", "Gallery image loading failed: " + (e != null ? e.getMessage() : "Unknown error"));
                            return false;
                        }
                        
                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d("ProductDetailActivity", "Gallery image loaded successfully: " + imageUrl);
                            return false;
                        }
                    })
                    .into(imageView);
            
            // 添加点击事件，点击图片时跳转到轮播图对应的位置
            final int position = i;
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (productImageSlider != null) {
                        productImageSlider.setCurrentItem(position, true);
                        // 滚动到轮播图位置
                        productImageSlider.requestFocus();
                        productImageSlider.requestFocusFromTouch();
                    }
                }
            });
            
            // 添加到gallery_container
            galleryContainer.addView(imageView);
        }
    }

    /**
     * 设置图片轮播
     */
    private void setupImageSlider() {
        if (allProductImages.isEmpty()) {
            // 如果没有图片，添加一个占位图
            allProductImages.add("placeholder");
        }
        
        // 创建适配器
        sliderAdapter = new ImageSliderAdapter(allProductImages);
        productImageSlider.setAdapter(sliderAdapter);
        
        // 更新图片计数显示
        updateImageCount(0);
        
        // 创建轮播指示器
        createSliderIndicators();
        
        // 设置ViewPager2的滑动监听
        productImageSlider.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // 更新指示器状态
                updateSliderIndicators(position);
                // 更新图片计数显示
                updateImageCount(position);
                // 重置轮播计时器
                resetSliderTimer();
            }
        });
        
        // 设置轮播动画
        productImageSlider.setPageTransformer(new ViewPager2.PageTransformer() {
            @Override
            public void transformPage(@NonNull View page, float position) {
                // 简单的透明度和缩放动画
                final float MIN_SCALE = 0.95f;
                final float MIN_ALPHA = 0.8f;
                
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                
                if (position < -1) { // [-Infinity,-1)
                    // 页面已经完全离开左侧屏幕
                    page.setAlpha(0f);
                } else if (position <= 1) { // [-1,1]
                    // 计算缩放和透明度
                    float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
                    float vertMargin = pageHeight * (1 - scaleFactor) / 2;
                    float horzMargin = pageWidth * (1 - scaleFactor) / 2;
                    
                    if (position < 0) {
                        page.setTranslationX(horzMargin - vertMargin / 2);
                    } else {
                        page.setTranslationX(-horzMargin + vertMargin / 2);
                    }
                    
                    // 缩放页面
                    page.setScaleX(scaleFactor);
                    page.setScaleY(scaleFactor);
                    
                    // 设置透明度
                    page.setAlpha(MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));
                } else { // (1,+Infinity]
                    // 页面已经完全离开右侧屏幕
                    page.setAlpha(0f);
                }
            }
        });
        
        // 初始化自动轮播
        startSliderTimer();
    }
    
    /**
     * 创建轮播指示器
     */
    private void createSliderIndicators() {
        if (allProductImages.size() <= 1) {
            // 如果只有一张图片，不需要显示指示器
            sliderIndicator.setVisibility(View.GONE);
            return;
        }
        
        sliderIndicator.setVisibility(View.VISIBLE);
        sliderIndicator.removeAllViews();
        
        // 获取屏幕密度，用于计算指示器大小
        float density = getResources().getDisplayMetrics().density;
        int indicatorSize = (int) (8 * density); // 指示器大小
        int indicatorMargin = (int) (4 * density); // 指示器间距
        
        for (int i = 0; i < allProductImages.size(); i++) {
            View indicator = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    indicatorSize, indicatorSize);
            params.setMargins(indicatorMargin, 0, indicatorMargin, 0);
            indicator.setLayoutParams(params);
            
            // 设置指示器背景
            indicator.setBackgroundResource(i == 0 ? 
                    R.drawable.indicator_selected : R.drawable.indicator_unselected);
            
            // 添加点击事件，点击指示器切换到对应页面
            final int position = i;
            indicator.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    productImageSlider.setCurrentItem(position);
                }
            });
            
            sliderIndicator.addView(indicator);
        }
    }
    
    /**
     * 更新轮播指示器状态
     */
    private void updateSliderIndicators(int position) {
        for (int i = 0; i < sliderIndicator.getChildCount(); i++) {
            View indicator = sliderIndicator.getChildAt(i);
            indicator.setBackgroundResource(i == position ? 
                    R.drawable.indicator_selected : R.drawable.indicator_unselected);
        }
    }
    
    /**
     * 更新图片计数显示
     */
    private void updateImageCount(int position) {
        imageCountText.setText((position + 1) + "/" + allProductImages.size());
    }
    
    /**
     * 开始轮播计时器
     */
    private void startSliderTimer() {
        sliderRunnable = new Runnable() {
            @Override
            public void run() {
                int currentPosition = productImageSlider.getCurrentItem();
                int nextPosition = (currentPosition + 1) % allProductImages.size();
                productImageSlider.setCurrentItem(nextPosition, true);
            }
        };
        sliderHandler.postDelayed(sliderRunnable, SLIDER_DELAY);
    }
    
    /**
     * 重置轮播计时器
     */
    private void resetSliderTimer() {
        if (sliderRunnable != null) {
            sliderHandler.removeCallbacks(sliderRunnable);
            sliderHandler.postDelayed(sliderRunnable, SLIDER_DELAY);
        }
    }
    
    /**
     * 图片轮播适配器
     */
    private class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.SliderViewHolder> {
        private List<String> imageUrls;
        
        public ImageSliderAdapter(List<String> imageUrls) {
            this.imageUrls = imageUrls;
        }
        
        @NonNull
        @Override
        public SliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image_slider, parent, false);
            return new SliderViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull SliderViewHolder holder, int position) {
            String imageUrl = imageUrls.get(position);
            
            // 配置Glide选项
            RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.ic_launcher_background) // 加载中的占位图
                    .error(R.drawable.ic_launcher_background) // 加载失败的占位图
                    .centerCrop(); // 居中裁剪
            
            if ("placeholder".equals(imageUrl)) {
                // 显示占位图
                holder.imageView.setImageResource(R.drawable.ic_launcher_background);
            } else {
                // 使用Glide加载图片
                Glide.with(ProductDetailActivity.this)
                        .load(imageUrl)
                        .apply(options)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                Log.e("ProductDetailActivity", "Image loading failed: " + (e != null ? e.getMessage() : "Unknown error"));
                                holder.imageView.setImageResource(R.drawable.ic_launcher_background);
                                return false;
                            }
                            
                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                Log.d("ProductDetailActivity", "Image loaded successfully");
                                return false;
                            }
                        })
                        .into(holder.imageView);
            }
        }
        
        @Override
        public int getItemCount() {
            return imageUrls.size();
        }
        
        class SliderViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            
            public SliderViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.slider_image);
                
                // 设置图片点击事件，点击时暂停/恢复轮播
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 点击图片时可以暂停轮播，再次点击恢复轮播
                        // 这里简化处理，只输出日志
                        Log.d("ProductDetailActivity", "Image clicked at position: " + getAdapterPosition());
                    }
                });
            }
        }
    }

    /**
     * 分享商品功能
     */
    private void shareProduct() {
        if (currentProduct == null) {
            Toast.makeText(this, "商品信息不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建分享内容
        String shareTitle = "推荐一个好商品";
        String shareContent = String.format(
            "商品名称：%s\n" +
            "价格：¥%.2f\n" +
            "规格：%s\n" +
            "厂商：%s\n" +
            "描述：%s\n\n" +
            "来自AI医疗助手，专业的医疗健康平台",
            currentProduct.getName(),
            currentProduct.getPrice(),
            currentProduct.getSpecification() != null ? currentProduct.getSpecification() : "标准规格",
            currentProduct.getManufacturer() != null ? currentProduct.getManufacturer() : "健康医药有限公司",
            currentProduct.getDescription()
        );

        // 创建分享Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareTitle);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareContent);

        // 创建选择器
        Intent chooser = Intent.createChooser(shareIntent, "分享商品到");
        
        // 检查是否有应用可以处理分享Intent
        if (shareIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(chooser);
        } else {
             Toast.makeText(this, "没有找到可用的分享应用", Toast.LENGTH_SHORT).show();
         }
     }

     /**
      * 购买商品功能
      */
     private void purchaseProduct() {
         if (currentProduct == null) {
             Toast.makeText(this, "商品信息不存在", Toast.LENGTH_SHORT).show();
             return;
         }

         // 显示支付方式选择对话框
         showPaymentMethodDialog();
     }

     /**
     * 显示支付方式选择对话框
     */
    private void showPaymentMethodDialog() {
        // 创建自定义对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        
        // 加载自定义布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_payment_method, null);
        builder.setView(dialogView);
        
        // 设置商品信息
        TextView tvProductName = dialogView.findViewById(R.id.tv_product_name);
        TextView tvProductPrice = dialogView.findViewById(R.id.tv_product_price);
        
        tvProductName.setText("商品：" + currentProduct.getName());
        tvProductPrice.setText(String.format("¥%.2f", currentProduct.getPrice()));
        
        // 创建对话框
        android.app.AlertDialog dialog = builder.create();
        
        // 设置对话框背景透明，让自定义圆角生效
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // 支付宝支付点击事件
        LinearLayout layoutAlipay = dialogView.findViewById(R.id.layout_alipay);
        layoutAlipay.setOnClickListener(v -> {
            dialog.dismiss();
            confirmPurchase("支付宝支付");
        });
        
        // 微信支付点击事件
        LinearLayout layoutWechat = dialogView.findViewById(R.id.layout_wechat);
        layoutWechat.setOnClickListener(v -> {
            dialog.dismiss();
            confirmPurchase("微信支付");
        });
        
        // 取消按钮点击事件
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

     /**
     * 确认购买对话框
     */
    private void confirmPurchase(String paymentMethod) {
        // 创建对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        // 加载自定义布局
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_purchase, null);
        builder.setView(dialogView);
        
        // 获取布局中的控件
        TextView tvProductName = dialogView.findViewById(R.id.tv_product_name);
        TextView tvProductPrice = dialogView.findViewById(R.id.tv_product_price);
        TextView tvPaymentMethod = dialogView.findViewById(R.id.tv_payment_method);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        
        // 设置商品信息
        tvProductName.setText(currentProduct.getName());
        tvProductPrice.setText(String.format("¥%.2f", currentProduct.getPrice()));
        tvPaymentMethod.setText(paymentMethod);
        
        // 创建并显示对话框
        final android.app.AlertDialog dialog = builder.create();
        dialog.setCancelable(false); // 点击外部不关闭对话框
        
        // 取消按钮点击事件
        btnCancel.setOnClickListener(v -> {
            Log.d("ProductDetailActivity", "取消购买");
            dialog.dismiss();
        });
        
        // 确认支付按钮点击事件
        btnConfirm.setOnClickListener(v -> {
            Log.d("ProductDetailActivity", "确认支付: " + paymentMethod);
            dialog.dismiss();
            // 执行支付逻辑
            processPurchase(paymentMethod);
        });
        
        dialog.show();
        
        // 设置对话框宽度，让它看起来更好看
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getWindowManager().getDefaultDisplay().getWidth() * 0.85);
            window.setAttributes(params);
        }
    }

     /**
      * 处理购买逻辑
      */
     private void processPurchase(String paymentMethod) {
         // 显示支付处理中的提示
         Toast.makeText(this, "正在跳转到" + paymentMethod + "...", Toast.LENGTH_SHORT).show();

         // 根据支付方式处理不同的支付逻辑
         if ("支付宝支付".equals(paymentMethod)) {
             processAlipayPayment();
         } else if ("微信支付".equals(paymentMethod)) {
             processWechatPayment();
         }
     }

     /**
      * 处理支付宝支付
      */
     private void processAlipayPayment() {
         Toast.makeText(this, "正在启动支付宝支付...", Toast.LENGTH_SHORT).show();
         
         // 显示加载对话框
         android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
         progressDialog.setMessage("正在创建订单...");
         progressDialog.setCancelable(true); // 允许用户取消
         progressDialog.setOnCancelListener(dialog -> {
             Toast.makeText(this, "订单创建已取消", Toast.LENGTH_SHORT).show();
         });
         progressDialog.show();
         
         // 设置超时处理
         android.os.Handler timeoutHandler = new android.os.Handler();
         Runnable timeoutRunnable = () -> {
             if (progressDialog.isShowing()) {
                 progressDialog.dismiss();
                 Toast.makeText(this, "订单创建超时，请重试", Toast.LENGTH_LONG).show();
             }
         };
         timeoutHandler.postDelayed(timeoutRunnable, 30000); // 30秒超时
         
         // 从服务器获取订单信息
         getOrderInfoFromServer(1, new OrderInfoCallback() { // 默认数量为1
             @Override
             public void onSuccess(String orderString, PaymentOrderResponse.OrderInfo orderInfo) {
                 // 取消超时处理
                 timeoutHandler.removeCallbacks(timeoutRunnable);
                 // 关闭加载对话框
                 if (progressDialog.isShowing()) {
                     progressDialog.dismiss();
                 }
                 
                 // 执行支付宝支付
                 Runnable payRunnable = new Runnable() {
                     @Override
                     public void run() {
                         PayTask alipay = new PayTask(ProductDetailActivity.this);
                         Map<String, String> result = alipay.payV2(orderString, true);
                         
                         Message msg = new Message();
                         msg.what = SDK_PAY_FLAG;
                         msg.obj = result;
                         mHandler.sendMessage(msg);
                     }
                 };
                 
                 // 必须异步调用
                 Thread payThread = new Thread(payRunnable);
                 payThread.start();
             }
             
             @Override
             public void onError(String errorMessage) {
                 // 取消超时处理
                 timeoutHandler.removeCallbacks(timeoutRunnable);
                 // 关闭加载对话框
                 if (progressDialog.isShowing()) {
                     progressDialog.dismiss();
                 }
                 
                 // 显示错误信息
                 runOnUiThread(() -> {
                     Toast.makeText(ProductDetailActivity.this, "订单创建失败: " + errorMessage, Toast.LENGTH_LONG).show();
                 });
             }
         });
     }

     /**
       * 从服务器获取支付订单信息
       */
      private void getOrderInfoFromServer(int quantity, OrderInfoCallback callback) {
          // 创建请求对象
          PaymentOrderRequest request = new PaymentOrderRequest(
              currentProduct.getId(),
              quantity,
              "30m"
          );
          
          android.util.Log.d("ProductDetail", "开始创建订单，商品ID: " + currentProduct.getId() + ", 数量: " + quantity);
          
          // 调用服务器API创建订单
          Call<ApiResponse<PaymentOrderResponse>> call = ApiClient.getApiService().createAlipayOrder(request);
          
          call.enqueue(new Callback<ApiResponse<PaymentOrderResponse>>() {
              @Override
              public void onResponse(Call<ApiResponse<PaymentOrderResponse>> call, Response<ApiResponse<PaymentOrderResponse>> response) {
                  android.util.Log.d("ProductDetail", "收到服务器响应，状态码: " + response.code());
                  if (response.isSuccessful() && response.body() != null) {
                      ApiResponse<PaymentOrderResponse> apiResponse = response.body();
                      if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                          PaymentOrderResponse orderResponse = apiResponse.getData();
                          if (orderResponse.isSuccess() && orderResponse.getOrderString() != null) {
                              android.util.Log.d("ProductDetail", "订单创建成功");
                              callback.onSuccess(orderResponse.getOrderString(), orderResponse.getOrderInfo());
                          } else {
                              android.util.Log.e("ProductDetail", "订单创建失败: " + orderResponse.getMessage());
                              callback.onError("订单创建失败: " + orderResponse.getMessage());
                          }
                      } else {
                          android.util.Log.e("ProductDetail", "API调用失败: " + apiResponse.getMessage());
                          callback.onError("API调用失败: " + apiResponse.getMessage());
                      }
                  } else {
                      android.util.Log.e("ProductDetail", "网络请求失败: " + response.message());
                      callback.onError("网络请求失败: " + response.message());
                  }
              }
              
              @Override
              public void onFailure(Call<ApiResponse<PaymentOrderResponse>> call, Throwable t) {
                  android.util.Log.e("ProductDetail", "网络连接失败: " + t.getMessage(), t);
                  callback.onError("网络连接失败: " + t.getMessage());
              }
          });
      }
      
      /**
       * 订单信息回调接口
       */
      private interface OrderInfoCallback {
          void onSuccess(String orderString, PaymentOrderResponse.OrderInfo orderInfo);
          void onError(String errorMessage);
      }
      
      /**
       * 微信支付订单信息回调接口
       */
      private interface WechatOrderInfoCallback {
          void onSuccess(PaymentOrderResponse.OrderInfo orderInfo);
          void onError(String errorMessage);
      }

     /**
      * 支付结果处理类
      */
     public static class PayResult {
         private String resultStatus;
         private String result;
         private String memo;

         public PayResult(Map<String, String> rawResult) {
             if (rawResult == null) {
                 return;
             }

             for (String key : rawResult.keySet()) {
                 if (TextUtils.equals(key, "resultStatus")) {
                     resultStatus = rawResult.get(key);
                 } else if (TextUtils.equals(key, "result")) {
                     result = rawResult.get(key);
                 } else if (TextUtils.equals(key, "memo")) {
                     memo = rawResult.get(key);
                 }
             }
         }

         @Override
         public String toString() {
             return "resultStatus={" + resultStatus + "};memo={" + memo
                     + "};result={" + result + "}";
         }

         /**
          * @return the resultStatus
          */
         public String getResultStatus() {
             return resultStatus;
         }

         /**
          * @return the memo
          */
         public String getMemo() {
             return memo;
         }

         /**
          * @return the result
          */
         public String getResult() {
             return result;
         }
     }

     /**
      * 处理微信支付
      */
     private void processWechatPayment() {
         Toast.makeText(this, "正在启动微信支付...", Toast.LENGTH_SHORT).show();
         
         // 显示加载对话框
         android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
         progressDialog.setMessage("正在创建微信订单...");
         progressDialog.setCancelable(true);
         progressDialog.setOnCancelListener(dialog -> {
             Toast.makeText(this, "订单创建已取消", Toast.LENGTH_SHORT).show();
         });
         progressDialog.show();
         
         // 设置超时处理
         android.os.Handler timeoutHandler = new android.os.Handler();
         Runnable timeoutRunnable = () -> {
             if (progressDialog.isShowing()) {
                 progressDialog.dismiss();
                 Toast.makeText(this, "订单创建超时，请重试", Toast.LENGTH_LONG).show();
             }
         };
         timeoutHandler.postDelayed(timeoutRunnable, 30000); // 30秒超时
         
         // 从服务器获取微信订单信息
         getWechatOrderInfoFromServer(1, new WechatOrderInfoCallback() {
             @Override
             public void onSuccess(PaymentOrderResponse.OrderInfo orderInfo) {
                 // 取消超时处理
                 timeoutHandler.removeCallbacks(timeoutRunnable);
                 // 关闭加载对话框
                 if (progressDialog.isShowing()) {
                     progressDialog.dismiss();
                 }
                 
                 // 启动微信支付
                 startWechatPay(orderInfo);
             }
             
             @Override
             public void onError(String errorMessage) {
                 // 取消超时处理
                 timeoutHandler.removeCallbacks(timeoutRunnable);
                 // 关闭加载对话框
                 if (progressDialog.isShowing()) {
                     progressDialog.dismiss();
                 }
                 
                 Toast.makeText(ProductDetailActivity.this, "创建微信订单失败: " + errorMessage, Toast.LENGTH_LONG).show();
             }
         });
     }
     
     /**
      * 从服务器获取微信订单信息
      */
     private void getWechatOrderInfoFromServer(int quantity, WechatOrderInfoCallback callback) {
         PaymentOrderRequest request = new PaymentOrderRequest();
         request.setProductId(currentProduct.getId());
         request.setQuantity(quantity);
         
         Call<ApiResponse<PaymentOrderResponse>> call = ApiClient.getPaymentService().createWechatOrder(request);
         call.enqueue(new Callback<ApiResponse<PaymentOrderResponse>>() {
             @Override
             public void onResponse(Call<ApiResponse<PaymentOrderResponse>> call, Response<ApiResponse<PaymentOrderResponse>> response) {
                 if (response.isSuccessful() && response.body() != null) {
                     ApiResponse<PaymentOrderResponse> apiResponse = response.body();
                     if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                         PaymentOrderResponse.OrderInfo orderInfo = apiResponse.getData().getOrderInfo();
                         if (orderInfo != null) {
                             // 保存订单信息，用于后续验证
                             mOrderInfo = orderInfo;
                             callback.onSuccess(orderInfo);
                         } else {
                             callback.onError("订单信息为空");
                         }
                     } else {
                         String errorMsg = apiResponse.getMessage() != null ? apiResponse.getMessage() : "创建订单失败";
                         callback.onError(errorMsg);
                     }
                 } else {
                     callback.onError("网络请求失败: " + response.code());
                 }
             }
             
             @Override
             public void onFailure(Call<ApiResponse<PaymentOrderResponse>> call, Throwable t) {
                 callback.onError("网络连接失败: " + t.getMessage());
             }
         });
     }
     
     /**
      * 启动微信支付
      */
     private void startWechatPay(PaymentOrderResponse.OrderInfo orderInfo) {
         if (orderInfo == null) {
             Log.e("ProductDetail", "启动微信支付失败: 订单信息为空");
             Toast.makeText(this, "启动支付失败，请重试", Toast.LENGTH_SHORT).show();
             return;
         }
         
         if (!wxApi.isWXAppInstalled()) {
             Log.e("ProductDetail", "启动微信支付失败: 未安装微信客户端");
             Toast.makeText(this, "未安装微信客户端，请安装后重试", Toast.LENGTH_SHORT).show();
             return;
         }
         
         if (wxApi.getWXAppSupportAPI() < 0x21020001) {
            Log.e("ProductDetail", "启动微信支付失败: 微信客户端版本不支持");
            Toast.makeText(this, "微信客户端版本不支持支付功能，请升级微信", Toast.LENGTH_SHORT).show();
            return;
        }
         
         // 获取APP支付参数
         Map<String, String> appPayParams = orderInfo.getAppPayParams();
         if (appPayParams == null || appPayParams.isEmpty()) {
             Log.e("ProductDetail", "启动微信支付失败: 支付参数错误");
             Toast.makeText(this, "支付参数错误，请重试", Toast.LENGTH_SHORT).show();
             return;
         }
         
         // 验证必要的支付参数是否存在
         String[] requiredParams = {"appid", "partnerid", "prepayid", "package", "noncestr", "timestamp", "sign"};
         for (String param : requiredParams) {
             if (appPayParams.get(param) == null || appPayParams.get(param).isEmpty()) {
                 Log.e("ProductDetail", "启动微信支付失败: 缺少必要参数: " + param);
                 Toast.makeText(this, "支付参数不完整，请重试", Toast.LENGTH_SHORT).show();
                 return;
             }
         }
         
         // 构建微信支付请求
         PayReq payReq = new PayReq();
         payReq.appId = appPayParams.get("appid");
         payReq.partnerId = appPayParams.get("partnerid");
         payReq.prepayId = appPayParams.get("prepayid");
         payReq.packageValue = appPayParams.get("package");
         payReq.nonceStr = appPayParams.get("noncestr");
         payReq.timeStamp = appPayParams.get("timestamp");
         payReq.sign = appPayParams.get("sign");
         
         Log.d("ProductDetail", "准备发起微信支付: prepayId=" + payReq.prepayId);
         
         // 发起微信支付
         boolean result = wxApi.sendReq(payReq);
         if (!result) {
             Log.e("ProductDetail", "启动微信支付失败: sendReq返回false");
             Toast.makeText(this, "启动微信支付失败，请重试", Toast.LENGTH_SHORT).show();
         } else {
             Log.d("ProductDetail", "成功向微信客户端发送支付请求");
         }
     }
     
     @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
         super.onActivityResult(requestCode, resultCode, data);
         
         if (requestCode == WECHAT_PAY_REQUEST_CODE) {
             if (data != null) {
                 String payResult = data.getStringExtra("pay_result");
                 int errCode = data.getIntExtra("err_code", -1);
                 
                 Log.d("ProductDetail", "微信支付返回结果: payResult=" + payResult + ", errCode=" + errCode);
                 
                 if (resultCode == RESULT_OK && "success".equals(payResult)) {
                     // 微信支付成功
                     // 可以在这里添加验证支付结果的代码，向服务器确认支付状态
                     verifyWechatPaymentStatus();
                     onPaymentSuccess("微信支付");
                 } else {
                     // 微信支付失败、取消或其他状态
                     String errorMessage = "支付已取消";
                     if (data != null) {
                         if ("cancel".equals(payResult)) {
                             errorMessage = "支付已取消";
                         } else if ("denied".equals(payResult)) {
                             errorMessage = "支付被拒绝";
                         } else if ("error".equals(payResult)) {
                             errorMessage = "支付失败，错误码: " + errCode;
                         }
                     }
                     Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                 }
             } else {
                 // 微信支付失败或取消
                 Toast.makeText(this, "支付处理失败", Toast.LENGTH_SHORT).show();
             }
         }
     }
     
     /**
      * 向服务器验证微信支付结果
      */
     private void verifyWechatPaymentStatus() {
         // 这里实现向服务器验证支付结果的逻辑
         // 为了安全性，在支付完成后向服务器确认支付状态
         Log.d("ProductDetail", "正在向服务器验证微信支付结果...");
         
         if (mOrderInfo != null) {
             // 构建验证请求参数
             Map<String, Object> paymentResult = new HashMap<>();
             paymentResult.put("out_trade_no", mOrderInfo.getOutTradeNo());
             paymentResult.put("total_amount", mOrderInfo.getTotalAmount());
             paymentResult.put("timestamp", System.currentTimeMillis());
             
             // 调用验证接口
             Call<ApiResponse<Map<String, Object>>> call = ApiClient.getApiService().verifyWechatPayment(paymentResult);
             call.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                 @Override
                 public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                     if (response.isSuccessful() && response.body() != null) {
                         ApiResponse<Map<String, Object>> apiResponse = response.body();
                         if (apiResponse.isSuccess()) {
                             Log.d("ProductDetail", "微信支付结果验证成功: " + apiResponse.getData());
                             // 可以在这里处理验证成功后的逻辑，如更新本地订单状态等
                         } else {
                             Log.w("ProductDetail", "微信支付结果验证失败: " + apiResponse.getMessage());
                             // 处理验证失败的情况，可能需要提示用户联系客服
                             Toast.makeText(ProductDetailActivity.this, "支付结果验证失败，请联系客服确认", Toast.LENGTH_SHORT).show();
                         }
                     } else {
                         Log.w("ProductDetail", "微信支付结果验证请求失败");
                     }
                 }
                 
                 @Override
                 public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                     Log.e("ProductDetail", "微信支付结果验证网络错误", t);
                     // 网络错误处理，可以选择在稍后重试或提示用户
                 }
             });
         } else {
             Log.w("ProductDetail", "订单信息为空，无法验证支付结果");
         }
     }
     
     // 保存当前订单信息的变量
     private PaymentOrderResponse.OrderInfo mOrderInfo;

     /**
      * 支付成功回调
      */
     private void onPaymentSuccess(String paymentMethod) {
         // 更新购买数量显示
         int newPurchaseCount = currentProduct.getPurchaseCount() + 1;
         currentProduct.setPurchaseCount(newPurchaseCount);
         productPurchaseCount.setText(newPurchaseCount + "+ 人已购");

         // 显示支付成功提示
         Toast.makeText(this, paymentMethod + "支付成功！感谢您的购买", Toast.LENGTH_LONG).show();

         // 创建订单
         createOrder(paymentMethod);
     }
     
     /**
      * 创建订单并设置状态为待发货
      */
     private void createOrder(String paymentMethod) {
         // 显示加载对话框
         android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
         progressDialog.setMessage("正在创建订单...");
         progressDialog.setCancelable(false);
         progressDialog.show();
         
         // 创建订单请求对象
         Order order = new Order();
         // 创建支付订单请求
        PaymentOrderRequest paymentRequest = new PaymentOrderRequest();
        paymentRequest.setProductId(currentProduct.getId());
        paymentRequest.setQuantity(1); // 默认购买数量为1
        paymentRequest.setTimeoutExpress("30m"); // 30分钟支付超时
        
        // 根据支付方式选择不同的API
        Call<ApiResponse<PaymentOrderResponse>> call;
        if ("alipay".equals(paymentMethod)) {
            call = ApiClient.getApiService().createAlipayOrder(paymentRequest);
        } else {
            call = ApiClient.getApiService().createWechatOrder(paymentRequest);
        }
        
        call.enqueue(new Callback<ApiResponse<PaymentOrderResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PaymentOrderResponse>> call, Response<ApiResponse<PaymentOrderResponse>> response) {
                progressDialog.dismiss();
                
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<PaymentOrderResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        PaymentOrderResponse orderResponse = apiResponse.getData();
                        // 获取订单号
                        String orderId = orderResponse.getOrderInfo() != null ? orderResponse.getOrderInfo().getOutTradeNo() : "unknown";
                        Log.d("ProductDetail", "订单创建成功，订单ID: " + orderId);
                        
                        // 处理支付结果
                        if ("alipay".equals(paymentMethod)) {
                            // 处理支付宝支付
                            handleAlipayResult(orderResponse);
                        } else {
                            // 处理微信支付
                            handleWechatPayResult(orderResponse);
                        }
                    } else {
                        Log.e("ProductDetail", "订单创建失败: " + apiResponse.getMessage());
                        Toast.makeText(ProductDetailActivity.this, "订单创建失败: " + apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e("ProductDetail", "订单创建网络请求失败: " + response.message());
                    Toast.makeText(ProductDetailActivity.this, "网络请求失败，请稍后重试", Toast.LENGTH_LONG).show();
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<PaymentOrderResponse>> call, Throwable t) {
                progressDialog.dismiss();
                Log.e("ProductDetail", "订单创建网络错误", t);
                Toast.makeText(ProductDetailActivity.this, "网络连接失败，请检查网络后重试", Toast.LENGTH_LONG).show();
            }
        });
     }
     
     /**
      * 从SharedPreferences获取用户ID
      */
     private String getUserIdFromSharedPreferences() {
         SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
         return preferences.getString("user_id", "default_user_id");
     }
     
     /**
     * 从SharedPreferences获取用户名
     */
    private String getUserNameFromSharedPreferences() {
        SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return preferences.getString("user_name", "用户");
    }
    
    /**
     * 处理支付宝支付结果
     */
    private void handleAlipayResult(PaymentOrderResponse response) {
        if (response != null && response.getOrderString() != null) {
            try {
                // 这里应该调用支付宝SDK进行支付
                // 示例代码：AlipaySDK.pay(response.getOrderString(), true, null);
                Log.d("ProductDetail", "支付宝订单字符串: " + response.getOrderString());
                Toast.makeText(this, "已准备支付宝支付", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("ProductDetail", "支付宝支付异常", e);
                Toast.makeText(this, "支付初始化失败，请稍后重试", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "获取支付信息失败", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 处理微信支付结果
     */
    private void handleWechatPayResult(PaymentOrderResponse response) {
        if (response != null && response.getOrderInfo() != null) {
            try {
                // 这里应该调用微信支付SDK进行支付
                // 示例代码需要结合具体的微信支付SDK实现
                Log.d("ProductDetail", "微信支付信息: " + response.getOrderInfo().getOutTradeNo());
                Toast.makeText(this, "已准备微信支付", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("ProductDetail", "微信支付异常", e);
                Toast.makeText(this, "支付初始化失败，请稍后重试", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "获取支付信息失败", Toast.LENGTH_LONG).show();
        }
    }
}