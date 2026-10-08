package com.trading.market;

/**
 * 新闻情绪分析器：基于关键词规则打分。
 * 采用规则引擎替代真实 LLM 调用——无外部依赖、结果可复现、离线可运行；
 * 架构上预留了替换为 LLMClient 的位置（见 agent 包说明）。
 * 示例：新闻 "Apple sales increase" → sentiment = positive, score = +1.0。
 */
public class SentimentAnalyzer {

    private static final String[] POSITIVE_WORDS = {
            "record", "upgrade", "beat", "buyback", "growth", "increase",
            "strong", "profit", "partnership", "innovation", "surge", "sales"
    };

    private static final String[] NEGATIVE_WORDS = {
            "recall", "lawsuit", "regulatory", "miss", "drop", "weak",
            "decline", "crash", "layoff", "shortage", "probe", "fine",
            "scandal", "faulty", "selloff", "cautious"
    };

    /**
     * 分析新闻标题，返回情绪分数 [-1, 1]。
     * 0 = 中性，正 = 利好，负 = 利空。
     */
    public double analyze(String headline) {
        if (headline == null || headline.isBlank()) {
            return 0;
        }
        String text = headline.toLowerCase();
        int positive = countHits(POSITIVE_WORDS, text);
        int negative = countHits(NEGATIVE_WORDS, text);
        if (positive == 0 && negative == 0) {
            return 0;
        }
        return (double) (positive - negative) / (positive + negative);
    }

    private int countHits(String[] words, String text) {
        int hits = 0;
        for (String word : words) {
            if (text.contains(word)) {
                hits++;
            }
        }
        return hits;
    }

}
