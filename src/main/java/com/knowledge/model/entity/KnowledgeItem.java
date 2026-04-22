package com.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识条目元数据实体
 */
@Data
@TableName("kb_knowledge_item")
public class KnowledgeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联知识库ID */
    private Long baseId;

    /** 原始文件名 */
    private String fileName;

    /** 文件格式(TXT/MD/PDF/DOCX/JSON/QA) */
    private String fileFormat;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态(PENDING/PROCESSING/COMPLETED/FAILED) */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
