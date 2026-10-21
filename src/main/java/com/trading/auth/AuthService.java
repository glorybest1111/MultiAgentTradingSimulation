package com.trading.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 认证服务：登录、会话校验、单点登录互踢、角色鉴权。
 *
 * <p>在线会话用 {@link ConcurrentHashMap} 维护，保证 GUI 线程与 Timer 线程并发安全。
 * 同一用户名再次登录会强制使旧会话失效 —— 这就是需求里的「互踢」。
 */
public class AuthService {

    /** 内置演示账号；真实系统应接入数据库/外部身份源 */
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Session> online = new ConcurrentHashMap<>();
    private final AtomicLong tokenGenerator = new AtomicLong(1);

    public AuthService() {
        // 直接在构造期装填（不调用实例方法，避免 this 在完全初始化前逃逸）
        for (User user : defaultUsers()) {
            users.put(user.getUsername(), user);
        }
    }

    private static List<User> defaultUsers() {
        return Arrays.asList(
                new User("admin", "admin123", Role.ADMIN, "AdminDesk"),
                new User("trader", "trader123", Role.USER, "Trader01"),
                new User("Alice", "alice123", Role.USER, "Alice"),
                new User("Bob", "bob123", Role.USER, "Bob"));
    }

    public void register(User user) {
        users.put(user.getUsername(), user);
    }

    /**
     * 登录。若该账号已在别处登录，旧会话会被立即踢下线。
     *
     * @return 新会话；用户名或密码错误时返回 null
     */
    public Session login(String username, String password) {
        User user = users.get(username);
        if (user == null || !user.matches(password)) {
            return null;
        }
        Session previous = online.remove(username);
        if (previous != null) {
            previous.invalidate("account logged in elsewhere");
        }
        String token = "T" + tokenGenerator.getAndIncrement() + "-" + username;
        Session session = new Session(token, user);
        online.put(username, session);
        return session;
    }

    public void logout(Session session) {
        if (session == null) {
            return;
        }
        Session current = online.get(session.getUser().getUsername());
        if (current == session) {
            online.remove(session.getUser().getUsername());
        }
        session.invalidate("logout");
    }

    /** 会话是否仍然在线且有效 */
    public boolean isValid(Session session) {
        return session != null && session.isValid()
                && session == online.get(session.getUser().getUsername());
    }

    /** 当前在线会话快照（管理员监控面板用） */
    public Collection<Session> onlineSessions() {
        return new ArrayList<>(online.values());
    }

    public List<User> allUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * 权限校验：必须是登录且有效的管理员会话。
     *
     * @throws SecurityException 非管理员时抛出，用于阻止普通用户发布新闻等高危操作
     */
    public void requireAdmin(Session session) {
        if (!isValid(session)) {
            throw new SecurityException("会话已失效，请重新登录");
        }
        if (!session.getUser().isAdmin()) {
            throw new SecurityException("权限不足：该操作仅限管理员");
        }
    }

    public void requireValid(Session session) {
        if (!isValid(session)) {
            throw new SecurityException("会话已失效，请重新登录");
        }
    }
}
