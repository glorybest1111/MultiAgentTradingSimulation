package com.trading.risk;

import com.trading.model.Order;

/**
 * 订单风控拦截器接口：在订单进入撮合之前执行检查。
 * 通过注入不同的实现，可以在不改动撮合引擎的前提下替换/扩展风控规则。
 */
public interface OrderValidator {

    /** 校验订单是否允许进入撮合 */
    ValidationResult validate(Order order, MarketContext context);
}
