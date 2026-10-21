package com.trading.risk;

import com.trading.model.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑点模型（撮合路径专用）：订单过大或簿深不足时，实际成交价相对最优价恶化。
 *
 * <p>职责边界：本类只负责撮合时「订单簿被吃空之后」的<b>虚拟流动性</b>定价，
 * 由 {@link com.trading.engine.MatchingEngine} 按层逐笔调用。
 *
 * <p>「按订单簿层级行走算均价」属于事前估算，已迁移至 {@link SlippageEstimator}，
 * 不再放在撮合路径的类里，避免误解成交口径。
 *
 * <p>虚拟流动性：订单簿被吃空后，市价单可进入虚拟深度——
 * 共 maxLevels 层、每层 depthPerLevel 股，价格逐层恶化 perLevel；
 * 虚拟深度耗尽后市价单剩余部分取消（模拟真实市场中流动性不足导致的部分成交）。
 */
public class SlippageModel {

    /** 虚拟深度每深入一层，成交价恶化的幅度 */
    private final double perLevel;

    /** 虚拟深度层数与每层容量 */
    private final int maxLevels;
    private final int depthPerLevel;

    public SlippageModel(double perLevel) {
        this(perLevel, 2, 10);
    }

    public SlippageModel(double perLevel, int maxLevels, int depthPerLevel) {
        this.perLevel = perLevel;
        this.maxLevels = maxLevels;
        this.depthPerLevel = depthPerLevel;
    }

    /** 簿深耗尽后第 level 层虚拟流动性的成交价（买向上加价，卖向下减价） */
    public double priceAfterLevels(double basePrice, int level, Side side) {
        double delta = perLevel * level;
        return side == Side.BUY ? basePrice + delta : basePrice - delta;
    }

    /** 虚拟深度总容量（每期重置） */
    public int virtualCapacity() {
        return maxLevels * depthPerLevel;
    }

    /** 虚拟深度每层容量 */
    public int getDepthPerLevel() {
        return depthPerLevel;
    }

    /** 已用股数对应的虚拟层数（从 1 开始） */
    public int levelFor(int usedQuantity) {
        return Math.min(maxLevels, usedQuantity / depthPerLevel + 1);
    }

    /** 第 level 层内剩余可成交数量 */
    public int remainingInLevel(int usedQuantity) {
        return depthPerLevel - usedQuantity % depthPerLevel;
    }

    /** 各虚拟层的价格阶梯（用于 GUI 展示隐藏流动性成本） */
    public List<Double> levelPrices(double basePrice, Side side) {
        List<Double> prices = new ArrayList<>();
        for (int i = 1; i <= maxLevels; i++) {
            prices.add(priceAfterLevels(basePrice, i, side));
        }
        return prices;
    }

    public double getPerLevel() {
        return perLevel;
    }

}
