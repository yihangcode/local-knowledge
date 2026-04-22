package com.knowledge.model.vo;

import lombok.Data;

/**
 * 导入任务进度 VO
 */
@Data
public class ImportTaskProgressVO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态：PENDING / PROCESSING / COMPLETED / FAILED
     */
    private String status;

    /**
     * 当前阶段：UPLOAD / PARSE / CHUNK / EMBEDDING / STORE / DONE
     */
    private String currentStage;

    /**
     * 总进度百分比（0-100）
     */
    private Integer progress;

    /**
     * 已处理分块数
     */
    private Integer processedChunks;

    /**
     * 总分块数
     */
    private Integer totalChunks;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
}
