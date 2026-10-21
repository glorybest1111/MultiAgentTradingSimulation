package com.trading.ui;

import com.trading.account.Account;
import com.trading.auth.Session;
import com.trading.model.Side;
import com.trading.risk.SlippageEstimator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * 管理员控制台（仅管理员可见）：
 * <ul>
 *   <li>手动新闻输入框 + 发布按钮（走 {@code requireAdmin} 权限校验）</li>
 *   <li>随机新闻自动发布开关：由 Timer 每 60 秒触发一次</li>
 *   <li>全市场数据监控面板：所有用户/AI 的资产明细</li>
 *   <li>订单簿深度：买卖各五档</li>
 * </ul>
 */
public class AdminConsoleView {

    private final TradingPlatformApplication app;
    private final Session session;
    private final Stage stage;

    private final ListView<String> monitorList = new ListView<>();
    private final ListView<String> bookList = new ListView<>();
    private final Label statusLabel = new Label();

    public AdminConsoleView(TradingPlatformApplication app, Session session) {
        this.app = app;
        this.session = session;
        this.stage = new Stage();
        build();
        // 面板随仿真节拍刷新
        app.addTickListener(this::refresh);
        refresh();
    }

    private void build() {
        // ---- 新闻发布区（管理员专属） ----
        TextArea newsInput = new TextArea();
        newsInput.setPromptText("输入新闻内容，例如：Apple announces record buyback");
        newsInput.setPrefRowCount(2);
        Button publishButton = new Button("发布新闻");
        Label publishHint = new Label();

        publishButton.setOnAction(event -> {
            try {
                String content = newsInput.getText();
                var item = app.publishNews(session, content);
                if (item == null) {
                    publishHint.setText("新闻内容不能为空");
                } else {
                    publishHint.setText(String.format("已发布：情绪分 %+.2f", item.getImpact()));
                    newsInput.clear();
                }
            } catch (SecurityException e) {
                publishHint.setText("发布失败：" + e.getMessage());
            }
        });

        // ---- 随机新闻自动发布开关：Timer 每 60 秒触发 ----
        CheckBox autoNews = new CheckBox("随机新闻自动发布（每 60 秒）");
        autoNews.setSelected(app.getAutoNewsProbability() > 0);
        java.util.Timer autoNewsTimer = new java.util.Timer("auto-news", true);
        // 用 AtomicBoolean 记录开关状态，避免 Timer 线程直接读取 JavaFX 控件
        java.util.concurrent.atomic.AtomicBoolean autoNewsEnabled =
                new java.util.concurrent.atomic.AtomicBoolean(app.getAutoNewsProbability() > 0);
        autoNews.setOnAction(event -> {
            try {
                app.setAutoNews(session, autoNews.isSelected());
                autoNewsEnabled.set(autoNews.isSelected());
            } catch (SecurityException e) {
                publishHint.setText("操作失败：" + e.getMessage());
                autoNews.setSelected(false);
                autoNewsEnabled.set(false);
            }
        });
        autoNewsTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (autoNewsEnabled.get() && session.isValid()) {
                    // Timer 线程 → 新闻引擎内部已做同步处理
                    app.getNewsEngine().maybeGenerate();
                }
            }
        }, 60_000L, 60_000L);

        // ---- 全市场监控 ----
        Button refreshButton = new Button("立即刷新监控");
        refreshButton.setOnAction(event -> refresh());
        Button logoutButton = new Button("退出登录");
        logoutButton.setOnAction(event -> {
            stage.close();
            app.logout(session);
        });

        GridPane header = new GridPane();
        header.setHgap(8);
        header.setVgap(8);
        header.add(new Label("管理员控制台"), 0, 0);
        header.add(statusLabel, 1, 0);
        header.add(autoNews, 0, 1);
        header.add(new Label("账号：" + session.getUser().getUsername()), 1, 1);

        VBox newsBox = new VBox(6, new Label("【新闻发布 · 管理员权限】"), newsInput, publishButton, publishHint);
        newsBox.setPadding(new Insets(8));
        newsBox.setStyle("-fx-border-color: #9ca3af; -fx-border-radius: 4;");

        VBox monitorBox = new VBox(6, new Label("【全市场资产监控】"), monitorList);
        monitorBox.setPadding(new Insets(8));
        monitorBox.setStyle("-fx-border-color: #9ca3af; -fx-border-radius: 4;");

        VBox bookBox = new VBox(6, new Label("【订单簿深度 · 买卖五档】"), bookList);
        bookBox.setPadding(new Insets(8));
        bookBox.setStyle("-fx-border-color: #9ca3af; -fx-border-radius: 4;");
        bookList.setPrefHeight(200);
        monitorList.setPrefHeight(200);

        HBox actions = new HBox(8, refreshButton, logoutButton);
        VBox root = new VBox(10, header, newsBox, new HBox(10, monitorBox, bookBox), actions);
        root.setPadding(new Insets(12));

        this.stage.setScene(new Scene(root, 880, 640));
        this.stage.setTitle("管理员控制台 - " + session.getUser().getUsername());
        this.stage.setOnCloseRequest(event -> {
            autoNewsTimer.cancel();
            app.logout(session);
        });
    }

    private void refresh() {
        if (!session.isValid()) {
            return;
        }
        double price = app.getPrice();
        statusLabel.setText(String.format("最新价 %.2f | 第 %d 期 | %s",
                price, app.getSimulation().getCurrentTick(),
                app.isHalted() ? "熔断暂停中" : "正常交易"));

        ObservableList<String> rows = FXCollections.observableArrayList();
        rows.add(String.format("%-16s %14s %8s %14s %10s %8s", "Account", "Cash", "Pos", "Equity", "Return", "Trades"));
        Map<String, Account> accounts = app.getAccounts();
        for (Account account : accounts.values()) {
            double equity = account.equity(price);
            double base = app.getConfig().initialCash;
            rows.add(String.format("%-16s %14.2f %8d %14.2f %+9.2f%% %8d",
                    account.getName(), account.getCash(), account.getPosition(), equity,
                    base > 0 ? (equity - base) / base * 100 : 0, account.getTradeCount()));
        }
        monitorList.setItems(rows);

        ObservableList<String> book = FXCollections.observableArrayList();
        List<double[]> asks = SlippageEstimator.topLevels(app.getSimulation().getEngine().getOrderBook(), Side.SELL, 5);
        List<double[]> bids = SlippageEstimator.topLevels(app.getSimulation().getEngine().getOrderBook(), Side.BUY, 5);
        book.add(String.format("%-10s %8s | %-10s %8s", "ASK", "Size", "BID", "Size"));
        for (int i = 0; i < 5; i++) {
            String ask = i < asks.size() ? String.format("%8.2f %8.0f", asks.get(i)[0], asks.get(i)[1]) : "";
            String bid = i < bids.size() ? String.format("%8.2f %8.0f", bids.get(i)[0], bids.get(i)[1]) : "";
            book.add(String.format("%-26s | %-26s", ask, bid));
        }
        bookList.setItems(book);
    }

    public void show() {
        stage.show();
    }

}
