package com.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识条目 VO
 */
@Data
public class KnowledgeItemVO {

    private Long id;
    private Long baseId;
    private String fileName;
    private String fileFormat;
    private Integer chunkCount;
    private String status;
    private LocalDateTime createTime;
}
