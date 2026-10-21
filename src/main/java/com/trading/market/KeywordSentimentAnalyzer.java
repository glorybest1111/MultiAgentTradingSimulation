package com.trading.market;

/**
 * 新闻情绪分析器默认实现：基于关键词规则打分。
 * 无外部依赖、离线可运行、结果可复现，适合课程实验与回测。
 * 示例：新闻 "Apple sales increase" → score = +1.0。
 */
public class KeywordSentimentAnalyzer implements SentimentAnalyzer {

    private static final String[] POSITIVE_WORDS = {
            "record", "upgrade", "beat", "buyback", "growth", "increase",
            "strong", "profit", "partnership", "innovation", "surge", "sales"
    };

    private static final String[] NEGATIVE_WORDS = {
            "recall", "lawsuit", "regulatory", "miss", "drop", "weak",
            "decline", "crash", "layoff", "shortage", "probe", "fine",
            "scandal", "faulty", "selloff", "cautious"
    };

    @Override
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
