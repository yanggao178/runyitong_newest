package com.wenxing.runyitong.model;

/**
 * 快递公司信息模型
 */
public class ExpressCompany {
    private String name;    // 快递公司名称
    private String code;    // 快递公司编码

    public ExpressCompany() {
    }

    public ExpressCompany(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "ExpressCompany{" +
                "name='" + name + '\'' +
                ", code='" + code + '\'' +
                '}';
    }
}