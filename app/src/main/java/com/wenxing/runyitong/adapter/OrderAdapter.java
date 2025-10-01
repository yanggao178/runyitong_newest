package com.wenxing.runyitong.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.activity.ExpressTrackingActivity;
import com.wenxing.runyitong.model.Order;
import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private Context context;
    private List<Order> orderList;

    public OrderAdapter(Context context, List<Order> orderList) {
        this.context = context;
        this.orderList = orderList;
    }
    
    /**
     * 更新订单列表数据
     * @param newOrderList 新的订单列表
     */
    public void updateData(List<Order> newOrderList) {
        this.orderList = newOrderList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.order_item, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);
        holder.orderId.setText("订单号: " + order.getOrderId());
        holder.productName.setText(order.getProductName());
        holder.orderStatus.setText(order.getStatus());
        holder.orderPrice.setText(order.getPrice());
        
        // 设置订单状态文本颜色
        switch (order.getStatus()) {
            case "待发货":
                holder.orderStatus.setTextColor(context.getResources().getColor(R.color.colorPrimary));
                break;
            case "待收货":
                holder.orderStatus.setTextColor(context.getResources().getColor(R.color.colorAccent));
                // 待收货状态添加点击事件，跳转到物流跟踪页面
                setupItemClickListener(holder.itemView, order);
                break;
            case "已完成":
                holder.orderStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
                // 已完成状态也可以查看物流
                setupItemClickListener(holder.itemView, order);
                break;
            default:
                holder.orderStatus.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return orderList != null ? orderList.size() : 0;
    }

    /**
     * 为订单项设置点击事件，跳转到物流跟踪页面
     */
    private void setupItemClickListener(View itemView, final Order order) {
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 这里暂时不传递真实的物流信息，用户可以在物流页面手动输入或选择
                // 在实际应用中，可以从订单对象中获取物流单号和快递公司信息
                ExpressTrackingActivity.startExpressTrackingActivity(
                        (AppCompatActivity) context,
                        order.getOrderId(),
                        null, // 实际应用中应从订单获取物流单号
                        null, // 实际应用中应从订单获取快递公司编码
                        null  // 实际应用中应从订单获取快递公司名称
                );
            }
        });
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView orderId;
        TextView productName;
        TextView orderStatus;
        TextView orderPrice;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.order_id);
            productName = itemView.findViewById(R.id.product_name);
            orderStatus = itemView.findViewById(R.id.order_status);
            orderPrice = itemView.findViewById(R.id.order_price);
        }
    }
}