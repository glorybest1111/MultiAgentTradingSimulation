package com.trading.ui;

import com.trading.account.Account;
import com.trading.agent.UserTerminalAgent;
import com.trading.auth.Session;
import com.trading.market.NewsItem;
import com.trading.market.NewsListener;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.profile.UserProfile;
import com.trading.risk.SlippageEstimator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 普通用户终端：
 * <ul>
 *   <li>实时行情面板：价格折线图 + 买卖五档盘口</li>
 *   <li>交易面板：限价单/市价单下单与撤单</li>
 *   <li>账户面板：现金、持仓、总权益、一键导出 CSV</li>
 *   <li>只读新闻滚动播报栏</li>
 *   <li>AI 智能托管开关（切换后由 AiCustodyAgent 接管决策）</li>
 * </ul>
 */
public class UserTerminalView {

    private final TradingPlatformApplication app;
    private final Session session;
    private final UserTerminalAgent agent;
    private final Stage stage;

    private final XYChart.Series<Number, Number> priceSeries = new XYChart.Series<>();
    private final ListView<String> bookList = new ListView<>();
    private final ListView<String> newsList = new ListView<>();
    private final Label accountLabel = new Label();
    private final Label statusLabel = new Label();
    private final Label profileLabel = new Label("尚未开启 AI 托管");
    private final NewsListener newsListener;

    public UserTerminalView(TradingPlatformApplication app, UserTerminalAgent agent, Session session) {
        this.app = app;
        this.agent = agent;
        this.session = session;
        this.stage = new Stage();
        this.newsListener = (NewsItem item) -> javafx.application.Platform.runLater(
                () -> pushNews(item.getHeadline() + "  (impact " + String.format("%+.2f", item.getImpact()) + ")"));
        app.addNewsListener(newsListener);
        build();
        app.addTickListener(this::refresh);
        refresh();
    }

    private void build() {
        // ---- 行情 ----
        NumberAxis xAxis = new NumberAxis("期数", 0, 200, 20);
        NumberAxis yAxis = new NumberAxis("价格", 0, 400, 20);
        yAxis.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("实时价格走势");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        priceSeries.setName("price");
        chart.getData().add(priceSeries);
        chart.setPrefHeight(240);
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);

        // ---- 盘口 ----
        bookList.setPrefHeight(180);

        // ---- 交易面板 ----
        ComboBox<Side> sideBox = new ComboBox<>(FXCollections.observableArrayList(Side.BUY, Side.SELL));
        sideBox.setValue(Side.BUY);
        ComboBox<OrderType> typeBox = new ComboBox<>(FXCollections.observableArrayList(OrderType.LIMIT, OrderType.MARKET));
        typeBox.setValue(OrderType.LIMIT);
        TextField quantityField = new TextField("10");
        quantityField.setPrefWidth(70);
        TextField priceField = new TextField("150");
        priceField.setPrefWidth(80);

        Button submitButton = new Button("下单");
        submitButton.setOnAction(event -> {
            try {
                int quantity = Integer.parseInt(quantityField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                if (typeBox.getValue() == OrderType.MARKET) {
                    price = app.getPrice();
                }
                app.placeUserOrder(session, sideBox.getValue(), typeBox.getValue(), Math.max(1, quantity), price);
            } catch (NumberFormatException e) {
                statusLabel.setText("数量/价格格式错误");
            } catch (SecurityException e) {
                statusLabel.setText("下单失败：" + e.getMessage());
            }
        });

        Button cancelButton = new Button("撤销我的挂单");
        cancelButton.setOnAction(event -> statusLabel.setText("已撤销 " + app.cancelMyOrders(session) + " 笔挂单"));

        GridPane tradePane = new GridPane();
        tradePane.setHgap(8);
        tradePane.setVgap(8);
        tradePane.add(new Label("方向"), 0, 0);
        tradePane.add(sideBox, 1, 0);
        tradePane.add(new Label("类型"), 2, 0);
        tradePane.add(typeBox, 3, 0);
        tradePane.add(new Label("数量"), 0, 1);
        tradePane.add(quantityField, 1, 1);
        tradePane.add(new Label("价格"), 2, 1);
        tradePane.add(priceField, 3, 1);
        tradePane.add(submitButton, 4, 1);
        tradePane.add(cancelButton, 5, 1);

        // ---- AI 托管 ----
        CheckBox aiToggle = new CheckBox("AI 智能托管");
        aiToggle.setOnAction(event -> {
            boolean enabled = aiToggle.isSelected();
            app.setAiManaged(session, enabled);
            profileLabel.setText(enabled ? "AI 接管中，正在生成用户画像…" : "已切换回人工模式");
        });

        // ---- 账户区 ----
        Button exportButton = new Button("导出我的交易记录");
        exportButton.setOnAction(event -> export());

        Button logoutButton = new Button("退出登录");
        logoutButton.setOnAction(event -> {
            stage.close();
            app.logout(session);
        });

        VBox left = new VBox(8, new Label("【实时行情】"), chart, new Label("【买卖五档】"), bookList);
        VBox right = new VBox(8,
                new Label("【账户】账号 " + session.getUser().getUsername()), accountLabel,
                new Label("【交易面板】"), tradePane, statusLabel,
                new Label("【AI 智能投顾】"), aiToggle, profileLabel,
                new Label("【新闻播报 · 只读】"), newsList,
                new HBox(8, exportButton, logoutButton));
        left.setPrefWidth(520);
        right.setPrefWidth(430);
        left.setPadding(new Insets(10));
        right.setPadding(new Insets(10));

        this.stage.setScene(new Scene(new HBox(10, left, right), 980, 660));
        this.stage.setTitle("用户终端 - " + session.getUser().getUsername());
        this.stage.setOnCloseRequest(event -> {
            app.removeNewsListener(newsListener);
            app.logout(session);
        });
    }

    private void refresh() {
        if (!session.isValid()) {
            return;
        }
        double price = app.getPrice();
        int tick = app.getSimulation().getCurrentTick();

        if (priceSeries.getData().size() > 300) {
            priceSeries.getData().remove(0);
        }
        priceSeries.getData().add(new XYChart.Data<>(tick, price));
        if (priceSeries.getNode() != null) {
            priceSeries.getNode().setStyle("-fx-stroke: #2563eb;");
        }

        ObservableList<String> book = FXCollections.observableArrayList();
        var orderBook = app.getSimulation().getEngine().getOrderBook();
        List<double[]> asks = SlippageEstimator.topLevels(orderBook, Side.SELL, 5);
        List<double[]> bids = SlippageEstimator.topLevels(orderBook, Side.BUY, 5);
        book.add(String.format("%-12s %8s | %-12s %8s", "ASK(sell)", "Size", "BID(buy)", "Size"));
        for (int i = 0; i < 5; i++) {
            String ask = i < asks.size() ? String.format("%10.2f %8.0f", asks.get(i)[0], asks.get(i)[1]) : "";
            String bid = i < bids.size() ? String.format("%10.2f %8.0f", bids.get(i)[0], bids.get(i)[1]) : "";
            book.add(String.format("%-30s | %-30s", ask, bid));
        }
        bookList.setItems(book);

        Account account = app.getAccounts().get(session.getUser().getTraderName());
        double base = app.getConfig().initialCash;
        if (account != null) {
            double equity = account.equity(price);
            int openOrders = app.getSimulation().openOrdersOf(session.getUser().getTraderName()).size();
            accountLabel.setText(String.format(
                    "现金 %.2f | 持仓 %d | 总权益 %.2f | 收益率 %+.2f%%\n"
                            + "累计已实现 %.2f | 手续费 %.2f | 成交 %d 笔 | 在途挂单 %d",
                    account.getCash(), account.getPosition(), equity,
                    base > 0 ? (equity - base) / base * 100 : 0,
                    account.getRealizedPnl(), account.getTotalFees(), account.getTradeCount(), openOrders));
        }
        statusLabel.setText(String.format("最新价 %.2f | 第 %d 期 | %s",
                price, tick, app.isHalted() ? "熔断暂停中" : "正常交易"));

        UserProfile profile = agent.getCustodyAgent().getProfile();
        if (agent.isAiManaged() && profile != null) {
            profileLabel.setText(String.format("画像：%s | 换手率 %.2f | 相对大盘 %+.2f%% | 胜率 %.0f%%",
                    profile.getTag().getChineseName(), profile.getTurnoverRate(),
                    profile.getExcessReturn() * 100, profile.getWinRate() * 100));
        }
    }

    private void pushNews(String text) {
        newsList.getItems().add(0, text);
        if (newsList.getItems().size() > 50) {
            newsList.getItems().remove(50, newsList.getItems().size());
        }
    }

    private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出我的交易记录");
        chooser.setInitialFileName(session.getUser().getTraderName() + "_decisions.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV 文件", "*.csv"));
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }
        try {
            Path generated = app.exportUserCsv(session);
            Files.copy(generated, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            statusLabel.setText("已导出到 " + target.getAbsolutePath());
        } catch (SecurityException e) {
            statusLabel.setText("导出失败：" + e.getMessage());
        } catch (Exception e) {
            statusLabel.setText("导出失败：" + e.getMessage());
        }
    }

    public void show() {
        stage.show();
    }
}
