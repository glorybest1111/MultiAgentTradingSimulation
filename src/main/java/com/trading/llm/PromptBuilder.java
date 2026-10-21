package com.trading.llm;

import com.trading.profile.ProfileTag;
import com.trading.profile.UserProfile;

/**
 * 动态 System Prompt 构造器：根据用户画像标签改写大模型的角色与约束。
 *
 * <p>这是本项目「动态 Prompt 注入与策略匹配」的核心：
 * <ul>
 *   <li>{@link ProfileTag#AGGRESSIVE_LOSING} → 互补保守型：必须写 CONSERVATIVE 关键词，
 *       强制模型限制单笔交易额、优先保本，只有在高确定性下才出手</li>
 *   <li>{@link ProfileTag#AGGRESSIVE_WINNING} → 同频激进型：允许放大收益，但仍受会员仓位上限约束</li>
 * </ul>
 *
 * <p>输出的 user prompt 是<b>结构化</b>的（KEY=value 形式），
 * 好处是本地 {@link MockLLMClient} 也能解析并给出确定性答复，
 * 从而实现「离线也能演示 LLM 投顾」。
 */
public class PromptBuilder {

    private static final String COMMON_RULES =
            "You are the AI investment manager of the trading user %s.\n"
                    + "You must reply EXACTLY in one line with this protocol and nothing else:\n"
                    + "ACTION=BUY|SELL|HOLD QTY=<integer> CONFIDENCE=<0..1> REASON=<short Chinese/English reason>\n"
                    + "Never exceed QTY computed from the risk budget. Never use markdown or code blocks.\n";

    private static final String CONSERVATIVE_ROLE =
            "PERSONA=CONSERVATIVE. This user is aggressive but currently LOSING money.\n"
                    + "Therefore you MUST act as a complementary conservative manager:\n"
                    + "  1. Capital preservation is the primary objective, profit comes second.\n"
                    + "  2. Prefer HOLD unless MOOD and TREND are clearly aligned against the position.\n"
                    + "  3. Keep every order small; reduce exposure when there is negative news.\n"
                    + "  4. Never average down in a falling market.\n";

    private static final String AGGRESSIVE_ROLE =
            "PERSONA=AGGRESSIVE-SAME-FREQUENCY. This user is aggressive and WINNING money.\n"
                    + "Therefore you should keep the same frequency and risk appetite:\n"
                    + "  1. Seize trend opportunities decisively when MOOD and TREND agree.\n"
                    + "  2. Let profits run, cut losses fast.\n"
                    + "  3. Still respect the risk budget given in MAX_QTY.\n";

    private static final String BALANCED_ROLE =
            "PERSONA=BALANCED. This user is conservative.\n"
                    + "Therefore you should trade patiently and keep positions small:\n"
                    + "  1. Only act on high-conviction signals.\n"
                    + "  2. Avoid chasing volatile moves.\n";

    /** 依据画像生成 System Prompt */
    public String buildSystemPrompt(UserProfile profile) {
        ProfileTag tag = profile == null ? ProfileTag.CONSERVATIVE_LOSING : profile.getTag();
        String role = tag == ProfileTag.AGGRESSIVE_LOSING ? CONSERVATIVE_ROLE
                : tag == ProfileTag.AGGRESSIVE_WINNING ? AGGRESSIVE_ROLE
                : BALANCED_ROLE;
        String traderName = profile == null ? "unknown" : profile.getTraderName();
        return String.format(COMMON_RULES, traderName) + role + tag.getStrategyHint() + "\n";
    }

    /** 组装结构化市场数据作为 User Prompt */
    public String buildUserPrompt(double price, double referencePrice, double mood, double trend,
                                  double volatility, int position, double cash, int maxQuantity) {
        double deviation = referencePrice > 0 ? (price - referencePrice) / referencePrice : 0;
        return String.format(
                "MOOD=%+.3f TREND=%+.4f DEVIATION=%+.4f VOLATILITY=%.4f PRICE=%.2f POSITION=%d CASH=%.2f MAX_QTY=%d",
                mood, trend, deviation, volatility, price, position, cash, maxQuantity);
    }
}
