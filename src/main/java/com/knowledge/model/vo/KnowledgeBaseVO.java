package com.knowledge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库信息 VO
 */
@Data
public class KnowledgeBaseVO {

    private Long id;
    private String name;
    private String description;
    private Integer itemCount;
    private Integer chunkCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
