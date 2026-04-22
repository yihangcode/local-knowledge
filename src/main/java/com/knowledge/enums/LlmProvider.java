package com.knowledge.enums;

import lombok.Getter;

/**
 * LLM Provider 枚举
 */
@Getter
public enum LlmProvider {

    OPENAI("openai", "OpenAI"),
    ZHIPU("zhipu", "智谱GLM"),
    QWEN("qwen", "通义千问"),
    DEEPSEEK("deepseek", "DeepSeek"),
    OLLAMA("ollama", "Ollama本地模型");

    private final String code;
    private final String name;

    LlmProvider(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static LlmProvider fromCode(String code) {
        for (LlmProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return OPENAI;
    }
}
