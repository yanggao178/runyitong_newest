package com.wenxing.runyitong.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.InputStream;
import android.graphics.drawable.Drawable;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.model.Book;


import java.util.List;
import com.wenxing.runyitong.api.ApiClient;

public class BookshelfAdapter extends RecyclerView.Adapter<BookshelfAdapter.BookViewHolder> {
    private static final String TAG = "BookshelfAdapter";
    private List<Book> books;
    private Context context;
    private OnBookClickListener onBookClickListener;

    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    public BookshelfAdapter(Context context, List<Book> books) {
        this.context = context;
        this.books = books;
        Log.d(TAG, "BookshelfAdapter created with " + (books != null ? books.size() : 0) + " books");
    }

    public void setOnBookClickListener(OnBookClickListener listener) {
        this.onBookClickListener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.bookshelf_item, parent, false);
        Log.d(TAG, "Creating new BookViewHolder");
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = books.get(position);
        Log.d(TAG, "Binding book: " + book.getName() + " at position " + position);
        
        holder.bookName.setText(book.getName());
        holder.bookAuthor.setText(book.getAuthor());
        
        // 使用Glide加载书籍封面
        if (book.getCoverUrl() != null && !book.getCoverUrl().isEmpty()) {
            // 获取完整的书籍图片URL并处理格式
            String completeCoverUrl = getCompleteCoverUrl(book.getCoverUrl());
            Log.d(TAG, "Loading cover for book: " + book.getName() + " with complete URL: " + completeCoverUrl);
            
            Glide.with(context)
                    .load(completeCoverUrl)
                    .apply(new RequestOptions()
                            .centerCrop() // 确保图片充满整个ImageView
                            .override(400, 600) // 设置图片大小，提高加载速度
                            .transform(new RoundedCorners(12)) // 圆角处理
                            .placeholder(R.drawable.ic_book_placeholder) // 加载中的占位图
                            .error(R.drawable.ic_book_placeholder)) // 加载失败的占位图
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load book cover: " + completeCoverUrl, e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Successfully loaded book cover: " + completeCoverUrl);
                            return false;
                        }
                    })
                    .into(holder.bookCover);
        } else {
            Log.d(TAG, "No cover URL for book: " + book.getName());
            holder.bookCover.setImageResource(R.drawable.ic_book_placeholder);
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onBookClickListener != null) {
                onBookClickListener.onBookClick(book);
            }
        });
    }
    
    /**
     * 处理并获取完整的书籍封面URL
     * 确保URL格式正确，包含必要的协议前缀、IP地址和端口号
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
        
        // 使用ApiClient中的BASE_URL常量（包含IP地址和端口号）
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

    @Override
    public int getItemCount() {
        int count = books != null ? books.size() : 0;
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    public void updateBooks(List<Book> newBooks) {
        Log.d(TAG, "Updating books list. New size: " + (newBooks != null ? newBooks.size() : 0));
        this.books = newBooks;
        notifyDataSetChanged();
    }

    public static class BookViewHolder extends RecyclerView.ViewHolder {
        ImageView bookCover;
        TextView bookName;
        TextView bookAuthor;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookCover = itemView.findViewById(R.id.book_cover);
            bookName = itemView.findViewById(R.id.book_title);
            bookAuthor = itemView.findViewById(R.id.book_author);
        }
    }
}