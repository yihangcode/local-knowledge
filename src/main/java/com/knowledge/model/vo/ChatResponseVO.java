package com.knowledge.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 对话响应 VO
 */
@Data
public class ChatResponseVO {

    /**
     * 回答内容
     */
    private String content;

    /**
     * 消息来源：KNOWLEDGE / AI
     */
    private String source;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 引用的知识条目
     */
    private List<SearchResultVO> references;

    /**
     * 是否命中知识库
     */
    private Boolean knowledgeHit;
}
