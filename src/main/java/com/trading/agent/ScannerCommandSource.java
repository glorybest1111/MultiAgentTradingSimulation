package com.trading.agent;

import java.util.Scanner;

/**
 * 控制台输入源：从 stdin 读取 "BUY 10 / SELL 5 / HOLD / EXIT"。
 * 仅在 {@code Main --human} 模式下使用，GUI 不会实例化本类。
 */
public class ScannerCommandSource implements CommandSource {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public TradeCommand nextCommand(MarketSnapshot snapshot) {
        System.out.printf("[第 %d 期] %s @ %.2f | 现金 %.2f | 持仓 %d (均价 %.2f) | 情绪 %+.2f%n",
                snapshot.getTick(), snapshot.getSymbol(), snapshot.getPrice(),
                snapshot.getCash(), snapshot.getPosition(), snapshot.getAvgCost(),
                snapshot.getMarketMood());
        if (snapshot.getLatestNews() != null) {
            System.out.println("  新闻: " + snapshot.getLatestNews());
        }
        System.out.print("  指令 (BUY/SELL/HOLD/EXIT [数量], 如 BUY 10): ");

        String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "EXIT";
        if (line.isEmpty()) {
            return TradeCommand.hold();
        }
        String[] parts = line.split("\\s+");
        String command = parts[0].toUpperCase();
        int quantity = parts.length > 1 ? parseQuantity(parts[1]) : 10;

        switch (command) {
            case "BUY":
                return TradeCommand.buy(Math.max(1, quantity));
            case "SELL":
                return TradeCommand.sell(Math.max(1, quantity));
            case "EXIT":
                return TradeCommand.exit();
            default:
                return TradeCommand.hold();
        }
    }

    private int parseQuantity(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 10;
        }
    }
}
