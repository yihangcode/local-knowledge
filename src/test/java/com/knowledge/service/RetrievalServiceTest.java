package com.knowledge.service;

import com.knowledge.config.KnowledgeProperties;
import com.knowledge.model.document.KnowledgeChunk;
import com.knowledge.model.vo.SearchResultVO;
import com.knowledge.repository.KnowledgeChunkRepository;
import com.knowledge.vectorstore.MongoVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RetrievalService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private MongoVectorStore mongoVectorStore;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    private KnowledgeProperties knowledgeProperties;
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        knowledgeProperties = new KnowledgeProperties();
        knowledgeProperties.getRetrieval().setFallbackThreshold(0.6);
        knowledgeProperties.getRetrieval().setTopK(5);
        knowledgeProperties.getRetrieval().setKeywordWeight(0.3);
        knowledgeProperties.getRetrieval().setSemanticWeight(0.7);

        retrievalService = new RetrievalService(
                mongoVectorStore, embeddingService, chunkRepository, knowledgeProperties);
    }

    // ====================== hybridSearch ======================

    @Test
    void hybridSearch_nullQuery() {
        List<SearchResultVO> result = retrievalService.hybridSearch(null, "1", 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void hybridSearch_blankQuery() {
        List<SearchResultVO> result = retrievalService.hybridSearch("   ", "1", 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void hybridSearch_keywordMatchOnly() {
        // 准备测试数据
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId("chunk1");
        chunk.setContent("Spring Boot 是一个优秀的 Java 框架");
        chunk.setBaseId("1");
        chunk.setItemId("1");
        chunk.setChunkIndex(0);

        when(chunkRepository.findByBaseId("1")).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyString())).thenReturn(new float[0]); // 语义搜索返回空

        List<SearchResultVO> result = retrievalService.hybridSearch("Spring Boot", "1", 5);

        assertFalse(result.isEmpty());
        assertTrue(result.get(0).getScore() > 0); // 有关键词匹配得分
    }

    @Test
    void hybridSearch_noMatch() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId("chunk1");
        chunk.setContent("Python 是一种编程语言");
        chunk.setBaseId("1");

        when(chunkRepository.findByBaseId("1")).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyString())).thenReturn(new float[0]);

        List<SearchResultVO> result = retrievalService.hybridSearch("天气预报", "1", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void hybridSearch_noBaseId_searchesAll() {
        when(chunkRepository.findAll()).thenReturn(List.of());
        when(embeddingService.embed(anyString())).thenReturn(new float[0]);

        retrievalService.hybridSearch("test", null, 5);

        verify(chunkRepository).findAll();
        verify(chunkRepository, never()).findByBaseId(anyString());
    }

    // ====================== retrieveWithFallbackCheck ======================

    @Test
    void retrieveWithFallbackCheck_lowScore_triggersFallback() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId("chunk1");
        chunk.setContent("完全不相关的内容");
        chunk.setBaseId("1");

        when(chunkRepository.findByBaseId("1")).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyString())).thenReturn(new float[0]);

        RetrievalService.RetrievalResult result =
                retrievalService.retrieveWithFallbackCheck("天气怎么样", "1");

        assertTrue(result.needFallback());
        assertEquals(0.0, result.maxScore());
    }

    @Test
    void retrieveWithFallbackCheck_highScore_noFallback() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId("chunk1");
        chunk.setContent("Spring Boot Java 框架开发教程");
        chunk.setBaseId("1");
        chunk.setItemId("1");
        chunk.setChunkIndex(0);

        when(chunkRepository.findByBaseId("1")).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        // 模拟语义搜索返回高分
        MongoVectorStore.ScoredChunk scoredChunk = new MongoVectorStore.ScoredChunk(chunk, 0.95);
        when(mongoVectorStore.semanticSearchWithEmbedding(any(float[].class), eq("1"), anyInt(), anyDouble()))
                .thenReturn(List.of(scoredChunk));

        RetrievalService.RetrievalResult result =
                retrievalService.retrieveWithFallbackCheck("Spring Boot", "1");

        assertFalse(result.needFallback());
        assertTrue(result.maxScore() >= 0.6);
    }

    @Test
    void retrieveWithFallbackCheck_emptyResults() {
        when(chunkRepository.findByBaseId("1")).thenReturn(List.of());
        when(embeddingService.embed(anyString())).thenReturn(new float[0]);

        RetrievalService.RetrievalResult result =
                retrievalService.retrieveWithFallbackCheck("test", "1");

        assertTrue(result.needFallback());
        assertEquals(0.0, result.maxScore());
        assertTrue(result.results().isEmpty());
    }
}
