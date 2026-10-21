package com.trading.profile;

import com.trading.account.Account;
import com.trading.model.Action;
import com.trading.model.Decision;

import java.util.List;

/**
 * 用户画像生成器：从历史决策与账户状态中提取行为特征并打标签。
 *
 * <p>计算口径：
 * <ul>
 *   <li>换手率 turnoverRate = 累计下单次数 / 已参与期数</li>
 *   <li>平均单笔成交额 avgOrderNotional = 累计成交额 / 下单次数</li>
 *   <li>相对大盘盈亏比 excessReturn = 用户收益率 - 标的价格收益率</li>
 *   <li>激进判定：换手率 ≥ 30% <b>或</b> 平均单笔成交额 ≥ 期初权益的 5%</li>
 *   <li>盈利判定：相对大盘盈亏比 &gt; 0（跑赢大盘即为赢）</li>
 *   <li>胜率（浮盈口径）：把每笔决策时的价格与当前市价比较，
 *       BUY 后上涨 / SELL 后下跌记为一次盈利决策</li>
 * </ul>
 */
public class UserProfileCalculator {

    private static final double AGGRESSIVE_TURNOVER = 0.30;
    private static final double AGGRESSIVE_NOTIONAL_RATIO = 0.05;

    /**
     * @param initialCash 期初资金
     * @param equity      当前总权益（现金 + 持仓市值）
     */
    public UserProfile calculate(String traderName, List<Decision> decisions, double currentPrice,
                                 double referencePrice, double initialCash, double equity, int currentTick) {
        int orderCount = 0;
        int tradedVolume = 0;
        double tradedNotional = 0;
        int scoredDecisions = 0;
        int profitableDecisions = 0;

        for (Decision decision : decisions) {
            if (decision == null || decision.getAction() == Action.HOLD) {
                continue;
            }
            orderCount++;
            tradedVolume += Math.abs(decision.getQuantity());
            double decisionPrice = decision.getDecisionPrice();
            if (!Double.isNaN(decisionPrice) && decisionPrice > 0) {
                tradedNotional += Math.abs(decision.getQuantity()) * decisionPrice;
                scoredDecisions++;
                boolean profit = decision.getAction() == Action.BUY
                        ? currentPrice > decisionPrice
                        : currentPrice < decisionPrice;
                if (profit) {
                    profitableDecisions++;
                }
            }
        }

        int periods = Math.max(1, currentTick);
        double turnoverRate = orderCount / (double) periods;
        double avgOrderNotional = orderCount == 0 ? 0 : tradedNotional / orderCount;
        double userReturn = initialCash > 0 ? (equity - initialCash) / initialCash : 0;
        double marketReturn = referencePrice > 0 ? (currentPrice - referencePrice) / referencePrice : 0;
        double excessReturn = userReturn - marketReturn;
        double winRate = scoredDecisions == 0 ? 0 : profitableDecisions / (double) scoredDecisions;

        boolean aggressive = turnoverRate >= AGGRESSIVE_TURNOVER
                || (initialCash > 0 && avgOrderNotional >= initialCash * AGGRESSIVE_NOTIONAL_RATIO);
        boolean profitable = excessReturn > 0;

        ProfileTag tag = resolveTag(aggressive, profitable);
        return new UserProfile(traderName, orderCount, tradedVolume, turnoverRate, avgOrderNotional,
                userReturn, marketReturn, excessReturn, winRate, tag);
    }

    private ProfileTag resolveTag(boolean aggressive, boolean profitable) {
        if (aggressive && profitable) {
            return ProfileTag.AGGRESSIVE_WINNING;
        }
        if (aggressive) {
            return ProfileTag.AGGRESSIVE_LOSING;
        }
        return profitable ? ProfileTag.CONSERVATIVE_WINNING : ProfileTag.CONSERVATIVE_LOSING;
    }

    /** 基于账户对象的便捷重载 */
    public UserProfile calculate(String traderName, Account account, List<Decision> decisions,
                                 double currentPrice, double referencePrice,
                                 double initialCash, int currentTick) {
        double equity = account == null ? initialCash : account.equity(currentPrice);
        return calculate(traderName, decisions, currentPrice, referencePrice, initialCash, equity, currentTick);
    }
}
