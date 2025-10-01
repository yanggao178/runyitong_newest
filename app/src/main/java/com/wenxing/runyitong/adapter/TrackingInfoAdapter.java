package com.wenxing.runyitong.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wenxing.runyitong.R;
import com.wenxing.runyitong.model.TrackingInfo;

import java.util.List;

public class TrackingInfoAdapter extends RecyclerView.Adapter<TrackingInfoAdapter.TrackingViewHolder> {

    private Context context;
    private List<TrackingInfo.TraceInfo> traceList;

    public TrackingInfoAdapter(Context context, List<TrackingInfo.TraceInfo> traceList) {
        this.context = context;
        this.traceList = traceList;
    }

    @NonNull
    @Override
    public TrackingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_tracking_info, parent, false);
        return new TrackingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackingViewHolder holder, int position) {
        try {
            if (traceList == null || traceList.isEmpty() || position >= traceList.size()) {
                return;
            }

            TrackingInfo.TraceInfo traceInfo = traceList.get(position);
            if (traceInfo == null) {
                holder.timeTextView.setText("");
                holder.descriptionTextView.setText("无效的物流记录");
                return;
            }
            
            // 安全设置文本内容，避免空指针异常
            String timeStr = traceInfo.getTime() != null ? traceInfo.getTime() : "";
            String contentStr = traceInfo.getContent() != null ? traceInfo.getContent() : "暂无描述";
            holder.timeTextView.setText(timeStr);
            holder.descriptionTextView.setText(contentStr);

            // 第一个节点（最新的物流信息）显示为激活状态
            boolean isFirstItem = position == 0;
            
            // 安全设置背景资源
            try {
                holder.dotView.setBackgroundResource(isFirstItem ? R.drawable.ic_tracking_dot_active : R.drawable.ic_tracking_dot_inactive);
                holder.lineView.setVisibility(isFirstItem ? View.INVISIBLE : View.VISIBLE);
            } catch (Exception e) {
                // 忽略资源设置错误
            }

            // 安全设置文本颜色
            try {
                int textColor = isFirstItem ? 
                        context.getResources().getColor(R.color.colorPrimary) : 
                        context.getResources().getColor(R.color.textSecondary);
                holder.timeTextView.setTextColor(textColor);
                holder.descriptionTextView.setTextColor(textColor);
            } catch (Exception e) {
                // 忽略颜色设置错误，使用默认颜色
            }
        } catch (Exception e) {
            // 捕获可能出现的所有异常，避免影响整个列表的渲染
            if (holder != null) {
                holder.timeTextView.setText("");
                holder.descriptionTextView.setText("物流信息加载异常");
            }
        }
    }

    @Override
    public int getItemCount() {
        return traceList != null ? traceList.size() : 0;
    }

    static class TrackingViewHolder extends RecyclerView.ViewHolder {
        TextView timeTextView;
        TextView descriptionTextView;
        View dotView;
        View lineView;

        public TrackingViewHolder(@NonNull View itemView) {
            super(itemView);
            timeTextView = itemView.findViewById(R.id.tracking_time);
            descriptionTextView = itemView.findViewById(R.id.tracking_description);
            dotView = itemView.findViewById(R.id.tracking_dot);
            lineView = itemView.findViewById(R.id.tracking_line);
        }
    }
}