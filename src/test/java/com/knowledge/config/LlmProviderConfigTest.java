package com.knowledge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LlmProviderConfig 单元测试
 * <p>
 * 验证 Provider 名称到 Bean 名称的映射逻辑
 * </p>
 */
class LlmProviderConfigTest {

    private final LlmProviderConfig config = new LlmProviderConfig();

    // ====================== resolveBeanPrefix 测试 ======================

    @ParameterizedTest
    @CsvSource({
            "openai, openAi",
            "OPENAI, openAi",
            "OpenAI, openAi",
            "deepseek, openAi",
            "DEEPSEEK, openAi",
            "zhipuai, zhiPuAi",
            "zhipu, zhiPuAi",
            "glm, zhiPuAi",
            "ZhiPuAI, zhiPuAi",
            "ollama, ollama",
            "OLLAMA, ollama"
    })
    void resolveBeanPrefix_validProviders(String provider, String expectedPrefix) {
        // 通过反射调用 private static 方法
        String result = invokeResolveBeanPrefix(provider);
        assertEquals(expectedPrefix, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "azure", "anthropic", ""})
    void resolveBeanPrefix_invalidProviders(String provider) {
        assertThrows(IllegalArgumentException.class, () -> invokeResolveBeanPrefix(provider));
    }

    // ====================== primaryChatModel 测试 ======================

    @Test
    void primaryChatModel_openaiProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        ChatModel mockChatModel = mock(ChatModel.class);
        when(context.getBean("openAiChatModel", ChatModel.class)).thenReturn(mockChatModel);

        ChatModel result = config.primaryChatModel(context, "openai");

        assertNotNull(result);
        assertEquals(mockChatModel, result);
        verify(context).getBean("openAiChatModel", ChatModel.class);
    }

    @Test
    void primaryChatModel_zhipuaiProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        ChatModel mockChatModel = mock(ChatModel.class);
        when(context.getBean("zhiPuAiChatModel", ChatModel.class)).thenReturn(mockChatModel);

        ChatModel result = config.primaryChatModel(context, "zhipuai");

        assertNotNull(result);
        assertEquals(mockChatModel, result);
        verify(context).getBean("zhiPuAiChatModel", ChatModel.class);
    }

    @Test
    void primaryChatModel_deepseekProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        ChatModel mockChatModel = mock(ChatModel.class);
        when(context.getBean("openAiChatModel", ChatModel.class)).thenReturn(mockChatModel);

        ChatModel result = config.primaryChatModel(context, "deepseek");

        assertNotNull(result);
        assertEquals(mockChatModel, result);
        verify(context).getBean("openAiChatModel", ChatModel.class);
    }

    // ====================== primaryEmbeddingModel 测试 ======================

    @Test
    void primaryEmbeddingModel_openaiProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        EmbeddingModel mockEmbeddingModel = mock(EmbeddingModel.class);
        when(context.getBean("openAiEmbeddingModel", EmbeddingModel.class)).thenReturn(mockEmbeddingModel);

        EmbeddingModel result = config.primaryEmbeddingModel(context, "openai");

        assertNotNull(result);
        assertEquals(mockEmbeddingModel, result);
        verify(context).getBean("openAiEmbeddingModel", EmbeddingModel.class);
    }

    @Test
    void primaryEmbeddingModel_ollamaProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        EmbeddingModel mockEmbeddingModel = mock(EmbeddingModel.class);
        when(context.getBean("ollamaEmbeddingModel", EmbeddingModel.class)).thenReturn(mockEmbeddingModel);

        EmbeddingModel result = config.primaryEmbeddingModel(context, "ollama");

        assertNotNull(result);
        assertEquals(mockEmbeddingModel, result);
        verify(context).getBean("ollamaEmbeddingModel", EmbeddingModel.class);
    }

    @Test
    void primaryEmbeddingModel_invalidProvider() {
        ApplicationContext context = mock(ApplicationContext.class);

        assertThrows(IllegalArgumentException.class,
                () -> config.primaryEmbeddingModel(context, "invalid_provider"));
    }

    // ====================== 辅助方法 ======================

    private String invokeResolveBeanPrefix(String provider) {
        try {
            var method = LlmProviderConfig.class.getDeclaredMethod("resolveBeanPrefix", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, provider);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }
}
