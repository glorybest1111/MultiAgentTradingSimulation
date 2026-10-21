package com.trading.account;

/**
 * 做空机制开关。
 *
 * <p><b>为什么默认不是 DISABLED：</b>原系统允许账户出现负持仓
 * （做市商的卖价被对手吃掉后自然会持有空头），这是实验 1 排名数据的一部分。
 * 因此保留 {@link #LEGACY} 作为默认值，让历史实验逐比特可复现；
 * 需要研究保证金/强平时才显式切到 {@link #MARGIN}。
 */
public enum ShortSellingMode {

    /** 旧行为：不校验，允许任意净空头 */
    LEGACY,

    /** 保证金模式：按初始保证金率冻结资金，不足则拒单，并计提借券利息 */
    MARGIN,

    /** 禁止做空：任何会产生/加大净空头的卖单都被拒绝 */
    DISABLED
}
