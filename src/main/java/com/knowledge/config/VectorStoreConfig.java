package com.knowledge.config;

import com.knowledge.vectorstore.MongoVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * VectorStore 配置
 * <p>
 * 将 MongoVectorStore 注册为 VectorStore 接口的实现 Bean。
 * MongoVectorStore 本身已经是 @Component，这里额外注册为 VectorStore 类型，
 * 以便其他需要 VectorStore 接口的地方可以注入。
 * </p>
 */
@Configuration
public class VectorStoreConfig {

    /**
     * MongoDB VectorStore Bean
     * 当 knowledge.vector-store.type=mongodb 时注册
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.vector-store.type", havingValue = "mongodb", matchIfMissing = true)
    public VectorStore vectorStore(MongoVectorStore mongoVectorStore) {
        return mongoVectorStore;
    }
}
