package com.knowledge.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 系统配置更新 DTO
 * <p>
 * 包含参数范围校验，防止非法值写入配置
 * </p>
 */
@Data
public class ConfigUpdateDTO {

    /**
     * LLM Provider（openai / zhipuai / ollama / deepseek）
     */
    @Pattern(regexp = "^(openai|zhipuai|zhipu|glm|ollama|deepseek)?$", message = "不支持的 LLM Provider")
    private String llmProvider;

    /**
     * Embedding Provider
     */
    @Pattern(regexp = "^(openai|zhipuai|zhipu|glm|ollama|deepseek)?$", message = "不支持的 Embedding Provider")
    private String embeddingProvider;

    /**
     * Fallback 阈值（0-1之间）
     */
    @DecimalMin(value = "0.0", inclusive = true, message = "Fallback 阈值不能小于 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "Fallback 阈值不能大于 1")
    private Double fallbackThreshold;

    /**
     * 是否启用 Fallback
     */
    private Boolean fallbackEnabled;

    /**
     * 分块大小（字符数），最小 50，最大 10000
     */
    @Min(value = 50, message = "分块大小不能小于 50")
    @Max(value = 10000, message = "分块大小不能大于 10000")
    private Integer chunkSize;

    /**
     * 分块重叠（字符数），必须小于 chunkSize
     */
    @Min(value = 0, message = "分块重叠不能小于 0")
    @Max(value = 5000, message = "分块重叠不能大于 5000")
    private Integer chunkOverlap;

    /**
     * 检索返回 Top K
     */
    @Min(value = 1, message = "TopK 不能小于 1")
    @Max(value = 100, message = "TopK 不能大于 100")
    private Integer topK;

    /**
     * 关键词权重（0-1之间，语义权重 = 1 - 关键词权重）
     */
    @DecimalMin(value = "0.0", inclusive = true, message = "关键词权重不能小于 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "关键词权重不能大于 1")
    private Double keywordWeight;

    /**
     * 业务校验：chunkOverlap 必须小于 chunkSize
     */
    public void validate() {
        if (chunkSize != null && chunkOverlap != null && chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("分块重叠(" + chunkOverlap + ")必须小于分块大小(" + chunkSize + ")");
        }
    }
}
