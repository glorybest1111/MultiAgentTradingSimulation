package com.trading.market;

/**
 * 新闻监听器：新闻发布后由 {@link NewsEngine} 推送，供 GUI 滚动播报栏使用。
 * 这样仿真核心不需要知道任何 UI 组件的存在。
 */
public interface NewsListener {

    /** 有新闻发布时回调（可能是随机新闻，也可能是管理员人工注入） */
    void onNews(NewsItem item);
}
