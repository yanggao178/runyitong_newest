package com.wenxing.runyitong.api;

import java.io.Serializable;

/**
 * 短信登录请求模型
 * 与后端SmsLoginRequest模型保持一致
 */
public class SmsLoginRequest implements Serializable {
    private String phone;
    private String verification_code;

    /**
     * 默认构造函数
     */
    public SmsLoginRequest() {}

    /**
     * 构造函数
     * @param phone 手机号码
     * @param verification_code 验证码
     */
    public SmsLoginRequest(String phone, String verification_code) {
        this.phone = phone;
        this.verification_code = verification_code;
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

    /**
     * 获取验证码
     * @return 验证码
     */
    public String getVerification_code() {
        return verification_code;
    }

    /**
     * 设置验证码
     * @param verification_code 验证码
     */
    public void setVerification_code(String verification_code) {
        this.verification_code = verification_code;
    }

    @Override
    public String toString() {
        return "SmsLoginRequest{" +
                "phone='" + phone + '\'' +
                ", verification_code='[PROTECTED]'" +
                '}';
    }
}