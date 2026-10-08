package com.trading.market;

import java.util.Random;

/**
 * 新闻引擎：随机发布市场新闻，经情绪分析器计算冲击力度。
 * 新闻冲击会直接影响价格并形成市场情绪（mood），供 Agent 决策参考。
 */
public class NewsEngine {

    /** 新闻池：标题（情绪分析器据此打分） */
    private static final String[] NEWS_POOL = {
            "Apple reports record iPhone sales",
            "Apple faces EU regulatory scrutiny",
            "Analysts upgrade Apple to Buy",
            "Apple recalls faulty MacBook batteries",
            "Supply chain shortage hits Apple production",
            "Apple announces share buyback program",
            "Apple beats quarterly earnings estimates",
            "Apple misses revenue expectations",
            "Apple unveils innovative AI features",
            "Tech selloff deepens as investors turn cautious"
    };

    private final SentimentAnalyzer analyzer;
    private final Random rng;
    private final double probability;

    public NewsEngine(SentimentAnalyzer analyzer, Random rng, double probability) {
        this.analyzer = analyzer;
        this.rng = rng;
        this.probability = probability;
    }

    /** 每期以 probability 的概率发布一条新闻；不发布时返回 null */
    public NewsItem maybeGenerate() {
        if (rng.nextDouble() > probability) {
            return null;
        }
        String headline = NEWS_POOL[rng.nextInt(NEWS_POOL.length)];
        // 情绪分数 × 随机强度 (0.7 ~ 1.3)
        double impact = analyzer.analyze(headline) * (0.7 + rng.nextDouble() * 0.6);
        return new NewsItem(headline, impact);
    }

}
