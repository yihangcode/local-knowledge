package com.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 配置
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.knowledge.repository")
@EnableMongoAuditing
public class MongoConfig {

    /**
     * 创建索引以支持关键词检索和快速查询
     * <p>
     * - baseId 索引：加速按知识库过滤查询
     * - itemId 索引：加速按知识条目过滤查询
     * - 文本索引：支持全文关键词检索
     * - 复合索引 (baseId + itemId)：加速联合查询
     * </p>
     */
    @Bean
    public boolean initIndexes(MongoTemplate mongoTemplate) {
        // baseId 单字段索引
        mongoTemplate.indexOps("knowledge_chunk").ensureIndex(
                new Index().on("baseId", Sort.Direction.ASC).named("idx_baseId"));

        // itemId 单字段索引
        mongoTemplate.indexOps("knowledge_chunk").ensureIndex(
                new Index().on("itemId", Sort.Direction.ASC).named("idx_itemId"));

        // baseId + itemId 复合索引
        mongoTemplate.indexOps("knowledge_chunk").ensureIndex(
                new Index()
                        .on("baseId", Sort.Direction.ASC)
                        .on("itemId", Sort.Direction.ASC)
                        .named("idx_baseId_itemId"));

        // content 文本索引（支持全文检索）
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("content")
                .build();
        mongoTemplate.indexOps("knowledge_chunk").ensureIndex(textIndex);

        return true;
    }
}
