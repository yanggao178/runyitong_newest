package com.wenxing.runyitong.adapter;

import android.animation.AnimatorInflater;
import android.animation.StateListAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.api.ApiClient;
import com.wenxing.runyitong.model.Video;

import java.util.List;

/**
 * 视频列表适配器
 * 用于在RecyclerView中显示视频列表项
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private Context context;
    private List<Video> videos;
    private OnVideoClickListener onVideoClickListener;
    private static final int CORNER_RADIUS = 8;

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    public VideoAdapter(Context context, List<Video> videos) {
        this.context = context;
        this.videos = videos;
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.onVideoClickListener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = videos.get(position);
        if (video == null) return;

        // 设置视频标题
        holder.titleTextView.setText(video.getTitle());

        // 设置视频时长
        holder.durationTextView.setText(video.getFormattedDuration());

        // 设置视频封面，添加圆角效果和平滑过渡
        RequestOptions requestOptions = new RequestOptions()
                .transform(new RoundedCorners(CORNER_RADIUS))
                .placeholder(R.drawable.video_placeholder)
                .error(R.drawable.video_placeholder)
                .centerCrop();
                
        // 处理视频封面图片URL
        String coverImageUrl = video.getCoverImage();
        if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
            // 检查是否已经是完整的URL
            if (!coverImageUrl.startsWith("http://") && !coverImageUrl.startsWith("https://")) {
                // 从ApiClient获取基础URL
                String baseUrl = ApiClient.getBaseUrl();
                // 确保URL格式正确，避免重复的斜杠
                if (coverImageUrl.startsWith("/")) {
                    coverImageUrl = baseUrl + coverImageUrl.substring(1);
                } else {
                    coverImageUrl = baseUrl + coverImageUrl;
                }
            }
        }
                
        // 使用Glide加载图片并添加过渡动画
        Glide.with(context)
                .load(coverImageUrl)
                .apply(requestOptions)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
                .into(holder.coverImageView);

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onVideoClickListener != null) {
                onVideoClickListener.onVideoClick(video);
            }
        });
    }

    @Override
    public int getItemCount() {
        return videos != null ? videos.size() : 0;
    }

    /**
     * 更新视频列表数据
     */
    public void updateVideos(List<Video> newVideos) {
        if (newVideos != null) {
            this.videos = newVideos;
            notifyDataSetChanged();
        }
    }

    /**
     * 视频ViewHolder
     */
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImageView;
        TextView titleTextView;
        TextView durationTextView;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImageView = itemView.findViewById(R.id.video_cover);
            titleTextView = itemView.findViewById(R.id.video_title);
            durationTextView = itemView.findViewById(R.id.video_duration);
            
            // 设置卡片点击反馈效果
            try {
                StateListAnimator animator = AnimatorInflater.loadStateListAnimator(
                        itemView.getContext(), R.animator.video_item_press);
                itemView.setStateListAnimator(animator);
            } catch (Exception e) {
                // 如果动画资源加载失败，使用简单的点击效果
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setBackgroundResource(R.drawable.tab_button_selected);
            }
            
            // 设置卡片点击反馈颜色
            // 使用更现代的方式获取Drawable，避免弃用警告
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                itemView.setForeground(itemView.getContext().getDrawable(R.drawable.selectable_background));
            } else {
                // 兼容旧版本API
                itemView.setForeground(itemView.getContext().getResources().getDrawable(R.drawable.selectable_background));
            }
        }
    }
}