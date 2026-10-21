package com.trading.auth;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 登录会话。
 *
 * <p>互踢（单点登录）的关键：同一账号再次登录时，{@code AuthService} 会把旧会话
 * 标记为 invalid 并触发 {@link #invalidListener}，由 UI 立即跳回登录界面。
 */
public class Session {

    private final String token;
    private final User user;
    private final long createdAtMillis;
    private final AtomicBoolean valid = new AtomicBoolean(true);
    private volatile Runnable invalidListener;

    public Session(String token, User user) {
        this.token = token;
        this.user = user;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getToken() {
        return token;
    }

    public User getUser() {
        return user;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /** 设置被踢下线时的回调（由 UI 注册） */
    public void setInvalidListener(Runnable listener) {
        this.invalidListener = listener;
    }

    /** 标记为失效并执行回调 */
    public void invalidate(String reason) {
        if (valid.compareAndSet(true, false)) {
            Runnable listener = invalidListener;
            if (listener != null) {
                listener.run();
            }
            System.out.println("[Auth] 会话失效: " + user.getUsername() + " - " + reason);
        }
    }

    public boolean isValid() {
        return valid.get();
    }
}
