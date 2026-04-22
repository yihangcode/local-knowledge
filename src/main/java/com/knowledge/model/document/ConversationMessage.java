package com.knowledge.model.document;

import com.knowledge.enums.MessageSource;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB 对话消息文档
 */
@Data
@Document(collection = "conversation_message")
public class ConversationMessage {

    @Id
    private String id;

    /** 对话会话ID */
    private String sessionId;

    /** 角色(user/assistant) */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息来源(KNOWLEDGE/AI/HYBRID) */
    private String source;

    /** 匹配的知识分块ID列表 */
    private String[] matchedChunkIds;

    /** 置信度分数 */
    private Double confidenceScore;

    /** 知识库ID */
    private String baseId;

    @CreatedDate
    private LocalDateTime createdAt;
}
