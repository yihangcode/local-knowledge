package com.knowledge.enums;

import lombok.Getter;

/**
 * 消息来源枚举
 */
@Getter
public enum MessageSource {

    KNOWLEDGE("KNOWLEDGE", "知识库匹配"),
    AI("AI", "AI生成"),
    HYBRID("HYBRID", "混合来源");

    private final String code;
    private final String description;

    MessageSource(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
