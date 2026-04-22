package com.knowledge.enums;

import lombok.Getter;

/**
 * Embedding Provider 枚举
 */
@Getter
public enum EmbeddingProvider {

    OPENAI("openai", "OpenAI Embedding"),
    ZHIPU("zhipu", "智谱Embedding"),
    QWEN("qwen", "通义Embedding"),
    DEEPSEEK("deepseek", "DeepSeek Embedding"),
    OLLAMA("ollama", "Ollama本地Embedding");

    private final String code;
    private final String name;

    EmbeddingProvider(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static EmbeddingProvider fromCode(String code) {
        for (EmbeddingProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return OPENAI;
    }
}
