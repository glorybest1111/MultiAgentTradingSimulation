package com.trading.auth;

/**
 * 用户角色：区分管理员与普通用户。
 * 只有管理员拥有发布新闻、查看全市场数据等权限。
 */
public enum Role {

    ADMIN("管理员"),
    USER("普通用户");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
