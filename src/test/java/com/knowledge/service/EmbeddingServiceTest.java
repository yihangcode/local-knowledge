package com.knowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EmbeddingService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingModel);
    }

    // ====================== embed 单文本 ======================

    @Test
    void embed_normalText() {
        float[] expected = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed("hello")).thenReturn(expected);

        float[] result = embeddingService.embed("hello");

        assertArrayEquals(expected, result);
        verify(embeddingModel).embed("hello");
    }

    @Test
    void embed_nullText() {
        float[] result = embeddingService.embed(null);

        assertEquals(0, result.length);
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void embed_blankText() {
        float[] result = embeddingService.embed("   ");

        assertEquals(0, result.length);
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void embed_modelThrowsException() {
        when(embeddingModel.embed("error")).thenThrow(new RuntimeException("API error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> embeddingService.embed("error"));
        assertTrue(ex.getMessage().contains("向量生成失败"));
    }

    // ====================== embedBatch 批量 ======================

    @Test
    void embedBatch_normalBatch() {
        List<String> texts = List.of("text1", "text2");
        Embedding emb1 = mock(Embedding.class);
        Embedding emb2 = mock(Embedding.class);
        when(emb1.getOutput()).thenReturn(new float[]{0.1f, 0.2f});
        when(emb2.getOutput()).thenReturn(new float[]{0.3f, 0.4f});

        EmbeddingResponse response = mock(EmbeddingResponse.class);
        when(response.getResults()).thenReturn(List.of(emb1, emb2));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        List<float[]> result = embeddingService.embedBatch(texts);

        assertEquals(2, result.size());
        assertArrayEquals(new float[]{0.1f, 0.2f}, result.get(0));
        assertArrayEquals(new float[]{0.3f, 0.4f}, result.get(1));
    }

    @Test
    void embedBatch_emptyInput() {
        List<float[]> result = embeddingService.embedBatch(List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void embedBatch_nullInput() {
        List<float[]> result = embeddingService.embedBatch(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void embedBatch_partialFailure() {
        List<String> texts = List.of("good", "bad");
        Embedding emb1 = mock(Embedding.class);
        when(emb1.getOutput()).thenReturn(new float[]{0.1f});

        EmbeddingResponse successResponse = mock(EmbeddingResponse.class);
        when(successResponse.getResults()).thenReturn(List.of(emb1));

        // 第一批成功，第二批失败
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(successResponse)
                .thenThrow(new RuntimeException("API error"));

        List<float[]> result = embeddingService.embedBatch(texts, 1);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).length); // 成功
        assertEquals(0, result.get(1).length); // 失败填充空向量
    }

    // ====================== dimensions ======================

    @Test
    void dimensions_supported() {
        when(embeddingModel.dimensions()).thenReturn(1536);

        assertEquals(1536, embeddingService.dimensions());
    }

    @Test
    void dimensions_unsupported() {
        when(embeddingModel.dimensions()).thenThrow(new UnsupportedOperationException());

        assertEquals(1536, embeddingService.dimensions()); // 默认值
    }
}
