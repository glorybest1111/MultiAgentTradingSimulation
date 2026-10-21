package com.trading.engine;

import com.trading.risk.ValidationResult;

/**
 * 做空策略：撮合引擎在做空行为上的扩展点。
 *
 * <p>引擎本身不认识「账户」，因此把做空权限判断、保证金校验与成交后的记账
 * 全部委托给外部实现，保持撮合引擎的纯粹性。
 *
 * <p>三种模式：
 * <ul>
 *   <li>{@link com.trading.account.ShortSellingMode#LEGACY}：旧行为，不做任何限制
 *       （保证历史实验数据可复现，是默认行为）</li>
 *   <li>{@link com.trading.account.ShortSellingMode#MARGIN}：需要初始保证金，
 *       现金不足则拒单，并记录借券与利息</li>
 *   <li>{@link com.trading.account.ShortSellingMode#DISABLED}：禁止产生任何净空头</li>
 * </ul>
 */
public interface ShortSellPolicy {

    /** 该卖单是否可以成交（卖空需要在引擎真正成交前做校验） */
    ValidationResult checkSell(String traderName, String symbol, double price, int quantity);

    /** 卖空成交后回写保证金/借券记账 */
    void onSellFill(String traderName, String symbol, double price, int quantity);
}
