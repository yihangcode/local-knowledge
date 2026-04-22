package com.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.enums.ImportStatus;
import com.knowledge.mapper.ImportTaskMapper;
import com.knowledge.model.entity.ImportTask;
import com.knowledge.model.vo.ImportTaskProgressVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ImportTaskService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ImportTaskServiceTest {

    @Mock
    private ImportTaskMapper importTaskMapper;

    @Captor
    private ArgumentCaptor<ImportTask> taskCaptor;

    private ImportTaskService importTaskService;

    @BeforeEach
    void setUp() {
        importTaskService = new ImportTaskService(importTaskMapper);
    }

    // ====================== createTask ======================

    @Test
    void createTask_success() {
        when(importTaskMapper.insert(any(ImportTask.class))).thenReturn(1);

        String taskId = importTaskService.createTask(1L, "test.pdf", "PDF");

        assertNotNull(taskId);
        verify(importTaskMapper).insert(taskCaptor.capture());

        ImportTask captured = taskCaptor.getValue();
        assertEquals(1L, captured.getBaseId());
        assertEquals("test.pdf", captured.getFileName());
        assertEquals("PDF", captured.getFileFormat());
        assertEquals(ImportStatus.PENDING.getCode(), captured.getStatus());
        assertEquals("CREATED", captured.getCurrentStage());
        assertEquals(0, captured.getProgress());
    }

    // ====================== markParsing ======================

    @Test
    void markParsing_success() {
        ImportTask task = createMockTask("task-1");
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(importTaskMapper.updateById(any(ImportTask.class))).thenReturn(1);

        importTaskService.markParsing("task-1");

        // markParsing 调了 updateStatus + updateProgress，所以 updateById 被调用 2 次
        verify(importTaskMapper, times(2)).updateById(taskCaptor.capture());

        var allUpdates = taskCaptor.getAllValues();
        // 第一次 updateStatus
        assertEquals(ImportStatus.PROCESSING.getCode(), allUpdates.get(0).getStatus());
        assertEquals("PARSING", allUpdates.get(0).getCurrentStage());
        // 第二次 updateProgress
        assertEquals(10, allUpdates.get(1).getProgress());
    }

    @Test
    void markParsing_taskNotFound() {
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 不应抛异常，静默处理
        assertDoesNotThrow(() -> importTaskService.markParsing("not-exist"));
    }

    // ====================== markChunking ======================

    @Test
    void markChunking_success() {
        ImportTask task = createMockTask("task-1");
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(importTaskMapper.updateById(any(ImportTask.class))).thenReturn(1);

        importTaskService.markChunking("task-1", 10);

        verify(importTaskMapper).updateById(taskCaptor.capture());
        ImportTask updated = taskCaptor.getValue();
        assertEquals("CHUNKING", updated.getCurrentStage());
        assertEquals(30, updated.getProgress());
        assertEquals(10, updated.getTotalChunks());
    }

    // ====================== markEmbedding ======================

    @Test
    void markEmbedding_halfProgress() {
        ImportTask task = createMockTask("task-1");
        task.setTotalChunks(10);
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(importTaskMapper.updateById(any(ImportTask.class))).thenReturn(1);

        importTaskService.markEmbedding("task-1", 5, 10);

        verify(importTaskMapper).updateById(taskCaptor.capture());
        ImportTask updated = taskCaptor.getValue();
        assertEquals("EMBEDDING", updated.getCurrentStage());
        assertEquals(5, updated.getProcessedChunks());
        assertEquals(10, updated.getTotalChunks());
        assertTrue(updated.getProgress() >= 30 && updated.getProgress() <= 95);
    }

    @Test
    void markEmbedding_zeroTotal() {
        ImportTask task = createMockTask("task-1");
        task.setTotalChunks(0);
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(importTaskMapper.updateById(any(ImportTask.class))).thenReturn(1);

        // 不应除零
        assertDoesNotThrow(() -> importTaskService.markEmbedding("task-1", 0, 0));
    }

    // ====================== markCompleted ======================

    @Test
    void markCompleted_success() {
        ImportTask task = createMockTask("task-1");
        task.setTotalChunks(10);
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(importTaskMapper.updateById(any(ImportTask.class))).thenReturn(1);

        importTaskService.markCompleted("task-1");

        verify(importTaskMapper).updateById(taskCaptor.capture());
        ImportTask updated = taskCaptor.getValue();
        assertEquals(ImportStatus.COMPLETED.getCode(), updated.getStatus());
        assertEquals("DONE", updated.getCurrentStage());
        assertEquals(100, updated.getProgress());
    }

    // ====================== markFailed ======================

    @Test
    void markFailed_success() {
        ImportTask task = createMockTask("task-1");
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(importTaskMapper.updateById(any(ImportTask.class))).thenReturn(1);

        importTaskService.markFailed("task-1", "连接超时");

        verify(importTaskMapper).updateById(taskCaptor.capture());
        ImportTask updated = taskCaptor.getValue();
        assertEquals(ImportStatus.FAILED.getCode(), updated.getStatus());
        assertEquals("FAILED", updated.getCurrentStage());
        assertEquals("连接超时", updated.getErrorMessage());
    }

    // ====================== getProgress ======================

    @Test
    void getProgress_taskExists() {
        ImportTask task = createMockTask("task-1");
        task.setStatus(ImportStatus.PROCESSING.getCode());
        task.setCurrentStage("EMBEDDING");
        task.setProgress(50);
        task.setProcessedChunks(5);
        task.setTotalChunks(10);
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        ImportTaskProgressVO vo = importTaskService.getProgress("task-1");

        assertNotNull(vo);
        assertEquals("task-1", vo.getTaskId());
        assertEquals(ImportStatus.PROCESSING.getCode(), vo.getStatus());
        assertEquals("EMBEDDING", vo.getCurrentStage());
        assertEquals(50, vo.getProgress());
    }

    @Test
    void getProgress_taskNotFound() {
        when(importTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ImportTaskProgressVO vo = importTaskService.getProgress("not-exist");

        assertNull(vo);
    }

    // ====================== 辅助方法 ======================

    private ImportTask createMockTask(String taskId) {
        ImportTask task = new ImportTask();
        task.setTaskId(taskId);
        task.setBaseId(1L);
        task.setFileName("test.pdf");
        task.setFileFormat("PDF");
        task.setStatus(ImportStatus.PENDING.getCode());
        task.setProgress(0);
        task.setCurrentStage("CREATED");
        task.setTotalChunks(0);
        task.setProcessedChunks(0);
        return task;
    }
}
