package com.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.common.BusinessException;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.model.dto.ChatRequestDTO;
import com.knowledge.model.dto.KnowledgeBaseCreateDTO;
import com.knowledge.model.vo.ChatResponseVO;
import com.knowledge.model.vo.KnowledgeBaseVO;
import com.knowledge.model.vo.SearchResultVO;
import com.knowledge.service.ChatService;
import com.knowledge.service.ImportTaskService;
import com.knowledge.service.KnowledgeService;
import com.knowledge.service.RetrievalService;
import com.knowledge.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller 层单元测试
 * <p>
 * 使用 MockMvc + Mockito 手动配置，不依赖 Spring 上下文
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private KnowledgeService knowledgeService;
    @Mock private ChatService chatService;
    @Mock private RetrievalService retrievalService;
    @Mock private ImportTaskService importTaskService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private KnowledgeProperties knowledgeProperties;

    @InjectMocks private KnowledgeBaseController knowledgeBaseController;
    @InjectMocks private ChatController chatController;
    @InjectMocks private RetrievalController retrievalController;

    @BeforeEach
    void setUp() {
        // 为每个 Controller 单独设置 MockMvc
    }

    private MockMvc knowledgeBaseMockMvc() {
        return MockMvcBuilders.standaloneSetup(knowledgeBaseController)
                .setControllerAdvice(new com.knowledge.config.GlobalExceptionHandler())
                .build();
    }

    private MockMvc chatMockMvc() {
        return MockMvcBuilders.standaloneSetup(chatController)
                .setControllerAdvice(new com.knowledge.config.GlobalExceptionHandler())
                .build();
    }

    private MockMvc retrievalMockMvc() {
        return MockMvcBuilders.standaloneSetup(retrievalController)
                .setControllerAdvice(new com.knowledge.config.GlobalExceptionHandler())
                .build();
    }

    // ====================== KnowledgeBaseController ======================

    @Test
    void createKnowledgeBase_success() throws Exception {
        KnowledgeBaseCreateDTO dto = new KnowledgeBaseCreateDTO();
        dto.setName("测试库");
        dto.setDescription("描述");

        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(1L);
        vo.setName("测试库");
        vo.setDescription("描述");

        when(knowledgeService.createKnowledgeBase(any())).thenReturn(vo);

        knowledgeBaseMockMvc().perform(post("/api/knowledge-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试库"));
    }

    @Test
    void getKnowledgeBase_success() throws Exception {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(1L);
        vo.setName("测试库");

        when(knowledgeService.getKnowledgeBase(1L)).thenReturn(vo);

        knowledgeBaseMockMvc().perform(get("/api/knowledge-base/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试库"));
    }

    @Test
    void getKnowledgeBase_notFound() throws Exception {
        when(knowledgeService.getKnowledgeBase(999L))
                .thenThrow(new BusinessException("知识库不存在: 999"));

        // BusinessException 由 GlobalExceptionHandler 处理，standalone MockMvc 返回 400
        knowledgeBaseMockMvc().perform(get("/api/knowledge-base/999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listKnowledgeBases_success() throws Exception {
        when(knowledgeService.listKnowledgeBases()).thenReturn(List.of());

        knowledgeBaseMockMvc().perform(get("/api/knowledge-base"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void deleteKnowledgeBase_success() throws Exception {
        knowledgeBaseMockMvc().perform(delete("/api/knowledge-base/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ====================== ChatController ======================

    @Test
    void chat_success() throws Exception {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage("你好");
        request.setSessionId("s1");

        ChatResponseVO response = new ChatResponseVO();
        response.setContent("你好！");
        response.setSessionId("s1");

        when(chatService.chat(any(ChatRequestDTO.class))).thenReturn(response);

        chatMockMvc().perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("你好！"));
    }

    @Test
    void getHistory_success() throws Exception {
        when(chatService.getChatHistory("s1")).thenReturn(List.of());

        chatMockMvc().perform(get("/api/chat/history/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ====================== RetrievalController ======================

    @Test
    void search_success() throws Exception {
        SearchResultVO result = new SearchResultVO();
        result.setChunkId("c1");
        result.setContent("测试内容");
        result.setScore(0.95);

        when(retrievalService.hybridSearch("测试", null, 5))
                .thenReturn(List.of(result));

        retrievalMockMvc().perform(get("/api/retrieval/search")
                        .param("query", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].chunkId").value("c1"))
                .andExpect(jsonPath("$.data[0].score").value(0.95));
    }

    @Test
    void search_withBaseId() throws Exception {
        when(retrievalService.hybridSearch("测试", "1", 3)).thenReturn(List.of());

        retrievalMockMvc().perform(get("/api/retrieval/search")
                        .param("query", "测试")
                        .param("baseId", "1")
                        .param("topK", "3"))
                .andExpect(status().isOk());
    }
}
