package com.knowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM Provider 配置类
 * <p>
 * 根据 application.yml 中的 knowledge.llm.provider 和 knowledge.embedding.provider
 * 动态选择对应的 ChatModel / EmbeddingModel Bean 作为 @Primary。
 * <p>
 * 切换 Provider 只需修改配置文件，无需改动代码：
 * <pre>
 * knowledge:
 *   llm:
 *     provider: openai    # openai / zhipuai / ollama
 *   embedding:
 *     provider: openai    # openai / zhipuai / ollama
 * </pre>
 * <p>
 * 支持的 Provider 及对应 Bean 名称：
 * <ul>
 *   <li>openai  → openAiChatModel / openAiEmbeddingModel</li>
 *   <li>zhipuai → zhiPuAiChatModel / zhiPuAiEmbeddingModel</li>
 *   <li>ollama  → ollamaChatModel / ollamaEmbeddingModel</li>
 * </ul>
 */
@Slf4j
@Configuration
public class LlmProviderConfig {

    private static final String CHAT_MODEL_BEAN_FORMAT = "%sChatModel";
    private static final String EMBEDDING_MODEL_BEAN_FORMAT = "%sEmbeddingModel";

    /**
     * Provider 名称到 Bean 前缀的映射
     * <p>
     * yml 中的 provider 值 → Spring AI 自动配置的 Bean 名称前缀
     */
    private static String resolveBeanPrefix(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "openAi";
            case "deepseek" -> "openAi";  // DeepSeek 兼容 OpenAI API，复用 openAi Bean
            case "zhipuai", "zhipu", "glm" -> "zhiPuAi";
            case "ollama" -> "ollama";
            default -> throw new IllegalArgumentException(
                    "不支持的 LLM Provider: " + provider + "，可选值: openai / deepseek / zhipuai / ollama");
        };
    }

    /**
     * 根据 knowledge.llm.provider 配置选择 @Primary ChatModel
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(
            ApplicationContext context,
            @Value("${knowledge.llm.provider:openai}") String provider) {

        String beanPrefix = resolveBeanPrefix(provider);
        String beanName = String.format(CHAT_MODEL_BEAN_FORMAT, beanPrefix);
        ChatModel chatModel = context.getBean(beanName, ChatModel.class);

        log.info("✅ LLM Provider 配置: provider={}, bean={}, class={}",
                provider, beanName, chatModel.getClass().getSimpleName());

        return chatModel;
    }

    /**
     * 根据 knowledge.embedding.provider 配置选择 @Primary EmbeddingModel
     */
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(
            ApplicationContext context,
            @Value("${knowledge.embedding.provider:openai}") String provider) {

        String beanPrefix = resolveBeanPrefix(provider);
        String beanName = String.format(EMBEDDING_MODEL_BEAN_FORMAT, beanPrefix);
        EmbeddingModel embeddingModel = context.getBean(beanName, EmbeddingModel.class);

        log.info("✅ Embedding Provider 配置: provider={}, bean={}, class={}",
                provider, beanName, embeddingModel.getClass().getSimpleName());

        return embeddingModel;
    }
}
