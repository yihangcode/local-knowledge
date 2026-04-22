package com.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入记录实体
 */
@Data
@TableName("kb_import_record")
public class ImportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联知识条目ID */
    private Long itemId;

    /** 关联知识库ID */
    private Long baseId;

    /** 文件名 */
    private String fileName;

    /** 文件格式 */
    private String fileFormat;

    /** 导入状态 */
    private String status;

    /** 耗时(毫秒) */
    private Long durationMs;

    /** 生成的分块数 */
    private Integer chunkCount;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
