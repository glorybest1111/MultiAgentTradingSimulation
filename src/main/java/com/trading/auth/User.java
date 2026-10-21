package com.trading.auth;

/**
 * 系统用户。
 *
 * <p>密码采用明文存储仅为了课程演示方便；真实系统应使用
 * BCrypt/argon2 加盐哈希并接入外部身份源。
 */
public class User {

    private final String username;
    private final String password;
    private final Role role;
    /** 该用户在仿真中的账户名（普通用户与其交易者同名） */
    private final String traderName;

    public User(String username, String password, Role role) {
        this(username, password, role, username);
    }

    public User(String username, String password, Role role, String traderName) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.traderName = traderName;
    }

    public String getUsername() {
        return username;
    }

    public boolean matches(String candidate) {
        return password != null && password.equals(candidate);
    }

    public Role getRole() {
        return role;
    }

    public String getTraderName() {
        return traderName;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public String toString() {
        return username + "(" + role.getDisplayName() + ")";
    }
}
