package com.trading.ai;

import com.trading.agent.MarketSnapshot;
import com.trading.model.Decision;

/**
 * 多 Agent 辩论决策机制：
 * BullAgent（看涨）与 BearAgent（看跌）各自陈述观点，
 * JudgeAgent（裁判）综合双方证据给出最终交易决策。
 * 相比单一 AI 直接交易，辩论机制提高决策可信度与可研究性。
 */
public class DebateSystem {

    private final BullAgent bull = new BullAgent();
    private final BearAgent bear = new BearAgent();
    private final JudgeAgent judge = new JudgeAgent();

    /** 一轮辩论 → 最终决策 */
    public Decision debate(MarketSnapshot snapshot) {
        Opinion bullOpinion = bull.formOpinion(snapshot);
        Opinion bearOpinion = bear.formOpinion(snapshot);
        return judge.judge(bullOpinion, bearOpinion, snapshot);
    }

}
