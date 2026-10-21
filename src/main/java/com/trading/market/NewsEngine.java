package com.trading.market;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 新闻引擎：随机或人工发布市场新闻，经情绪分析器计算冲击力度。
 * 新闻冲击会直接影响价格并形成市场情绪（mood），供 Agent 决策参考。
 *
 * <p>支持两种新闻来源：
 * <ul>
 *   <li>随机发布：{@link #maybeGenerate()}，由仿真主循环按概率调用</li>
 *   <li>人工注入：{@link #injectNews(String)}，供管理员控制台/外部系统调用</li>
 * </ul>
 * 两种方式都会通过 {@link NewsListener} 推送给订阅者（如 GUI 新闻栏）。
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
    private final List<NewsListener> listeners = new CopyOnWriteArrayList<>();
    private volatile double probability;

    public NewsEngine(SentimentAnalyzer analyzer, Random rng, double probability) {
        this.analyzer = analyzer;
        this.rng = rng;
        this.probability = probability;
    }

    /** 每期以 probability 的概率发布一条新闻；不发布时返回 null */
    public synchronized NewsItem maybeGenerate() {
        if (rng.nextDouble() > probability) {
            return null;
        }
        String headline = NEWS_POOL[rng.nextInt(NEWS_POOL.length)];
        // 情绪分数 × 随机强度 (0.7 ~ 1.3)
        double impact = analyzer.analyze(headline) * (0.7 + rng.nextDouble() * 0.6);
        return publish(headline, impact);
    }

    /**
     * 人工注入新闻（管理员权限接口）。
     * 同 Apollo：冲击力度 = 情绪分 × 随机强度，保证与随机新闻同一量化口径。
     *
     * @param content 新闻正文/标题
     * @return 生成的新闻对象，内容为空白时返回 null
     */
    public synchronized NewsItem injectNews(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        double impact = analyzer.analyze(content) * (0.7 + rng.nextDouble() * 0.6);
        return publish(content.trim(), impact);
    }

    /** 指定确定冲击力度地发布（用于可复现实验） */
    public synchronized NewsItem injectNews(String content, double impact) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return publish(content.trim(), impact);
    }

    private NewsItem publish(String headline, double impact) {
        NewsItem item = new NewsItem(headline, impact);
        for (NewsListener listener : listeners) {
            // 监听器异常不应影响主循环
            try {
                listener.onNews(item);
            } catch (RuntimeException ignored) {
                // 日志系统未接入时静默处理
            }
        }
        return item;
    }

    public void addListener(NewsListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(NewsListener listener) {
        listeners.remove(listener);
    }

    /** 随机新闻开关：管理员 GUI 的「自动发布」开关直接调它 */
    public void setProbability(double probability) {
        this.probability = Math.max(0, Math.min(1, probability));
    }

    public double getProbability() {
        return probability;
    }
}
