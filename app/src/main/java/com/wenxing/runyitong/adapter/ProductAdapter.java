package com.wenxing.runyitong.adapter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView.ScaleType;

import com.wenxing.runyitong.activity.PharmacyProductsActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.wenxing.runyitong.R;
import com.wenxing.runyitong.model.Product;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private Context context;
    private List<Product> productList;

    public ProductAdapter(Context context, List<Product> productList) {
        this.context = context;
        this.productList = productList;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.product_item, parent, false);
        return new ProductViewHolder(view);
    }

    private OnItemClickListener onItemClickListener;

    // 点击事件接口
    public interface OnItemClickListener {
        void onItemClick(Product product);
    }

    // 设置点击事件监听
    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        if (productList == null || position < 0 || position >= productList.size()) {
            return;
        }
        
        Product product = productList.get(position);
        if (product == null) {
            return;
        }
        
        // 安全设置产品名称
        if (holder.productName != null) {
            holder.productName.setText(product.getName() != null ? product.getName() : "未知商品");
        }
        
        // 安全设置价格
        if (holder.productPrice != null) {
            try {
                holder.productPrice.setText(String.format("%.2f", product.getPrice()));
            } catch (Exception e) {
                holder.productPrice.setText("0.00");
            }
        }
        
        // 安全设置非会员价（原价）
        if (holder.nonMemberPrice != null) {
            try {
                // 获取原价
                double originalPrice = product.getOriginalPrice();
                // 设置原价文本
                holder.nonMemberPrice.setText(String.format("%.2f", originalPrice));
                // 添加删除线样式
                holder.nonMemberPrice.setPaintFlags(holder.nonMemberPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } catch (Exception e) {
                holder.nonMemberPrice.setText("0.00");
            }
        }
        
        // 设置非会员价标签
        if (holder.nonMemberPriceLabel != null) {
            holder.nonMemberPriceLabel.setVisibility(View.VISIBLE);
        }
        
        // 安全设置描述
        if (holder.productDescription != null) {
            holder.productDescription.setText(product.getDescription() != null ? product.getDescription() : "暂无描述");
        }

        // 安全设置药店名
        if (holder.pharmacyName != null) {
            String pharmacyText = "药店: " + (product.getPharmacyName() != null ? product.getPharmacyName() : "未知");
            holder.pharmacyName.setText(pharmacyText);
        }

        // 安全加载商品图片
        if (holder.productImage != null) {
            String imageUrl = product.getFeaturedImageFile();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                try {
                    // 使用Glide加载图片，设置固定宽高和缩放类型，确保在所有界面中显示一致
                    Glide.with(context)
                            .load(imageUrl)
                            .apply(new RequestOptions()
                                    .placeholder(R.drawable.ic_launcher_background) // 占位图
                                    .error(R.drawable.ic_launcher_background) // 加载错误时显示的图
                                    .centerCrop() // 中心裁剪，保持图片比例
                                    .override(400, 400)) // 设置固定宽高，确保在所有界面中显示一致
                            .into(holder.productImage);
                } catch (Exception e) {
                    Log.e("ProductAdapter", "加载商品图片失败: " + e.getMessage());
                    holder.productImage.setImageResource(R.drawable.ic_launcher_background);
                }
            } else {
                holder.productImage.setImageResource(R.drawable.ic_launcher_background);
            }
        }

        // 添加点击事件
        if (holder.itemView != null) {
            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null && product != null) {
                    onItemClickListener.onItemClick(product);
                }
            });
        }
        
        // 为进店标识和箭头图标添加点击事件
        View.OnClickListener enterPharmacyClickListener = v -> {
            try {
                // 增强上下文检查
                if (context == null) {
                    android.util.Log.e("ProductAdapter", "上下文为空，无法启动药店商品页面");
                    return;
                }
                
                // 检查context是否为有效的Activity或者ApplicationContext
                if (!(context instanceof Activity) && !(context instanceof Application)) {
                    android.util.Log.e("ProductAdapter", "上下文类型无效: " + context.getClass().getName());
                    return;
                }
                
                if (product == null) {
                    android.util.Log.e("ProductAdapter", "商品信息为空，无法获取药店名称");
                    return;
                }
                
                if (product.getPharmacyName() == null || product.getPharmacyName().isEmpty()) {
                    android.util.Log.e("ProductAdapter", "药店名称为空");
                    return;
                }
                
                // 尝试加载PharmacyProductsActivity类，确保它可以被正确加载
                try {
                    Class.forName("com.wenxing.runyitong.activity.PharmacyProductsActivity");
                } catch (ClassNotFoundException e) {
                    android.util.Log.e("ProductAdapter", "找不到PharmacyProductsActivity类", e);
                    return;
                }
                
                // 创建Intent并启动Activity
                Intent intent = new Intent(context, PharmacyProductsActivity.class);
                intent.putExtra(PharmacyProductsActivity.EXTRA_PHARMACY_NAME, product.getPharmacyName());
                
                // 根据上下文类型决定是否添加FLAG_ACTIVITY_NEW_TASK
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    android.util.Log.d("ProductAdapter", "使用ApplicationContext启动Activity，添加FLAG_ACTIVITY_NEW_TASK标志");
                } else {
                    android.util.Log.d("ProductAdapter", "使用Activity上下文启动Activity");
                }
                
                context.startActivity(intent);
            } catch (Exception e) {
                // 捕获所有异常，并记录详细错误信息
                android.util.Log.e("ProductAdapter", "点击进店标识出错: " + e.getMessage(), e);
                // 显示友好的错误提示
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "无法打开药店页面，请稍后再试", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        };
        
        if (holder.pharmacyEnterText != null) {
            holder.pharmacyEnterText.setOnClickListener(enterPharmacyClickListener);
        }
        
        if (holder.pharmacyArrowIcon != null) {
            holder.pharmacyArrowIcon.setOnClickListener(enterPharmacyClickListener);
        }
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productName;
        TextView productPrice;
        TextView productDescription;
        TextView pharmacyName;
        TextView pharmacyEnterText;
        ImageView pharmacyArrowIcon;
        TextView nonMemberPriceLabel;
        TextView nonMemberPrice;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.product_image);
            productName = itemView.findViewById(R.id.product_name);
            productPrice = itemView.findViewById(R.id.product_price);
            productDescription = itemView.findViewById(R.id.product_description);
            pharmacyName = itemView.findViewById(R.id.pharmacy_name);
            pharmacyEnterText = itemView.findViewById(R.id.pharmacy_enter_text);
            pharmacyArrowIcon = itemView.findViewById(R.id.pharmacy_arrow_icon);
            nonMemberPriceLabel = itemView.findViewById(R.id.non_member_price_label);
            nonMemberPrice = itemView.findViewById(R.id.non_member_price);
        }
    }
}