package com.trading.simulation;

/**
 * 仿真配置：价格过程、新闻、费用、参与方开关等参数集中管理，
 * 保证多次实验（如做市商前后对比）除实验变量外完全一致。
 */
public class SimulationConfig {

    public String symbol = "AAPL";
    public double initialPrice = 150;
    public int ticks = 300;                  // 仿真周期数
    public long seed = 42;                   // 随机种子，保证可复现
    public double volatility = 0.01;         // 每期价格波动率
    public double drift = 0.0;               // 基础漂移
    public double newsProbability = 0.10;    // 每期新闻概率
    public double newsShockScale = 0.05;     // 新闻冲击对价格的瞬时影响
    public double moodDecay = 0.9;           // 市场情绪衰减系数
    public double moodScale = 0.0005;        // 情绪对价格漂移的影响
    public double initialCash = 100_000;     // 每个账户初始资金
    public double feeRate = 0.001;           // 手续费率 0.1%
    public double slippagePerLevel = 0.10;   // 滑点：每层虚拟深度的价格恶化幅度
    public boolean marketMakerEnabled = true;
    public boolean newsEnabled = true;
    public boolean debateEnabled = true;
    public boolean humanEnabled = false;

    /** 复制配置用于对比实验 */
    public SimulationConfig copy() {
        SimulationConfig c = new SimulationConfig();
        c.symbol = symbol;
        c.initialPrice = initialPrice;
        c.ticks = ticks;
        c.seed = seed;
        c.volatility = volatility;
        c.drift = drift;
        c.newsProbability = newsProbability;
        c.newsShockScale = newsShockScale;
        c.moodDecay = moodDecay;
        c.moodScale = moodScale;
        c.initialCash = initialCash;
        c.feeRate = feeRate;
        c.slippagePerLevel = slippagePerLevel;
        c.marketMakerEnabled = marketMakerEnabled;
        c.newsEnabled = newsEnabled;
        c.debateEnabled = debateEnabled;
        c.humanEnabled = humanEnabled;
        return c;
    }

}
