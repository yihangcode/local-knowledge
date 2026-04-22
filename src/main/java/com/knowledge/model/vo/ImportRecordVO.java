package com.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入记录 VO
 */
@Data
public class ImportRecordVO {

    private Long id;
    private Long baseId;
    private String fileName;
    private String fileFormat;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private Long duration;
    private String errorMessage;
    private LocalDateTime createTime;
}
