package com.trading.simulation;

/**
 * 仿真配置：价格过程、新闻、费用、做空、风控、Dashboard 等参数集中管理，
 * 保证多次实验（如做市商前后对比、情绪开关对比）除实验变量外完全一致。
 */
public class SimulationConfig {

    // ---------------- 基础 ----------------
    public String symbol = "AAPL";
    /** 实验名：用于日志分文件与控制台标题 */
    public String experimentName = "simulation";
    public double initialPrice = 150;
    public int ticks = 300;                  // 仿真周期数
    public long seed = 42;                   // 随机种子，保证可复现

    // ---------------- 价格过程 ----------------
    public double volatility = 0.01;         // 每期价格波动率
    public double drift = 0.0;               // 基础漂移
    public double newsProbability = 0.10;    // 每期新闻概率
    public double newsShockScale = 0.05;     // 新闻冲击对价格的瞬时影响
    public double moodDecay = 0.9;           // 市场情绪衰减系数
    public double moodScale = 0.0005;        // 情绪对价格漂移的影响
    /** 情绪是否参与 Agent 决策（关闭后即为「无情绪」对照组） */
    public boolean sentimentAffectsStrategies = true;

    // ---------------- 账户与费用 ----------------
    public double initialCash = 100_000;     // 每个账户初始资金
    public double feeRate = 0.001;           // 手续费率 0.1%
    public double slippagePerLevel = 0.10;   // 滑点：每层虚拟深度的价格恶化幅度

    // ---------------- 做空 / 保证金 ----------------
    public boolean shortSellingEnabled = false;   // 是否允许裸卖空
    public double initialMarginRate = 0.50;       // 初始保证金率
    public double maintenanceMarginRate = 0.30;   // 维持保证金率，低于该水平触发强平
    public double borrowInterestPerTick = 0.00002; // 借券利息（每期按市值计提）

    // ---------------- 风控：涨跌停 + 熔断 ----------------
    public boolean riskControlEnabled = true;
    public double priceLimitPct = 0.10;           // 相对参考价（初始价）±10% 拒单
    public double circuitBreakerDropPct = 0.05;   // 窗口内跌幅超过 5% 触发熔断
    public int circuitBreakerWindow = 20;         // 熔断观察窗口（期数）
    public long circuitBreakerHaltMillis = 60_000L; // 熔断暂停时长（真实时钟模式）
    public int circuitBreakerHaltTicks = 10;      // 熔断暂停时长（仿真周期模式）
    /** true 时熔断按真实毫秒计时；false 时按仿真期数计时（离线实验推荐） */
    public boolean realTimeHalt = false;

    // ---------------- 控制台 Dashboard ----------------
    /** 每 N 期打印一次实时排名 Dashboard；0 表示关闭 */
    public int dashboardInterval = 50;

    // ---------------- 控制台 Dashboard ----------------

    /** 复制配置用于对比实验 */
    public SimulationConfig copy() {
        SimulationConfig c = new SimulationConfig();
        c.symbol = symbol;
        c.experimentName = experimentName;
        c.initialPrice = initialPrice;
        c.ticks = ticks;
        c.seed = seed;
        c.volatility = volatility;
        c.drift = drift;
        c.newsProbability = newsProbability;
        c.newsShockScale = newsShockScale;
        c.moodDecay = moodDecay;
        c.moodScale = moodScale;
        c.sentimentAffectsStrategies = sentimentAffectsStrategies;
        c.initialCash = initialCash;
        c.feeRate = feeRate;
        c.slippagePerLevel = slippagePerLevel;
        c.shortSellingEnabled = shortSellingEnabled;
        c.initialMarginRate = initialMarginRate;
        c.maintenanceMarginRate = maintenanceMarginRate;
        c.borrowInterestPerTick = borrowInterestPerTick;
        c.riskControlEnabled = riskControlEnabled;
        c.priceLimitPct = priceLimitPct;
        c.circuitBreakerDropPct = circuitBreakerDropPct;
        c.circuitBreakerWindow = circuitBreakerWindow;
        c.circuitBreakerHaltMillis = circuitBreakerHaltMillis;
        c.circuitBreakerHaltTicks = circuitBreakerHaltTicks;
        c.realTimeHalt = realTimeHalt;
        c.dashboardInterval = dashboardInterval;
        return c;
    }

}
