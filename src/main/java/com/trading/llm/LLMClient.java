package com.trading.llm;

/**
 * 大模型访问抽象。
 *
 * <p>之所以抽成接口，是为了让「依赖 LLM 的高级功能」与「离线可复现的核心实验」解耦：
 * <ul>
 *   <li>{@link MockLLMClient}：本地规则版，无网络、无 Key、结果确定，默认使用</li>
 *   <li>{@link HttpLLMClient}：OpenAI 兼容协议的真实实现，通过环境变量注入 Key</li>
 * </ul>
 *
 * <p>核心撮合路径永远不依赖本接口，因此拔掉网络后全部实验仍可运行。
 */
public interface LLMClient {

    /**
     * 发起一次对话。
     *
     * @param systemPrompt 系统提示词（角色/画像/约束）
     * @param userPrompt   用户提示词（市场数据与指令）
     * @return 模型返回的纯文本
     */
    String complete(String systemPrompt, String userPrompt);

    /** 该客户端当前是否可用（Mock 恒为 true） */
    boolean isAvailable();

    /** 客户端名称，用于日志与可解释展示 */
    default String name() {
        return getClass().getSimpleName();
    }
}
