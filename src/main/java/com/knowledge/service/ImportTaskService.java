package com.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.enums.ImportStatus;
import com.knowledge.mapper.ImportTaskMapper;
import com.knowledge.model.entity.ImportTask;
import com.knowledge.model.vo.ImportTaskProgressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 导入任务管理服务
 * <p>
 * 管理异步导入任务的生命周期：创建 → 解析 → 分块 → 向量化 → 完成/失败
 * 前端通过轮询 taskId 获取进度
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportTaskService {

    private final ImportTaskMapper importTaskMapper;

    /**
     * 创建导入任务
     *
     * @param baseId     知识库ID
     * @param fileName   文件名
     * @param fileFormat 文件格式
     * @return 任务ID
     */
    public String createTask(Long baseId, String fileName, String fileFormat) {
        ImportTask task = new ImportTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setBaseId(baseId);
        task.setFileName(fileName);
        task.setFileFormat(fileFormat);
        task.setStatus(ImportStatus.PENDING.getCode());
        task.setProgress(0);
        task.setCurrentStage("CREATED");
        task.setTotalChunks(0);
        task.setProcessedChunks(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        importTaskMapper.insert(task);
        log.info("Created import task: taskId={}, baseId={}, fileName={}", task.getTaskId(), baseId, fileName);
        return task.getTaskId();
    }

    /**
     * 更新任务状态
     */
    public void updateStatus(String taskId, String status, String currentStage) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) {
            log.warn("Import task not found: {}", taskId);
            return;
        }
        task.setStatus(status);
        task.setCurrentStage(currentStage);
        task.setUpdatedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
        log.info("Updated task {}: status={}, stage={}", taskId, status, currentStage);
    }

    /**
     * 更新任务进度
     */
    public void updateProgress(String taskId, int progress, int processedChunks, int totalChunks) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) {
            log.warn("Import task not found: {}", taskId);
            return;
        }
        task.setProgress(progress);
        task.setProcessedChunks(processedChunks);
        task.setTotalChunks(totalChunks);
        task.setUpdatedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
    }

    /**
     * 标记任务为处理中（PARSING 阶段）
     */
    public void markParsing(String taskId) {
        updateStatus(taskId, ImportStatus.PROCESSING.getCode(), "PARSING");
        updateProgress(taskId, 10, 0, 0);
    }

    /**
     * 标记任务为分块阶段
     */
    public void markChunking(String taskId, int totalChunks) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) return;
        task.setStatus(ImportStatus.PROCESSING.getCode());
        task.setCurrentStage("CHUNKING");
        task.setProgress(30);
        task.setTotalChunks(totalChunks);
        task.setUpdatedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
    }

    /**
     * 标记任务为向量化阶段
     */
    public void markEmbedding(String taskId, int processedChunks, int totalChunks) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) return;
        int progress = 30 + (int) (60.0 * processedChunks / Math.max(totalChunks, 1));
        task.setStatus(ImportStatus.PROCESSING.getCode());
        task.setCurrentStage("EMBEDDING");
        task.setProgress(Math.min(progress, 95));
        task.setProcessedChunks(processedChunks);
        task.setTotalChunks(totalChunks);
        task.setUpdatedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
    }

    /**
     * 标记任务完成
     */
    public void markCompleted(String taskId) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) return;
        task.setStatus(ImportStatus.COMPLETED.getCode());
        task.setCurrentStage("DONE");
        task.setProgress(100);
        task.setProcessedChunks(task.getTotalChunks());
        task.setUpdatedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
        log.info("Import task completed: {}", taskId);
    }

    /**
     * 标记任务失败
     */
    public void markFailed(String taskId, String errorMessage) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) return;
        task.setStatus(ImportStatus.FAILED.getCode());
        task.setCurrentStage("FAILED");
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);
        log.error("Import task failed: {}, error: {}", taskId, errorMessage);
    }

    /**
     * 获取任务进度信息
     */
    public ImportTaskProgressVO getProgress(String taskId) {
        ImportTask task = getByTaskId(taskId);
        if (task == null) {
            return null;
        }

        ImportTaskProgressVO vo = new ImportTaskProgressVO();
        vo.setTaskId(task.getTaskId());
        vo.setStatus(task.getStatus());
        vo.setCurrentStage(task.getCurrentStage());
        vo.setProgress(task.getProgress());
        vo.setProcessedChunks(task.getProcessedChunks());
        vo.setTotalChunks(task.getTotalChunks());
        vo.setErrorMessage(task.getErrorMessage());
        return vo;
    }

    /**
     * 根据 taskId 查询任务
     */
    public ImportTask getByTaskId(String taskId) {
        LambdaQueryWrapper<ImportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportTask::getTaskId, taskId);
        return importTaskMapper.selectOne(wrapper);
    }

    /**
     * 查询知识库下最近的任务
     */
    public ImportTask getLatestTaskByBaseId(Long baseId) {
        LambdaQueryWrapper<ImportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportTask::getBaseId, baseId)
                .orderByDesc(ImportTask::getCreatedAt)
                .last("LIMIT 1");
        return importTaskMapper.selectOne(wrapper);
    }
}
