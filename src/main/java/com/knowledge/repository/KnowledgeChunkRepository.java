package com.knowledge.repository;

import com.knowledge.model.document.KnowledgeChunk;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 知识分块 MongoDB Repository
 */
public interface KnowledgeChunkRepository extends MongoRepository<KnowledgeChunk, String> {

    List<KnowledgeChunk> findByBaseId(String baseId);

    List<KnowledgeChunk> findByItemId(String itemId);

    List<KnowledgeChunk> findByBaseIdAndItemId(String baseId, String itemId);

    long countByBaseId(String baseId);

    void deleteByItemId(String itemId);

    void deleteByBaseId(String baseId);
}
