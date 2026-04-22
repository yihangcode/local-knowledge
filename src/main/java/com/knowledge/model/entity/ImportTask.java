package com.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步导入任务实体
 */
@Data
@TableName("kb_import_task")
public class ImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务唯一标识(UUID) */
    private String taskId;

    /** 关联知识库ID */
    private Long baseId;

    /** 文件名 */
    private String fileName;

    /** 文件格式 */
    private String fileFormat;

    /** 任务状态(CREATED/PARSING/CHUNKING/EMBEDDING/COMPLETED/FAILED) */
    private String status;

    /** 进度百分比(0-100) */
    private Integer progress;

    /** 当前阶段描述 */
    private String currentStage;

    /** 总分块数 */
    private Integer totalChunks;

    /** 已处理分块数 */
    private Integer processedChunks;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
