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
import java.util.Locale;

/**
 * 决策日志：将 AI 决策与成交记录写入 CSV（可解释 AI 的落盘实现）。
 *
 * <p>按<b>实验</b>分文件：一次 {@code Main} 运行会产生多个 MarketSimulation
 * （竞赛、无做市商、有做市商），若共用同一份 CSV，tick 会重复出现多遍且无法区分来源。
 * 现在每个实验生成独立文件 {@code <experiment>_decision_log.csv} / {@code <experiment>_trade_log.csv}，
 * 并在每行首列写入 experiment 名，便于合并后仍能区分。
 */
public class DecisionLog {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path dir;
    private final String experiment;
    private final Path decisionFile;
    private final Path tradeFile;
    private boolean decisionHeaderWritten;
    private boolean tradeHeaderWritten;

    public DecisionLog(Path dir) {
        this(dir, "simulation");
    }

    public DecisionLog(Path dir, String experiment) {
        this.dir = dir;
        this.experiment = sanitize(experiment);
        this.decisionFile = dir.resolve(this.experiment + "_decision_log.csv");
        this.tradeFile = dir.resolve(this.experiment + "_trade_log.csv");
    }

    public void logDecision(int tick, Decision decision) {
        List<String> row = List.of(
                experiment,
                String.valueOf(tick),
                decision.getTimestamp().format(TIME_FORMAT),
                decision.getAgentName(),
                String.valueOf(decision.getAction()),
                String.valueOf(decision.getQuantity()),
                String.format(Locale.ROOT, "%.2f", decision.getConfidence()),
                decision.getReason());
        appendCsv(decisionFile, row,
                List.of("experiment", "tick", "time", "agent", "action", "quantity", "confidence", "reason"),
                !decisionHeaderWritten);
        decisionHeaderWritten = true;
    }

    public void logTrade(int tick, Trade trade) {
        List<String> row = List.of(
                experiment,
                String.valueOf(tick),
                trade.getTimestamp().format(TIME_FORMAT),
                trade.getSymbol(),
                String.format(Locale.ROOT, "%.2f", trade.getPrice()),
                String.valueOf(trade.getQuantity()),
                trade.getBuyTrader(),
                trade.getSellTrader(),
                trade.getReason());
        appendCsv(tradeFile, row,
                List.of("experiment", "tick", "time", "symbol", "price", "quantity", "buyer", "seller", "reason"),
                !tradeHeaderWritten);
        tradeHeaderWritten = true;
    }

    public String getExperiment() {
        return experiment;
    }

    public Path getDecisionFile() {
        return decisionFile;
    }

    public Path getTradeFile() {
        return tradeFile;
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
            String cell = cells.get(i) == null ? "" : cells.get(i);
            if (cell.contains(",") || cell.contains("\"")) {
                sb.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(cell);
            }
        }
        return sb.toString();
    }

    /** 实验名落盘为文件名前需做安全化处理 */
    private static String sanitize(String name) {
        String cleaned = name == null ? "simulation" : name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        return cleaned.isEmpty() ? "simulation" : cleaned.toLowerCase(Locale.ROOT);
    }
}
