package com.trading.ui;

import com.trading.account.Account;
import com.trading.agent.HighFrequencyTrader;
import com.trading.agent.MarketMakerAgent;
import com.trading.agent.MomentumTrader;
import com.trading.agent.UserTerminalAgent;
import com.trading.agent.ValueTrader;
import com.trading.auth.AuthService;
import com.trading.auth.Session;
import com.trading.auth.User;
import com.trading.llm.HttpLLMClient;
import com.trading.llm.LLMClient;
import com.trading.llm.MockLLMClient;
import com.trading.market.NewsEngine;
import com.trading.market.NewsItem;
import com.trading.market.NewsListener;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.simulation.MarketSimulation;
import com.trading.simulation.SimulationConfig;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JavaFX 前端应用外壳：负责仿真生命周期、会话管理、权限校验与新闻广播。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仿真由 JavaFX Timeline 驱动（每 1 秒一期），所有 UI 更新都在 FX 线程完成</li>
 *   <li>认证/会话集中在 {@link AuthService}，同账号二次登录会互斥（互踢）</li>
 *   <li>高危操作走 {@link AuthService#requireAdmin(Session)}，普通用户发布新闻会被拒绝</li>
 *   <li>新闻通过 {@link NewsListener} 由引擎推送，UI 只做展示，不参与撮合</li>
 * </ul>
 */
public class TradingPlatformApplication extends Application {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AuthService authService = new AuthService();
    private final Map<String, UserTerminalAgent> terminalAgents = new ConcurrentHashMap<>();
    private final List<NewsListener> newsListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> tickListeners = new CopyOnWriteArrayList<>();

    private SimulationConfig config;
    private MarketSimulation simulation;
    private LLMClient llmClient;
    private Stage primaryStage;
    private int tickCounter;
    private javafx.animation.Timeline clock;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        buildSimulation();
        primaryStage.setTitle("多智能体金融模拟平台");
        showLogin(null);
        startClock();
        primaryStage.show();
    }

    // ---------------- 仿真装配 ----------------

    private void buildSimulation() {
        config = new SimulationConfig();
        config.experimentName = "gui-session";
        config.seed = 42;
        config.ticks = Integer.MAX_VALUE;       // GUI 模式持续运行，由 Timeline 推进
        config.dashboardInterval = 0;           // 图形界面不需要控制台 Dashboard
        config.circuitBreakerHaltTicks = 5;
        simulation = new MarketSimulation(config);
        simulation.addAgent(new MomentumTrader());
        simulation.addAgent(new ValueTrader());
        simulation.addAgent(new HighFrequencyTrader());
        simulation.addAgent(new MarketMakerAgent());
        llmClient = createLlmClient();
        simulation.getNewsEngine().addListener(item -> {
            String text = "[" + TIME.format(item.getTime()) + "] " + item.getHeadline()
                    + "  (impact " + String.format("%+.2f", item.getImpact()) + ")";
            for (NewsListener listener : newsListeners) {
                try {
                    listener.onNews(item);
                } catch (RuntimeException ignored) {
                    // UI 已关闭时忽略
                }
            }
            System.out.println("[News] " + text);
        });
    }

    /** 优先使用真实大模型；未配置 Key 时自动降级为本地 Mock（离线可演示） */
    private LLMClient createLlmClient() {
        LLMClient http = new HttpLLMClient();
        return http.isAvailable() ? http : new MockLLMClient();
    }

    private void startClock() {
        clock = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> stepSimulation()));
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clock.play();
    }

    private void stepSimulation() {
        if (simulation == null) {
            return;
        }
        try {
            simulation.step(++tickCounter);
            for (Runnable listener : tickListeners) {
                try {
                    listener.run();
                } catch (RuntimeException ignored) {
                    // 单个面板刷新失败不影响其他面板
                }
            }
        } catch (RuntimeException e) {
            System.err.println("[Simulation] tick 异常: " + e.getMessage());
        }
    }

    // ---------------- 会话与互踢 ----------------

    private void showLogin(String message) {
        LoginView loginView = new LoginView(authService, (username, password) -> onLogin(username, password), message);
        primaryStage.setScene(loginView.getScene());
        primaryStage.setTitle("登录 - 多智能体金融模拟平台");
    }

    private void onLogin(String username, String password) {
        Session session = authService.login(username, password);
        if (session == null) {
            showLogin("用户名或密码错误");
            return;
        }
        User user = session.getUser();
        // 互踢：该账号再次登录时旧会话立即失效并被跳回登录页
        session.setInvalidListener(() -> Platform.runLater(() -> onKicked(user)));

        if (user.isAdmin()) {
            AdminConsoleView view = new AdminConsoleView(this, session);
            view.show();
        } else {
            UserTerminalView view = new UserTerminalView(this, ensureTerminalAgent(user), session);
            view.show();
        }
        primaryStage.hide();
    }

    private void onKicked(User user) {
        System.out.println("[Auth] " + user.getUsername() + " 被踢下线");
        showLogin("您的账号已在其他终端登录，当前会话已失效");
        primaryStage.show();
        primaryStage.toFront();
    }

    public void logout(Session session) {
        authService.logout(session);
        Platform.runLater(() -> {
            showLogin("已安全退出");
            primaryStage.show();
        });
    }

    /**
     * 为登录用户准备交易账户：同名 Agent 只注册一次，避免重复开户。
     * AI 托管复用同一个账户（UserTerminalAgent 内部切换值机方）。
     */
    public UserTerminalAgent ensureTerminalAgent(User user) {
        return terminalAgents.computeIfAbsent(user.getUsername(), key -> {
            UserTerminalAgent agent = new UserTerminalAgent(user.getTraderName(), llmClient,
                    config.initialCash, config.initialPrice);
            simulation.addAgent(agent);
            return agent;
        });
    }

    // ---------------- 交易能力（受权限保护） ----------------

    public void placeUserOrder(Session session, Side side, OrderType type, int quantity, double price) {
        authService.requireValid(session);
        String traderName = session.getUser().getTraderName();
        Order order = new Order(config.symbol, side, type, price, quantity, traderName);
        List<com.trading.model.Trade> trades = simulation.submitOrder(order);
        // 人工下单同样计入画像统计，AI 托管接管后能看到用户真实的操作习惯
        UserTerminalAgent terminal = terminalAgents.get(session.getUser().getUsername());
        if (terminal != null) {
            terminal.getCustodyAgent().recordManualOrder(order);
        }
        System.out.println("[Order] " + traderName + " " + side + " " + quantity + " → 成交 " + trades.size() + " 笔");
    }

    /** 用户撤掉自己未成交挂单 */
    public int cancelMyOrders(Session session) {
        authService.requireValid(session);
        String traderName = session.getUser().getTraderName();
        List<Order> orders = new ArrayList<>(simulation.openOrdersOf(traderName));
        int cancelled = 0;
        for (Order order : orders) {
            if (simulation.cancelOrder(order)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    /** 切换 AI 托管 */
    public void setAiManaged(Session session, boolean managed) {
        authService.requireValid(session);
        ensureTerminalAgent(session.getUser()).setAiManaged(managed);
    }

    public boolean isAiManaged(Session session) {
        return ensureTerminalAgent(session.getUser()).isAiManaged();
    }

    /** 导出当前用户的决策记录为 CSV */
    public Path exportUserCsv(Session session) throws IOException {
        authService.requireValid(session);
        String traderName = session.getUser().getTraderName();
        List<Decision> decisions = simulation.getDecisions(traderName);
        Path file = Paths.get("data", traderName + "_decisions.csv");
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("tick,time,agent,action,quantity,confidence,reason");
        int index = 0;
        for (Decision decision : decisions) {
            lines.add(String.join(",",
                    String.valueOf(index++),
                    decision.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    decision.getAgentName(),
                    String.valueOf(decision.getAction()),
                    String.valueOf(decision.getQuantity()),
                    String.format("%.2f", decision.getConfidence()),
                    csvEscape(decision.getReason())));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private String csvEscape(String text) {
        String value = text == null ? "" : text.replace("\"", "\"\"");
        return "\"" + value + "\"";
    }

    // ---------------- 管理员专属能力 ----------------

    /** 人工发布新闻：仅管理员可用 */
    public NewsItem publishNews(Session session, String content) {
        authService.requireAdmin(session);
        return simulation.injectNews(content);
    }

    /** 随机新闻自动发布：本质上切换 NewsEngine 的发布概率 */
    public void setAutoNews(Session session, boolean enabled) {
        authService.requireAdmin(session);
        simulation.getNewsEngine().setProbability(enabled ? 0.30 : 0.0);
    }

    public double getAutoNewsProbability() {
        return simulation.getNewsEngine().getProbability();
    }

    // ---------------- 面板数据查询 ----------------

    public Map<String, Account> getAccounts() {
        return simulation.getAccounts();
    }

    public MarketSimulation getSimulation() {
        return simulation;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public double getPrice() {
        return simulation.getPrice();
    }

    public boolean isHalted() {
        return simulation.isHalted();
    }

    public void addTickListener(Runnable listener) {
        tickListeners.add(listener);
    }

    public void addNewsListener(NewsListener listener) {
        newsListeners.add(listener);
    }

    public void removeNewsListener(NewsListener listener) {
        newsListeners.remove(listener);
    }

    public NewsEngine getNewsEngine() {
        return simulation.getNewsEngine();
    }

    public AuthService getAuthService() {
        return authService;
    }

    public LLMClient getLlmClient() {
        return llmClient;
    }

    @Override
    public void stop() {
        if (clock != null) {
            clock.stop();
        }
        Platform.exit();
    }
}
