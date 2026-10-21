package com.trading.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 队列式输入源：GUI 把按钮点击塞进队列，仿真线程取出执行。
 *
 * <p>与 {@link ScannerCommandSource} 的关键区别：<b>永不阻塞</b>——
 * 没有指令时立即返回 HOLD，因此不会卡住整个仿真主循环。
 */
public class QueueCommandSource implements CommandSource {

    private final BlockingQueue<TradeCommand> queue = new LinkedBlockingQueue<>();

    /** GUI 线程调用：提交一条指令 */
    public void submit(TradeCommand command) {
        if (command != null) {
            queue.offer(command);
        }
    }

    public void submitBuy(int quantity) {
        submit(TradeCommand.buy(quantity));
    }

    public void submitSell(int quantity) {
        submit(TradeCommand.sell(quantity));
    }

    @Override
    public TradeCommand nextCommand(MarketSnapshot snapshot) {
        TradeCommand command = queue.poll();
        return command == null ? TradeCommand.hold() : command;
    }

    public int pendingSize() {
        return queue.size();
    }
}
