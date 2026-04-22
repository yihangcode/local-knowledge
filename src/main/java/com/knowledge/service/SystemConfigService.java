package com.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.mapper.SystemConfigMapper;
import com.knowledge.model.dto.ConfigUpdateDTO;
import com.knowledge.model.entity.SystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 * <p>
 * 管理 LLM Provider、Embedding Provider、检索参数等运行时配置
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final KnowledgeProperties knowledgeProperties;

    /**
     * 获取所有配置（以 Map 形式返回）
     * <p>
     * 配置键统一使用点号格式（与 init.sql 一致）：llm.provider, embedding.provider 等
     * </p>
     */
    public Map<String, String> getAllConfigs() {
        List<SystemConfig> configs = systemConfigMapper.selectList(null);
        Map<String, String> map = new HashMap<>();
        for (SystemConfig config : configs) {
            map.put(config.getConfigKey(), config.getConfigValue());
        }

        // 补充默认值（使用点号格式，与 init.sql 一致）
        map.putIfAbsent("llm.provider", knowledgeProperties.getLlm().getProvider());
        map.putIfAbsent("embedding.provider", knowledgeProperties.getEmbedding().getProvider());
        map.putIfAbsent("retrieval.fallback_threshold", String.valueOf(knowledgeProperties.getRetrieval().getFallbackThreshold()));
        map.putIfAbsent("retrieval.fallback_enabled", "true");
        map.putIfAbsent("chunk.size", String.valueOf(knowledgeProperties.getChunk().getSize()));
        map.putIfAbsent("chunk.overlap", String.valueOf(knowledgeProperties.getChunk().getOverlap()));
        map.putIfAbsent("retrieval.top_k", String.valueOf(knowledgeProperties.getRetrieval().getTopK()));
        map.putIfAbsent("retrieval.keyword_weight", String.valueOf(knowledgeProperties.getRetrieval().getKeywordWeight()));
        map.putIfAbsent("retrieval.semantic_weight", String.valueOf(knowledgeProperties.getRetrieval().getSemanticWeight()));
        map.putIfAbsent("vector_store.type", knowledgeProperties.getVectorStore().getType());

        return map;
    }

    /**
     * 获取单个配置
     */
    public String getConfig(String key) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, key);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 更新系统配置
     */
    @Transactional
    public void updateConfig(ConfigUpdateDTO dto) {
        // 参数范围校验
        dto.validate();
        if (dto.getLlmProvider() != null) {
            upsertConfig("llm.provider", dto.getLlmProvider(), "llm");
            knowledgeProperties.getLlm().setProvider(dto.getLlmProvider());
        }
        if (dto.getEmbeddingProvider() != null) {
            upsertConfig("embedding.provider", dto.getEmbeddingProvider(), "embedding");
            knowledgeProperties.getEmbedding().setProvider(dto.getEmbeddingProvider());
        }
        if (dto.getFallbackThreshold() != null) {
            upsertConfig("retrieval.fallback_threshold", String.valueOf(dto.getFallbackThreshold()), "retrieval");
            knowledgeProperties.getRetrieval().setFallbackThreshold(dto.getFallbackThreshold());
        }
        if (dto.getFallbackEnabled() != null) {
            upsertConfig("retrieval.fallback_enabled", String.valueOf(dto.getFallbackEnabled()), "retrieval");
        }
        if (dto.getChunkSize() != null) {
            upsertConfig("chunk.size", String.valueOf(dto.getChunkSize()), "chunk");
            knowledgeProperties.getChunk().setSize(dto.getChunkSize());
        }
        if (dto.getChunkOverlap() != null) {
            upsertConfig("chunk.overlap", String.valueOf(dto.getChunkOverlap()), "chunk");
            knowledgeProperties.getChunk().setOverlap(dto.getChunkOverlap());
        }
        if (dto.getTopK() != null) {
            upsertConfig("retrieval.top_k", String.valueOf(dto.getTopK()), "retrieval");
            knowledgeProperties.getRetrieval().setTopK(dto.getTopK());
        }
        if (dto.getKeywordWeight() != null) {
            upsertConfig("retrieval.keyword_weight", String.valueOf(dto.getKeywordWeight()), "retrieval");
            knowledgeProperties.getRetrieval().setKeywordWeight(dto.getKeywordWeight());
            knowledgeProperties.getRetrieval().setSemanticWeight(1.0 - dto.getKeywordWeight());
        }

        log.info("System config updated: {}", dto);
    }

    /**
     * 插入或更新配置项
     *
     * @param key         配置键（点号格式，如 llm.provider）
     * @param value       配置值
     * @param configGroup 配置分组（llm/embedding/retrieval/chunk）
     */
    private void upsertConfig(String key, String value, String configGroup) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, key);
        SystemConfig existing = systemConfigMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setConfigValue(value);
            existing.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.updateById(existing);
        } else {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigGroup(configGroup);
            config.setDescription("Config: " + key);
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.insert(config);
        }
    }
}
