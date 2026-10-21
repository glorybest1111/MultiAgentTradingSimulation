package com.trading.profile;

/**
 * 用户画像标签：由「是否激进 × 是否跑赢大盘」两个维度组合而成。
 *
 * <p>LLM 投顾会依据标签切换互补/同频策略：
 * <ul>
 *   <li>{@link #AGGRESSIVE_LOSING}：激进且亏损 → 强制互补保守型（限仓、保本优先）</li>
 *   <li>{@link #AGGRESSIVE_WINNING}：激进且盈利 → 保持同频激进型（放大收益）</li>
 *   <li>其余两类沿用与其风险偏好相匹配的温和策略</li>
 * </ul>
 */
public enum ProfileTag {

    AGGRESSIVE_WINNING("aggressive_winning", "激进且盈利", true, true, 0.20,
            "同频激进型：跟随用户风格放大收益，但仍受单笔仓位上限约束"),
    AGGRESSIVE_LOSING("aggressive_losing", "激进且亏损", true, false, 0.05,
            "互补保守型：严格限制单笔交易额，优先保本，耐心等待高确定性机会"),
    CONSERVATIVE_WINNING("conservative_winning", "保守且盈利", false, true, 0.12,
            "稳健增益型：低频操作，在趋势明确时小幅加仓"),
    CONSERVATIVE_LOSING("conservative_losing", "保守且亏损", false, false, 0.08,
            "防守观察型：极小仓位试探，达不到阈值就空仓");

    private final String code;
    private final String chineseName;
    private final boolean aggressive;
    private final boolean profitable;
    private final double maxNotionalRatio;
    private final String strategyHint;

    ProfileTag(String code, String chineseName, boolean aggressive, boolean profitable,
               double maxNotionalRatio, String strategyHint) {
        this.code = code;
        this.chineseName = chineseName;
        this.aggressive = aggressive;
        this.profitable = profitable;
        this.maxNotionalRatio = maxNotionalRatio;
        this.strategyHint = strategyHint;
    }

    public String getCode() {
        return code;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isAggressive() {
        return aggressive;
    }

    public boolean isProfitable() {
        return profitable;
    }

    /** 单笔交易占用资金的最大比例（相对总权益） */
    public double getMaxNotionalRatio() {
        return maxNotionalRatio;
    }

    public String getStrategyHint() {
        return strategyHint;
    }
}
