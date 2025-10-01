package com.wenxing.runyitong.model;

/**
 * 快递下单结果模型
 */
public class ExpressOrderResult {
    private String orderCode;    // 订单编码
    private String shipperCode;  // 快递公司编码
    private String logisticCode; // 物流单号
    private boolean success;     // 是否成功
    private String reason;       // 原因
    private String resultCode;   // 结果码

    public ExpressOrderResult() {
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public void setShipperCode(String shipperCode) {
        this.shipperCode = shipperCode;
    }

    public String getLogisticCode() {
        return logisticCode;
    }

    public void setLogisticCode(String logisticCode) {
        this.logisticCode = logisticCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    @Override
    public String toString() {
        return "ExpressOrderResult{" +
                "orderCode='" + orderCode + '\'' +
                ", shipperCode='" + shipperCode + '\'' +
                ", logisticCode='" + logisticCode + '\'' +
                ", success=" + success +
                ", reason='" + reason + '\'' +
                ", resultCode='" + resultCode + '\'' +
                '}';
    }
}