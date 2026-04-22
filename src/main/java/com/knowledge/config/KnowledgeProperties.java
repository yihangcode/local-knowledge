package com.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库系统配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    /**
     * 向量存储配置
     */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    /**
     * 检索配置
     */
    private RetrievalConfig retrieval = new RetrievalConfig();

    /**
     * 分块配置
     */
    private ChunkConfig chunk = new ChunkConfig();

    /**
     * LLM 配置
     */
    private LlmConfig llm = new LlmConfig();

    /**
     * Embedding 配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    @Data
    public static class VectorStoreConfig {
        /**
         * 向量存储类型：mongodb / milvus / chroma
         */
        private String type = "mongodb";
    }

    @Data
    public static class RetrievalConfig {
        /**
         * Fallback 阈值
         */
        private double fallbackThreshold = 0.6;
        /**
         * Top-K 结果数
         */
        private int topK = 5;
        /**
         * 关键词检索权重
         */
        private double keywordWeight = 0.3;
        /**
         * 语义检索权重
         */
        private double semanticWeight = 0.7;
    }

    @Data
    public static class ChunkConfig {
        /**
         * 分块大小（字符数）
         */
        private int size = 500;
        /**
         * 分块重叠（字符数）
         */
        private int overlap = 50;
    }

    @Data
    public static class LlmConfig {
        /**
         * 当前 LLM Provider
         */
        private String provider = "openai";
    }

    @Data
    public static class EmbeddingConfig {
        /**
         * 当前 Embedding Provider
         */
        private String provider = "openai";
    }
}
