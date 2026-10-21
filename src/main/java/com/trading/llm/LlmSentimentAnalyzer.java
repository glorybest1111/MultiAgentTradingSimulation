package com.trading.llm;

import com.trading.market.KeywordSentimentAnalyzer;
import com.trading.market.NewsItem;
import com.trading.market.SentimentAnalyzer;

import java.util.Locale;

/**
 * 基于大模型的情绪分析器：{@link SentimentAnalyzer} 的第二种实现。
 *
 * <p>这是「预留替换为 LLMClient 的位置」这一承诺的真实落地：
 * 与关键词版实现同一接口，可在装配处一行替换，无需改动任何其他代码。
 *
 * <p>LLM 不可用（无 Key / 网络异常）时自动降级为关键词规则版，保证系统可用性。
 */
public class LlmSentimentAnalyzer implements SentimentAnalyzer {

    private static final String SYSTEM_PROMPT =
            "You are a financial sentiment classifier. "
                    + "Reply with ONE integer from -10 to 10 only. "
                    + "-10 means extremely bearish, 0 means neutral, 10 means extremely bullish.";

    private final LLMClient client;
    private final SentimentAnalyzer fallback;

    public LlmSentimentAnalyzer(LLMClient client) {
        this(client, new KeywordSentimentAnalyzer());
    }

    public LlmSentimentAnalyzer(LLMClient client, SentimentAnalyzer fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    @Override
    public double analyze(String headline) {
        if (headline == null || headline.isBlank() || client == null || !client.isAvailable()) {
            return fallback.analyze(headline);
        }
        String reply = client.complete(SYSTEM_PROMPT, "Headline: " + headline);
        Double score = parseScore(reply);
        if (score == null) {
            return fallback.analyze(headline);
        }
        return score;
    }

    @Override
    public double analyze(NewsItem item) {
        return item == null ? 0 : analyze(item.getHeadline());
    }

    /** 从模型回复中解析 [-10,10] 的整数并归一化到 [-1,1] */
    private Double parseScore(String reply) {
        if (reply == null) {
            return null;
        }
        String text = reply.trim().toUpperCase(Locale.ROOT);
        int bestIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                bestIndex = i;
                break;
            }
            if (c == '-' && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) {
                bestIndex = i;
                break;
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        int end = bestIndex;
        while (end < text.length() && (Character.isDigit(text.charAt(end))
                || (end == bestIndex && text.charAt(end) == '-'))) {
            end++;
        }
        try {
            double raw = Double.parseDouble(text.substring(bestIndex, end));
            return Math.max(-1.0, Math.min(1.0, raw / 10.0));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
