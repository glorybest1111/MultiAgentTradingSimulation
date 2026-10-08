package com.trading.agent;

import com.trading.model.Decision;

/**
 * 交易者统一接口：人类交易者、AI 策略、做市商都实现该接口。
 */
public interface TraderAgent {

    /** 交易者名称（用于账户归属与排名） */
    String getName();

    /**
     * 每个交易周期调用一次。
     * 通过 api 下单/撤单，返回本次决策记录（用于 AI 可解释日志）。
     */
    Decision onTick(MarketSnapshot snapshot, TradingApi api);

}
