package com.trading.agent;

import com.trading.llm.LLMClient;
import com.trading.model.Decision;

/**
 * 用户终端交易者：在「人工操作」与「AI 托管」之间切换的统一入口。
 *
 * <p>账户归属由 {@link #getName()} 决定，两个子 Agent 使用同一个名字，
 * 因此共享同一个账户；{@code aiManaged} 决定当期由谁值机。
 * 这样 GUI 只需翻转一个开关，不必在仿真中反复注册/注销 Agent。
 */
public class UserTerminalAgent implements TraderAgent {

    private final String traderName;
    private final QueueCommandSource commandSource = new QueueCommandSource();
    private final HumanTrader humanTrader;
    private final AiCustodyAgent custodyAgent;
    private volatile boolean aiManaged;

    public UserTerminalAgent(String traderName, LLMClient llmClient, double initialCash, double referencePrice) {
        this.traderName = traderName;
        this.humanTrader = new HumanTrader(commandSource);
        this.custodyAgent = new AiCustodyAgent(traderName, llmClient);
        this.custodyAgent.setInitialCash(initialCash);
        this.custodyAgent.setReferencePrice(referencePrice);
    }

    public UserTerminalAgent(String traderName) {
        this(traderName, null, 100_000, 150);
    }

    @Override
    public String getName() {
        return traderName;
    }

    public boolean isAiManaged() {
        return aiManaged;
    }

    /** GUI 的「AI 智能托管」开关 */
    public void setAiManaged(boolean aiManaged) {
        this.aiManaged = aiManaged;
    }

    /** GUI 提交人工下单指令 */
    public void submitManualCommand(TradeCommand command) {
        commandSource.submit(command);
    }

    public QueueCommandSource getCommandSource() {
        return commandSource;
    }

    public AiCustodyAgent getCustodyAgent() {
        return custodyAgent;
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        return aiManaged ? custodyAgent.onTick(snapshot, api) : humanTrader.onTick(snapshot, api);
    }
}
