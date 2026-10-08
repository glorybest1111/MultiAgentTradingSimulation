package com.trading.agent;

import com.trading.model.Decision;
import com.trading.model.Trade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 决策日志：将 AI 决策与成交记录写入 CSV（可解释 AI 的落盘实现）。
 * 输出目录默认 data/，每次运行追加记录。
 */
public class DecisionLog {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path dir;
    private final Path decisionFile;
    private final Path tradeFile;
    private boolean decisionHeaderWritten;
    private boolean tradeHeaderWritten;

    public DecisionLog(Path dir) {
        this.dir = dir;
        this.decisionFile = dir.resolve("decision_log.csv");
        this.tradeFile = dir.resolve("trade_log.csv");
    }

    public void logDecision(int tick, Decision decision) {
        List<String> row = List.of(
                String.valueOf(tick),
                decision.getTimestamp().format(TIME_FORMAT),
                decision.getAgentName(),
                String.valueOf(decision.getAction()),
                String.valueOf(decision.getQuantity()),
                String.format("%.2f", decision.getConfidence()),
                decision.getReason());
        appendCsv(decisionFile, row,
                List.of("tick", "time", "agent", "action", "quantity", "confidence", "reason"),
                !decisionHeaderWritten);
        decisionHeaderWritten = true;
    }

    public void logTrade(int tick, Trade trade) {
        List<String> row = List.of(
                String.valueOf(tick),
                trade.getTimestamp().format(TIME_FORMAT),
                trade.getSymbol(),
                String.format("%.2f", trade.getPrice()),
                String.valueOf(trade.getQuantity()),
                trade.getBuyTrader(),
                trade.getSellTrader(),
                trade.getReason());
        appendCsv(tradeFile, row,
                List.of("tick", "time", "symbol", "price", "quantity", "buyer", "seller", "reason"),
                !tradeHeaderWritten);
        tradeHeaderWritten = true;
    }

    private void appendCsv(Path file, List<String> row, List<String> header, boolean writeHeader) {
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            if (writeHeader && !Files.exists(file)) {
                lines.add(toCsvLine(header));
            }
            lines.add(toCsvLine(row));
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[DecisionLog] 写入失败: " + file + " - " + e.getMessage());
        }
    }

    private String toCsvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String cell = cells.get(i);
            if (cell.contains(",") || cell.contains("\"")) {
                sb.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(cell);
            }
        }
        return sb.toString();
    }

}
