package com.knowledge.model.vo;

import lombok.Data;

/**
 * 检索结果 VO
 */
@Data
public class SearchResultVO {

    /**
     * 分块ID
     */
    private String chunkId;

    /**
     * 匹配内容
     */
    private String content;

    /**
     * 相似度得分
     */
    private Double score;

    /**
     * 所属知识库ID
     */
    private String baseId;

    /**
     * 所属知识条目ID
     */
    private String itemId;

    /**
     * 来源文件名
     */
    private String sourceFileName;

    /**
     * 分块索引
     */
    private Integer chunkIndex;
}
