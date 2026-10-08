package com.trading.test;

import com.trading.account.Account;
import com.trading.engine.MatchingEngine;
import com.trading.engine.OrderBook;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.model.Trade;
import com.trading.risk.FeeCalculator;
import com.trading.risk.SlippageModel;

import java.util.List;

/**
 * 核心引擎单元测试（作业要求）：买卖成交 / 多订单排序 / 部分成交 / 无法成交，
 * 并扩展：卖单主动成交 / 市价单 / IOC / 止损单 / 冰山单 / 手续费 / 滑点 / 账户盈亏。
 *
 * 自包含测试运行器，无需外部依赖：
 *   java com.trading.test.CoreEngineTest
 */
public class CoreEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== 核心引擎单元测试 =====");
        test1_buySellMatch();
        test2_multiOrderPriority();
        test3_partialFill();
        test4_noMatch();
        test5_sellSideMatch();
        test6_marketOrder();
        test7_iocOrder();
        test8_stopOrder();
        test9_icebergOrder();
        test10_feeCalculator();
        test11_slippageWalk();
        test12_accountPnL();
        System.out.println();
        System.out.printf("测试完成: 通过 %d 个, 失败 %d 个%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** 测试 1: 买卖成交 — 卖单挂单后买单主动成交 */
    private static void test1_buySellMatch() {
        MatchingEngine engine = new MatchingEngine();
        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 100, "T1");
        List<Trade> empty = engine.submitOrder(sell);
        check(empty.isEmpty(), "1.1 卖单挂入订单簿, 暂无成交");

        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 151, 50, "T2");
        List<Trade> trades = engine.submitOrder(buy);
        check(trades.size() == 1, "1.2 买单主动成交产生 1 笔成交");
        if (!trades.isEmpty()) {
            checkEquals(150, trades.get(0).getPrice(), 1e-9, "1.3 成交价按挂单价 150");
            check(trades.get(0).getQuantity() == 50, "1.4 成交数量 50");
        }
        check(sell.getQuantity() == 50, "1.5 卖单剩余 50 股继续挂单");
    }

    /** 测试 2: 多订单排序 — 价格优先 + 时间优先 */
    private static void test2_multiOrderPriority() {
        OrderBook book = new OrderBook();
        Order sell1 = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 100, "T1");
        Order sell2 = new Order("AAPL", Side.SELL, OrderType.LIMIT, 149, 100, "T2");
        Order sell3 = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 100, "T3");
        book.addOrder(sell1);
        book.addOrder(sell2);
        book.addOrder(sell3);

        check(book.getBestAsk() == sell2, "2.1 卖单价格低优先 (best ask = 149)");

        Order buy1 = new Order("AAPL", Side.BUY, OrderType.LIMIT, 100, 100, "T4");
        Order buy2 = new Order("AAPL", Side.BUY, OrderType.LIMIT, 101, 100, "T5");
        book.addOrder(buy1);
        book.addOrder(buy2);
        check(book.getBestBid() == buy2, "2.2 买单价格高优先 (best bid = 101)");

        List<Order> asks = book.orderedSnapshot(Side.SELL);
        check(asks.get(0) == sell2, "2.3 卖单队列: 最低价排最前");
        check(asks.get(1) == sell1 && asks.get(2) == sell3, "2.4 同价 150 的两笔卖单按时间优先");
    }

    /** 测试 3: 部分成交 — 对手盘数量不足, 剩余部分挂单 */
    private static void test3_partialFill() {
        MatchingEngine engine = new MatchingEngine();
        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 60, "T1");
        engine.submitOrder(sell);

        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 150, 100, "T2");
        List<Trade> trades = engine.submitOrder(buy);
        check(trades.size() == 1 && trades.get(0).getQuantity() == 60, "3.1 仅成交 60 股");
        check(buy.getQuantity() == 40, "3.2 买单剩余 40 股");
        check(engine.getOrderBook().getBestBid() == buy, "3.3 剩余 40 股挂入订单簿");
        check(engine.getOrderBook().getAsks().isEmpty(), "3.4 卖单已被吃完");
    }

    /** 测试 4: 无法成交 — 价格不交叉, 双方均挂单 */
    private static void test4_noMatch() {
        MatchingEngine engine = new MatchingEngine();
        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 100, "T1");
        engine.submitOrder(sell);

        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 149, 100, "T2");
        List<Trade> trades = engine.submitOrder(buy);
        check(trades.isEmpty(), "4.1 买价 149 < 卖价 150, 无成交");
        check(engine.getOrderBook().getBestBid() == buy, "4.2 买单挂入订单簿");
        check(engine.getOrderBook().getBestAsk() == sell, "4.3 卖单仍在订单簿");
    }

    /** 测试 5: 卖单主动成交（补全撮合引擎另一侧） */
    private static void test5_sellSideMatch() {
        MatchingEngine engine = new MatchingEngine();
        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 150, 100, "T1");
        engine.submitOrder(buy);

        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 149, 50, "T2");
        List<Trade> trades = engine.submitOrder(sell);
        check(trades.size() == 1, "5.1 卖单主动成交产生 1 笔成交");
        if (!trades.isEmpty()) {
            checkEquals(150, trades.get(0).getPrice(), 1e-9, "5.2 按买单挂单价 150 成交 (卖方价格改善)");
        }
        check(buy.getQuantity() == 50, "5.3 买单剩余 50 股");
    }

    /** 测试 6: 市价单 — 先吃簿内, 簿深不足走滑点模型, 虚拟深度耗尽后剩余取消 */
    private static void test6_marketOrder() {
        MatchingEngine engine = new MatchingEngine();
        engine.setLastPrice(150);
        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 100, "T1");
        engine.submitOrder(sell);

        Order buy1 = new Order("AAPL", Side.BUY, OrderType.MARKET, 0, 80, "T2");
        List<Trade> t1 = engine.submitOrder(buy1);
        check(t1.size() == 1 && t1.get(0).getQuantity() == 80, "6.1 市价单吃掉簿内 80 股");

        Order buy2 = new Order("AAPL", Side.BUY, OrderType.MARKET, 0, 50, "T3");
        List<Trade> t2 = engine.submitOrder(buy2);
        check(t2.size() == 3, "6.2 簿深不足: 簿内 20 + 虚拟层 10 + 虚拟层 10, 分三笔");
        check(t2.get(0).getQuantity() == 20 && t2.get(1).getQuantity() == 10 && t2.get(2).getQuantity() == 10,
                "6.3 分三笔成交 20 + 10 + 10");
        checkEquals(150, t2.get(0).getPrice(), 1e-9, "6.4 第一笔按挂单价 150");
        checkEquals(150.10, t2.get(1).getPrice(), 1e-9, "6.5 虚拟层 1 价格 = 150 + 0.10");
        checkEquals(150.20, t2.get(2).getPrice(), 1e-9, "6.6 虚拟层 2 价格 = 150 + 0.20");
        check(buy2.getQuantity() == 10, "6.7 虚拟深度耗尽, 剩余 10 股取消");

        // 6.8 流动性耗尽: 空簿市价单只成交虚拟容量部分
        engine.resetVirtualDepth();
        Order buy3 = new Order("AAPL", Side.BUY, OrderType.MARKET, 0, 100, "T4");
        List<Trade> t3 = engine.submitOrder(buy3);
        int filled = 0;
        for (Trade t : t3) {
            filled += t.getQuantity();
        }
        check(filled == 20, "6.8 虚拟深度容量 20 股, 市价单 100 股只成交 20 股");
        check(buy3.getQuantity() == 80, "6.9 剩余 80 股取消 (流动性不足)");
    }

    /** 测试 7: IOC — 立即成交否则取消, 未成交部分不挂单 */
    private static void test7_iocOrder() {
        MatchingEngine engine = new MatchingEngine();
        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 60, "T1");
        engine.submitOrder(sell);

        Order buy = new Order("AAPL", Side.BUY, OrderType.IOC, 151, 100, "T2");
        List<Trade> trades = engine.submitOrder(buy);
        check(trades.size() == 1 && trades.get(0).getQuantity() == 60, "7.1 IOC 立即成交 60 股");
        check(engine.getOrderBook().getBids().isEmpty(), "7.2 未成交的 40 股被取消, 不挂单");
    }

    /** 测试 8: 止损单 — 市价触及触发价后自动转为市价单 */
    private static void test8_stopOrder() {
        MatchingEngine engine = new MatchingEngine();
        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 147, 10, "T1");
        engine.submitOrder(buy);

        Order stop = Order.stop("AAPL", Side.SELL, 148, 10, "T2");
        List<Trade> before = engine.submitOrder(stop);
        check(before.isEmpty(), "8.1 止损单挂起, 暂不撮合");

        engine.setLastPrice(149);
        check(engine.triggerStopOrders(149).isEmpty(), "8.2 市价 149 未触及 148, 不触发");

        engine.setLastPrice(147.5);
        List<Trade> triggered = engine.triggerStopOrders(147.5);
        check(triggered.size() == 1, "8.3 市价 147.5 <= 148, 触发止损");
        if (!triggered.isEmpty()) {
            checkEquals(147, triggered.get(0).getPrice(), 1e-9, "8.4 止损转市价单, 按买方挂单价 147 成交");
        }
    }

    /** 测试 9: 冰山单 — 分批可见, 成交后自动补充下一层 */
    private static void test9_icebergOrder() {
        MatchingEngine engine = new MatchingEngine();
        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 30, "T1")
                .withIcebergSlice(10);
        check(sell.effectiveQuantity() == 10, "9.1 冰山单只露出 10 股");
        engine.submitOrder(sell);

        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 150, 25, "T2");
        List<Trade> trades = engine.submitOrder(buy);
        check(trades.size() == 3, "9.2 分三笔成交 (10 + 10 + 5)");
        int totalQty = 0;
        for (Trade t : trades) {
            totalQty += t.getQuantity();
        }
        check(totalQty == 25, "9.3 共成交 25 股");
        check(sell.getQuantity() == 5, "9.4 冰山单剩余 5 股");
        check(engine.getOrderBook().getBestAsk() == sell, "9.5 剩余部分继续在订单簿中");
    }

    /** 测试 10: 手续费 — 0.1% 费率 */
    private static void test10_feeCalculator() {
        FeeCalculator fee = new FeeCalculator(0.001);
        checkEquals(10, fee.fee(10000), 1e-9, "10.1 交易金额 10000 手续费 10");
        checkEquals(10010, fee.buyCost(100, 100), 1e-9, "10.2 买入 100 股 @100 总成本 10010");
        checkEquals(9990, fee.sellProceeds(100, 100), 1e-9, "10.3 卖出 100 股 @100 净收入 9990");
    }

    /** 测试 11: 滑点 — 按订单簿行走计算平均成交价 (作业示例: 100.67) */
    private static void test11_slippageWalk() {
        OrderBook book = new OrderBook();
        book.addOrder(new Order("AAPL", Side.SELL, OrderType.LIMIT, 100, 100, "T1"));
        book.addOrder(new Order("AAPL", Side.SELL, OrderType.LIMIT, 101, 200, "T2"));

        double avg = SlippageModel.walkBook(book, Side.SELL, 300);
        checkEquals(100.6666666667, avg, 1e-9, "11.1 买 300 股平均成本 100.67 (100股@100 + 200股@101)");

        SlippageModel model = new SlippageModel(0.10);
        checkEquals(100.20, model.priceAfterLevels(100, 2, Side.BUY), 1e-9, "11.2 虚拟深度第 2 层买价 100.20");
        checkEquals(99.80, model.priceAfterLevels(100, 2, Side.SELL), 1e-9, "11.3 虚拟深度第 2 层卖价 99.80");
    }

    /** 测试 12: 账户盈亏 — 加权平均成本、已实现盈亏与手续费 */
    private static void test12_accountPnL() {
        Account account = new Account("A", 20000);
        account.applyFill(Side.BUY, 100, 100, 10);
        checkEquals(9990, account.getCash(), 1e-9, "12.1 买入后现金 = 20000 - 10000 - 10");
        check(account.getPosition() == 100, "12.2 持仓 100 股");

        account.applyFill(Side.SELL, 110, 100, 11);
        checkEquals(20979, account.getCash(), 1e-9, "12.3 卖出后现金 = 9990 + 11000 - 11");
        checkEquals(1000, account.getRealizedPnl(), 1e-9, "12.4 已实现盈亏 = (110-100) x 100 = 1000");
        checkEquals(21, account.getTotalFees(), 1e-9, "12.5 累计手续费 21");
        check(account.getPosition() == 0, "12.6 平仓后持仓为 0");
        checkEquals(20979, account.equity(999), 1e-9, "12.7 空仓时净值 = 现金");
        checkEquals(0.04895, account.returnRate(20000, 999), 1e-9, "12.8 收益率 4.895%");
    }

    // ---------------- 断言工具 ----------------

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static void checkEquals(double expected, double actual, double epsilon, String name) {
        check(Math.abs(expected - actual) < epsilon, name + " (期望 " + expected + ", 实际 " + actual + ")");
    }

}
