package com.wenxing.runyitong.api;

import java.io.Serializable;

/**
 * 短信验证码请求对象
 * 与后端SmsCodeRequest模型保持一致
 */
public class SmsCodeRequest implements Serializable {
    private String phone;

    /**
     * 无参构造函数，用于JSON解析
     */
    public SmsCodeRequest() {}

    /**
     * 有参构造函数
     * @param phone 手机号码
     */
    public SmsCodeRequest(String phone) {
        this.phone = phone;
    }

    /**
     * 获取手机号码
     * @return 手机号码
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置手机号码
     * @param phone 手机号码
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public String toString() {
        return "SmsCodeRequest{" +
                "phone='" + phone + '\'' +
                '}';
    }
}