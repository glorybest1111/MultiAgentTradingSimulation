package com.trading.agent;

/**
 * 人类交易者的输入源抽象。
 *
 * <p>把 {@code Scanner} 从 {@link HumanTrader} 里彻底剥离：
 * 控制台模式用 {@link ScannerCommandSource}，GUI 模式可以实现自己的队列版本
 * （JavaFX 线程把按钮点击塞进队列，仿真线程取出执行），
 * 仿真主循环因此完全无需感知 UI 的存在。
 */
public interface CommandSource {

    /**
     * 取一条指令。实现必须是<b>非阻塞</b>的：没有指令时应立刻返回 HOLD，
     * 否则会卡住整个仿真主循环。
     */
    TradeCommand nextCommand(MarketSnapshot snapshot);
}
