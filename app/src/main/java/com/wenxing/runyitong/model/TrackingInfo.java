package com.wenxing.runyitong.model;

import java.util.List;

/**
 * 物流轨迹信息模型
 */
public class TrackingInfo {
    private String orderSn;      // 物流单号
    private String shipperCode;  // 快递公司编码
    private String shipperName;  // 快递公司名称
    private List<TraceInfo> traces;  // 物流轨迹列表
    private String status;       // 物流状态
    private boolean success;     // 是否成功
    private String errorMsg;     // 失败原因

    public TrackingInfo() {
    }

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public void setShipperCode(String shipperCode) {
        this.shipperCode = shipperCode;
    }

    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    public List<TraceInfo> getTraces() {
        return traces;
    }

    public void setTraces(List<TraceInfo> traces) {
        this.traces = traces;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    /**
     * 物流轨迹详情信息
     */
    public static class TraceInfo {
        private String time;    // 时间
        private String content; // 描述
        private String description; // 与后端保持一致的字段名

        public TraceInfo() {
        }

        public TraceInfo(String time, String content) {
            this.time = time;
            this.content = content;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getContent() {
            // 如果content为空，尝试返回description（后端字段名）
            return content != null ? content : description;
        }

        public void setContent(String content) {
            this.content = content;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "TraceInfo{" +
                    "time='" + time + "'" +
                    ", content='" + content + "'" +
                    ", description='" + description + "'" +
                    "}";
        }
    }

    @Override
    public String toString() {
        return "TrackingInfo{" +
                "orderSn='" + orderSn + '\'' +
                ", shipperCode='" + shipperCode + '\'' +
                ", shipperName='" + shipperName + '\'' +
                ", traces=" + traces +
                ", status='" + status + '\'' +
                ", success=" + success +
                ", errorMsg='" + errorMsg + '\'' +
                '}';
    }
}