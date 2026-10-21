package com.trading.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 真实大模型客户端（OpenAI 兼容协议 /chat/completions）。
 *
 * <p>仅使用 JDK 内置的 {@link HttpClient}，不引入任何第三方依赖。
 * 通过环境变量注入配置，避免把密钥写进代码：
 * <ul>
 *   <li>{@code LLM_API_KEY}：密钥，缺省时本客户端自动不可用</li>
 *   <li>{@code LLM_BASE_URL}：接口地址，默认 https://api.openai.com/v1/chat/completions</li>
 *   <li>{@code LLM_MODEL}：模型名，默认 gpt-4o-mini</li>
 * </ul>
 *
 * <p>任何网络异常都会被捕获并降级为 HOLD，绝不因为 LLM 故障中断撮合主循环。
 */
public class HttpLLMClient implements LLMClient {

    private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpLLMClient() {
        this(System.getenv("LLM_API_KEY"),
                System.getenv("LLM_BASE_URL"),
                System.getenv("LLM_MODEL"));
    }

    public HttpLLMClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_URL : baseUrl.trim();
        this.model = (model == null || model.isBlank()) ? "gpt-4o-mini" : model.trim();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return "ACTION=HOLD QTY=0 CONFIDENCE=0.00 REASON=LLM unavailable: missing LLM_API_KEY";
        }
        try {
            String body = buildBody(systemPrompt, userPrompt);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return extractContent(response.body());
        } catch (Exception e) {
            return String.format("ACTION=HOLD QTY=0 CONFIDENCE=0.00 REASON=LLM error: %s",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replace("\n", " "));
        }
    }

    private String buildBody(String systemPrompt, String userPrompt) {
        StringBuilder payload = new StringBuilder();
        payload.append('{')
                .append("\"model\":\"").append(escape(model)).append("\",")
                .append("\"temperature\":0.2,")
                .append("\"messages\":[")
                .append("{\"role\":\"system\",\"content\":\"").append(escape(systemPrompt)).append("\"},")
                .append("{\"role\":\"user\",\"content\":\"").append(escape(userPrompt)).append("\"}")
                .append("]}");
        return payload.toString();
    }

    /**
     * 极简 JSON 提取：避免为了一个字段引入 JSON 库。
     * 解析失败时返回原文，由上层决策解析器兜底为 HOLD。
     */
    private String extractContent(String json) {
        if (json == null || json.isBlank()) {
            return "ACTION=HOLD QTY=0 CONFIDENCE=0.00 REASON=empty LLM response";
        }
        String key = "\"content\":";
        int index = json.indexOf(key);
        if (index < 0) {
            return json;
        }
        int start = json.indexOf('"', index + key.length());
        if (start < 0) {
            return json;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++;
                if (i < json.length()) {
                    char n = json.charAt(i);
                    sb.append(n == 'n' ? '\n' : n);
                }
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
