package com.knowledge.repository;

import com.knowledge.model.document.ConversationMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 对话消息 MongoDB Repository
 */
public interface ConversationRepository extends MongoRepository<ConversationMessage, String> {

    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ConversationMessage> findByBaseIdOrderByCreatedAtDesc(String baseId);

    void deleteBySessionId(String sessionId);
}
