package com.wenxing.runyitong.api;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * 注册响应模型类
 */
public class RegisterResponse implements Serializable {
    @SerializedName("user_id")
    private Long userId;
    
    @SerializedName("username")
    private String username;
    
    @SerializedName("phone")
    private String phone;
    
    @SerializedName("full_name")
    private String fullName;
    
    @SerializedName("message")
    private String message;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public String toString() {
        return "RegisterResponse{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", phone='" + phone + '\'' +
                ", fullName='" + fullName + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}