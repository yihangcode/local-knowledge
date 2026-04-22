package com.knowledge.service;

import com.knowledge.config.KnowledgeProperties;
import com.knowledge.enums.MessageSource;
import com.knowledge.model.document.ConversationMessage;
import com.knowledge.model.dto.ChatRequestDTO;
import com.knowledge.model.vo.ChatResponseVO;
import com.knowledge.model.vo.SearchResultVO;
import com.knowledge.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    private KnowledgeProperties knowledgeProperties;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        knowledgeProperties = new KnowledgeProperties();
        chatService = new ChatService(
                chatModel, retrievalService, conversationRepository, redisTemplate, knowledgeProperties);
    }

    // ====================== chat 非流式 ======================

    @Test
    void chat_knowledgeBaseHit() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("什么是 Spring Boot?");
        request.setBaseId("1");
        request.setSessionId("session-1");

        // 模拟检索命中
        SearchResultVO searchResult = new SearchResultVO();
        searchResult.setChunkId("chunk1");
        searchResult.setContent("Spring Boot 是一个框架");
        searchResult.setScore(0.9);
        searchResult.setSourceFileName("guide.pdf");

        RetrievalService.RetrievalResult retrievalResult =
                new RetrievalService.RetrievalResult(List.of(searchResult), 0.9, false);
        when(retrievalService.retrieveWithFallbackCheck("什么是 Spring Boot?", "1"))
                .thenReturn(retrievalResult);

        // 模拟 ChatModel 响应
        AssistantMessage assistantMessage = new AssistantMessage("Spring Boot 是一个优秀的 Java 框架");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ChatResponseVO result = chatService.chat(request);

        assertNotNull(result);
        assertEquals(MessageSource.KNOWLEDGE.name(), result.getSource());
        assertTrue(result.getKnowledgeHit());
        assertEquals("session-1", result.getSessionId());
        assertFalse(result.getReferences().isEmpty());
    }

    @Test
    void chat_fallbackToLlm() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("今天天气怎么样?");
        request.setBaseId("1");
        request.setSessionId("session-2");

        // 模拟检索未命中
        RetrievalService.RetrievalResult retrievalResult =
                new RetrievalService.RetrievalResult(List.of(), 0.2, true);
        when(retrievalService.retrieveWithFallbackCheck("今天天气怎么样?", "1"))
                .thenReturn(retrievalResult);

        // 模拟 LLM Fallback 响应
        AssistantMessage assistantMessage = new AssistantMessage("今天天气不错");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ChatResponseVO result = chatService.chat(request);

        assertNotNull(result);
        assertEquals(MessageSource.AI.name(), result.getSource());
        // fallback 到 AI 的回答不应标记为知识库命中
        assertFalse(result.getKnowledgeHit());
    }

    @Test
    void chat_forceLlm() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("随便聊聊");
        request.setSessionId("session-3");
        request.setForceLlm(true);

        // 强制 LLM 不走检索
        AssistantMessage assistantMessage = new AssistantMessage("好的，聊聊吧");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ChatResponseVO result = chatService.chat(request);

        assertNotNull(result);
        assertEquals(MessageSource.AI.name(), result.getSource());
        verify(retrievalService, never()).retrieveWithFallbackCheck(anyString(), anyString());
    }

    @Test
    void chat_newSessionCreated() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("hello");
        request.setSessionId(null); // 无 session ID

        RetrievalService.RetrievalResult retrievalResult =
                new RetrievalService.RetrievalResult(List.of(), 0.0, true);

        when(retrievalService.retrieveWithFallbackCheck("hello", null))
                .thenReturn(retrievalResult);

        AssistantMessage assistantMessage = new AssistantMessage("Hi!");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ChatResponseVO result = chatService.chat(request);

        assertNotNull(result.getSessionId()); // 自动生成 session ID
        assertFalse(result.getSessionId().isBlank());
    }

    // ====================== getChatHistory ======================

    @Test
    void getChatHistory_returnsMessages() {
        ConversationMessage msg = new ConversationMessage();
        msg.setSessionId("s1");
        msg.setRole("user");
        msg.setContent("hello");

        when(conversationRepository.findBySessionIdOrderByCreatedAtAsc("s1"))
                .thenReturn(List.of(msg));

        List<ConversationMessage> history = chatService.getChatHistory("s1");

        assertEquals(1, history.size());
        assertEquals("hello", history.get(0).getContent());
    }

    // ====================== deleteSession ======================

    @Test
    void deleteSession_success() {
        chatService.deleteSession("s1");

        verify(conversationRepository).deleteBySessionId("s1");
        verify(redisTemplate).delete("chat:history:s1");
    }
}
