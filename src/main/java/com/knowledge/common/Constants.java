package com.knowledge.common;

/**
 * 系统常量
 */
public class Constants {

    /** 对话历史 Redis Key 前缀 */
    public static final String CHAT_HISTORY_PREFIX = "chat:history:";

    /** 对话历史过期时间（小时） */
    public static final int CHAT_HISTORY_TTL_HOURS = 24;

    /** 检索结果缓存 Redis Key 前缀 */
    public static final String RETRIEVAL_CACHE_PREFIX = "retrieval:cache:";

    /** 检索结果缓存过期时间（分钟） */
    public static final int RETRIEVAL_CACHE_TTL_MINUTES = 30;

    /** MongoDB 知识分块集合名 */
    public static final String CHUNK_COLLECTION = "knowledge_chunk";

    /** MongoDB 对话消息集合名 */
    public static final String CONVERSATION_COLLECTION = "conversation_message";

    /** 默认分块大小 */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认分块重叠 */
    public static final int DEFAULT_CHUNK_OVERLAP = 50;

    /** 默认 Top-K */
    public static final int DEFAULT_TOP_K = 5;

    /** 默认 Fallback 阈值 */
    public static final double DEFAULT_FALLBACK_THRESHOLD = 0.6;
}
