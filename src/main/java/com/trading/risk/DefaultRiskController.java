package com.trading.risk;

import com.trading.model.Order;

/**
 * 默认风控拦截器：涨跌停板 + 熔断，串联调用。
 *
 * <p>它被注入到 {@code MatchingEngine} 之前执行（撮合前置拦截器），
 * 拒绝的订单不会进入订单簿，也不会产生任何成交。
 */
public class DefaultRiskController implements OrderValidator {

    private final PriceLimitRule priceLimit;
    private final CircuitBreaker circuitBreaker;

    public DefaultRiskController(PriceLimitRule priceLimit, CircuitBreaker circuitBreaker) {
        this.priceLimit = priceLimit;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ValidationResult validate(Order order, MarketContext context) {
        if (circuitBreaker != null) {
            ValidationResult halted = circuitBreaker.check(context);
            if (!halted.isAccepted()) {
                return halted;
            }
        }
        if (priceLimit != null) {
            return priceLimit.check(order, context);
        }
        return ValidationResult.accept();
    }

    public PriceLimitRule getPriceLimit() {
        return priceLimit;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
