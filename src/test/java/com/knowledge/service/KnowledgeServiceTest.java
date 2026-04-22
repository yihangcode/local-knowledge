package com.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.chunker.ChunkerFactory;
import com.knowledge.common.BusinessException;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.mapper.ImportRecordMapper;
import com.knowledge.mapper.KnowledgeBaseMapper;
import com.knowledge.mapper.KnowledgeItemMapper;
import com.knowledge.model.dto.KnowledgeBaseCreateDTO;
import com.knowledge.model.entity.KnowledgeBase;
import com.knowledge.model.entity.KnowledgeItem;
import com.knowledge.model.vo.KnowledgeBaseVO;
import com.knowledge.model.vo.KnowledgeItemVO;
import com.knowledge.parser.ParserFactory;
import com.knowledge.repository.KnowledgeChunkRepository;
import com.knowledge.vectorstore.MongoVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

/**
 * KnowledgeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private KnowledgeItemMapper knowledgeItemMapper;
    @Mock private ImportRecordMapper importRecordMapper;
    @Mock private KnowledgeChunkRepository chunkRepository;
    @Mock private MongoVectorStore mongoVectorStore;
    @Mock private ParserFactory parserFactory;
    @Mock private ChunkerFactory chunkerFactory;
    @Mock private EmbeddingService embeddingService;
    @Mock private ImportTaskService importTaskService;

    private KnowledgeProperties knowledgeProperties;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeProperties = new KnowledgeProperties();
        knowledgeService = new KnowledgeService(
                knowledgeBaseMapper, knowledgeItemMapper, importRecordMapper,
                chunkRepository, mongoVectorStore, parserFactory, chunkerFactory,
                embeddingService, importTaskService, knowledgeProperties);
    }

    // ====================== createKnowledgeBase ======================

    @Test
    void createKnowledgeBase_success() {
        KnowledgeBaseCreateDTO dto = new KnowledgeBaseCreateDTO();
        dto.setName("测试知识库");
        dto.setDescription("测试描述");

        when(knowledgeBaseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenReturn(1);

        KnowledgeBaseVO result = knowledgeService.createKnowledgeBase(dto);

        assertNotNull(result);
        assertEquals("测试知识库", result.getName());
        assertEquals("测试描述", result.getDescription());
        assertEquals(0, result.getItemCount());
        assertEquals(0, result.getChunkCount());
    }

    @Test
    void createKnowledgeBase_duplicateName() {
        KnowledgeBaseCreateDTO dto = new KnowledgeBaseCreateDTO();
        dto.setName("已存在的知识库");

        when(knowledgeBaseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> knowledgeService.createKnowledgeBase(dto));
    }

    // ====================== getKnowledgeBase ======================

    @Test
    void getKnowledgeBase_success() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("测试");
        kb.setCreatedAt(LocalDateTime.now());

        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

        KnowledgeBaseVO result = knowledgeService.getKnowledgeBase(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("测试", result.getName());
    }

    @Test
    void getKnowledgeBase_notFound() {
        when(knowledgeBaseMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> knowledgeService.getKnowledgeBase(999L));
    }

    // ====================== listKnowledgeBases ======================

    @Test
    void listKnowledgeBases_success() {
        KnowledgeBase kb1 = new KnowledgeBase();
        kb1.setId(1L);
        kb1.setName("KB1");
        kb1.setCreatedAt(LocalDateTime.now());

        KnowledgeBase kb2 = new KnowledgeBase();
        kb2.setId(2L);
        kb2.setName("KB2");
        kb2.setCreatedAt(LocalDateTime.now());

        when(knowledgeBaseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(kb1, kb2));

        List<KnowledgeBaseVO> result = knowledgeService.listKnowledgeBases();

        assertEquals(2, result.size());
    }

    // ====================== deleteKnowledgeBase ======================

    @Test
    void deleteKnowledgeBase_success() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("测试");

        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(knowledgeItemMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(importRecordMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(knowledgeBaseMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> knowledgeService.deleteKnowledgeBase(1L));

        verify(mongoVectorStore).deleteByBaseId("1");
        verify(knowledgeBaseMapper).deleteById(1L);
    }

    @Test
    void deleteKnowledgeBase_notFound() {
        when(knowledgeBaseMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> knowledgeService.deleteKnowledgeBase(999L));
    }

    // ====================== listItems ======================

    @Test
    void listItems_success() {
        KnowledgeItem item = new KnowledgeItem();
        item.setId(1L);
        item.setBaseId(1L);
        item.setFileName("test.pdf");
        item.setFileFormat("PDF");
        item.setChunkCount(5);
        item.setStatus("COMPLETED");
        item.setCreatedAt(LocalDateTime.now());

        when(knowledgeItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));

        List<KnowledgeItemVO> result = knowledgeService.listItems(1L);

        assertEquals(1, result.size());
        assertEquals("test.pdf", result.get(0).getFileName());
    }

    // ====================== deleteItem ======================

    @Test
    void deleteItem_success() {
        KnowledgeItem item = new KnowledgeItem();
        item.setId(1L);
        item.setBaseId(1L);
        item.setChunkCount(5);

        when(knowledgeItemMapper.selectById(1L)).thenReturn(item);
        when(knowledgeBaseMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(knowledgeItemMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> knowledgeService.deleteItem(1L));
    }

    @Test
    void deleteItem_notFound() {
        when(knowledgeItemMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> knowledgeService.deleteItem(999L));
    }
}
