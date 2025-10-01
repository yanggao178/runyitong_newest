package com.wenxing.runyitong.model;

import com.google.gson.annotations.SerializedName;

/**
 * 用户信息模型类
 */
public class UserInfo {
    @SerializedName("id")
    private String id;
    
    @SerializedName("name")
    private String name;
    
    /**
     * 获取用户ID
     * @return 用户ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 设置用户ID
     * @param id 用户ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * 获取用户名
     * @return 用户名
     */
    public String getName() {
        return name;
    }
    
    /**
     * 设置用户名
     * @param name 用户名
     */
    public void setName(String name) {
        this.name = name;
    }
}