package com.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 向量生成服务
 * <p>
 * 封装 Spring AI EmbeddingModel，支持批量文本向量化。
 * 适配 Spring AI 1.0.0-M4 API。
 * <p>
 * 运行时切换：通过 ApplicationContext 按 knowledge.embedding.provider
 * 动态获取对应的 EmbeddingModel Bean，而不是使用启动时固定的 Bean。
 * </p>
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final ApplicationContext applicationContext;
    private final com.knowledge.config.KnowledgeProperties knowledgeProperties;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            ApplicationContext applicationContext,
                            com.knowledge.config.KnowledgeProperties knowledgeProperties) {
        this.embeddingModel = embeddingModel;
        this.applicationContext = applicationContext;
        this.knowledgeProperties = knowledgeProperties;
    }

    /**
     * 运行时获取当前 Provider 对应的 EmbeddingModel
     */
    private EmbeddingModel resolveEmbeddingModel() {
        String provider = knowledgeProperties.getEmbedding().getProvider();
        String beanPrefix = resolveBeanPrefix(provider);
        String beanName = beanPrefix + "EmbeddingModel";
        try {
            return applicationContext.getBean(beanName, EmbeddingModel.class);
        } catch (Exception e) {
            log.warn("Failed to resolve EmbeddingModel for provider={}, falling back to primary. Error: {}",
                    provider, e.getMessage());
            return embeddingModel;
        }
    }

    private static String resolveBeanPrefix(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "openAi";
            case "deepseek" -> "openAi";
            case "zhipuai", "zhipu", "glm" -> "zhiPuAi";
            case "ollama" -> "ollama";
            default -> "openAi";
        };
    }

    /**
     * 对单个文本生成 Embedding 向量
     *
     * @param text 输入文本
     * @return 向量数组
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            float[] embedding = resolveEmbeddingModel().embed(text);
            log.debug("Generated embedding for text (length={}), vector dim={}", text.length(), embedding.length);
            return embedding;
        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage(), e);
            throw new RuntimeException("向量生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量生成 Embedding 向量
     * <p>
     * 分批调用，避免单次请求过大
     * </p>
     *
     * @param texts    文本列表
     * @param batchSize 每批大小
     * @return 向量列表（与输入文本一一对应）
     */
    public List<float[]> embedBatch(List<String> texts, int batchSize) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                // Spring AI 1.0.0-M4: EmbeddingRequest 只接受 List<String>
                EmbeddingRequest request = new EmbeddingRequest(batch, null);
                // Spring AI 1.0.0-M4: 使用 call() 方法
                EmbeddingResponse response = resolveEmbeddingModel().call(request);

                // 从 response 中逐个获取 embedding
                for (int j = 0; j < batch.size(); j++) {
                    float[] embedding = response.getResults().get(j).getOutput();
                    allEmbeddings.add(embedding);
                }

                log.debug("Embedded batch {}/{}, size={}", (i / batchSize + 1),
                        (texts.size() + batchSize - 1) / batchSize, batch.size());
            } catch (Exception e) {
                log.error("Failed to embed batch starting at index {}: {}", i, e.getMessage(), e);
                // 为失败的批次填充空向量
                for (int j = 0; j < batch.size(); j++) {
                    allEmbeddings.add(new float[0]);
                }
            }
        }

        return allEmbeddings;
    }

    /**
     * 批量生成 Embedding 向量（默认批大小 10）
     */
    public List<float[]> embedBatch(List<String> texts) {
        return embedBatch(texts, 10);
    }

    /**
     * 获取当前 Embedding 模型的向量维度
     */
    public int dimensions() {
        try {
            return resolveEmbeddingModel().dimensions();
        } catch (Exception e) {
            // 某些实现可能不支持，用默认值
            return 1536;
        }
    }
}
