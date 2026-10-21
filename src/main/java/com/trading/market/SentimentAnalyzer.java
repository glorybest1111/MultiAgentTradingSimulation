package com.trading.market;

/**
 * 新闻情绪分析器接口。
 *
 * <p>原来是一个硬编码的关键词规则类，现在抽离为接口，支持两种实现互相替换：
 * <ul>
 *   <li>{@link KeywordSentimentAnalyzer}：关键词规则引擎（默认，离线、无外部依赖、结果可复现）</li>
 *   <li>{@code com.trading.llm.LlmSentimentAnalyzer}：调用真实大模型获得情绪分</li>
 * </ul>
 *
 * <p>替换方式：
 * <pre>{@code
 * SentimentAnalyzer analyzer = new KeywordSentimentAnalyzer();
 * // 或
 * SentimentAnalyzer analyzer = new LlmSentimentAnalyzer(myLlmClient);
 * }</pre>
 */
public interface SentimentAnalyzer {

    /**
     * 分析新闻标题，返回情绪分数 [-1, 1]。
     * 0 = 中性，正 = 利好，负 = 利空。
     */
    double analyze(String headline);

    /** 按 NewsItem 分析的便捷方法 */
    default double analyze(NewsItem item) {
        return item == null ? 0 : analyze(item.getHeadline());
    }
}
