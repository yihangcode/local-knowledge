package com.knowledge.model.document;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB 知识分块文档
 */
@Data
@Document(collection = "knowledge_chunk")
public class KnowledgeChunk {

    @Id
    private String id;

    /** 关联知识条目ID */
    private String itemId;

    /** 关联知识库ID */
    private String baseId;

    /** 分块文本内容 */
    private String content;

    /** Embedding 向量 */
    private float[] embedding;

    /** 分块序号 */
    private Integer chunkIndex;

    /** 扩展元数据（来源页码、Q&A标签等） */
    private Map<String, Object> metadata;

    @CreatedDate
    private LocalDateTime createdAt;
}
