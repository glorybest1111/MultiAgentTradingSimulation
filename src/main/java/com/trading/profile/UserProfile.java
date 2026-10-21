package com.trading.profile;

/**
 * 用户画像：由历史订单与账户数据计算得到，供 LLM 投顾选择策略。
 */
public class UserProfile {

    private final String traderName;
    private final int orderCount;        // 累计下单次数（不含 HOLD）
    private final int tradedVolume;      // 累计成交股数
    private final double turnoverRate;   // 换手率：每期平均下单次数
    private final double avgOrderNotional; // 平均单笔成交额
    private final double userReturn;     // 用户收益率
    private final double marketReturn;   // 同期大盘（标的价格）收益率
    private final double excessReturn;   // 相对大盘盈亏比
    private final double winRate;        // 盈利平仓占比
    private final ProfileTag tag;

    public UserProfile(String traderName, int orderCount, int tradedVolume, double turnoverRate,
                       double avgOrderNotional, double userReturn, double marketReturn,
                       double excessReturn, double winRate, ProfileTag tag) {
        this.traderName = traderName;
        this.orderCount = orderCount;
        this.tradedVolume = tradedVolume;
        this.turnoverRate = turnoverRate;
        this.avgOrderNotional = avgOrderNotional;
        this.userReturn = userReturn;
        this.marketReturn = marketReturn;
        this.excessReturn = excessReturn;
        this.winRate = winRate;
        this.tag = tag;
    }

    public String getTraderName() {
        return traderName;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public int getTradedVolume() {
        return tradedVolume;
    }

    public double getTurnoverRate() {
        return turnoverRate;
    }

    public double getAvgOrderNotional() {
        return avgOrderNotional;
    }

    public double getUserReturn() {
        return userReturn;
    }

    public double getMarketReturn() {
        return marketReturn;
    }

    public double getExcessReturn() {
        return excessReturn;
    }

    public double getWinRate() {
        return winRate;
    }

    public ProfileTag getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return String.format("Profile{%s tag=%s turnover=%.2f orderAvg=$%.0f return=%+.2f%% vs market=%+.2f%% win=%.0f%%}",
                traderName, tag.getCode(), turnoverRate, avgOrderNotional,
                userReturn * 100, marketReturn * 100, winRate * 100);
    }
}
