package com.knowledge.vectorstore;

import com.knowledge.model.document.KnowledgeChunk;
import com.knowledge.repository.KnowledgeChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 自定义 MongoDB VectorStore 实现
 * <p>
 * 第一阶段：使用 MongoDB 数组字段存储 Embedding 向量，应用层计算余弦相似度。
 * 后续可无缝替换为 MilvusVectorStore / ChromaVectorStore 等实现。
 * <p>
 * 适配 Spring AI 1.0.0-M4：
 * - VectorStore.similaritySearch() 接受 SearchRequest 参数
 * - Document 使用 getContent() 而非 getText()
 * - 1.0.0-M4 中 Document 不持有 embedding，embedding 通过自定义方式存储到 MongoDB
 * </p>
 */
@Slf4j
@Component
public class MongoVectorStore implements VectorStore {

    private final KnowledgeChunkRepository chunkRepository;

    public MongoVectorStore(KnowledgeChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setContent(doc.getContent());
            chunk.setMetadata(doc.getMetadata());

            // 从 metadata 中提取关联信息
            Map<String, Object> meta = doc.getMetadata();
            if (meta != null) {
                if (meta.containsKey("baseId")) {
                    chunk.setBaseId(String.valueOf(meta.get("baseId")));
                }
                if (meta.containsKey("itemId")) {
                    chunk.setItemId(String.valueOf(meta.get("itemId")));
                }
                if (meta.containsKey("chunkIndex")) {
                    Object chunkIndexObj = meta.get("chunkIndex");
                    if (chunkIndexObj instanceof Integer) {
                        chunk.setChunkIndex((Integer) chunkIndexObj);
                    } else if (chunkIndexObj instanceof Number) {
                        chunk.setChunkIndex(((Number) chunkIndexObj).intValue());
                    }
                }
                // 从 metadata 中提取 embedding（由 KnowledgeService 预先设置）
                if (meta.containsKey("_embedding")) {
                    Object embObj = meta.get("_embedding");
                    if (embObj instanceof float[]) {
                        chunk.setEmbedding((float[]) embObj);
                    }
                }
            }

            chunkRepository.save(chunk);
        }
        log.info("Added {} documents to MongoVectorStore", documents.size());
    }

    @Override
    public Optional<Boolean> delete(List<String> idList) {
        chunkRepository.deleteAllById(idList);
        log.info("Deleted {} documents from MongoVectorStore", idList.size());
        return Optional.of(true);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            log.warn("Query is empty, returning empty results");
            return List.of();
        }

        // 获取过滤条件中的 baseId（通过 SearchRequest 的 filterExpression）
        String baseId = extractBaseId(request);

        // 查询所有相关分块
        List<KnowledgeChunk> chunks;
        if (baseId != null && !baseId.isBlank()) {
            chunks = chunkRepository.findByBaseId(baseId);
        } else {
            chunks = chunkRepository.findAll();
        }

        // 获取 TopK 和相似度阈值（通过 SearchRequest 配置）
        int topK = request.getTopK();
        double similarityThreshold = request.getSimilarityThreshold();

        // 计算余弦相似度并排序
        List<ScoredChunk> scoredChunks = chunks.stream()
                .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                .map(chunk -> {
                    double similarity = cosineSimilarity(query, chunk.getContent(), chunk.getEmbedding());
                    return new ScoredChunk(chunk, similarity);
                })
                .filter(sc -> sc.score >= similarityThreshold)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());

        // 转换为 Spring AI Document
        return scoredChunks.stream()
                .map(sc -> {
                    Map<String, Object> metadata = new HashMap<>();
                    if (sc.chunk.getMetadata() != null) {
                        metadata.putAll(sc.chunk.getMetadata());
                    }
                    metadata.put("baseId", sc.chunk.getBaseId());
                    metadata.put("itemId", sc.chunk.getItemId());
                    metadata.put("chunkIndex", sc.chunk.getChunkIndex());
                    metadata.put("similarity", sc.score);

                    return new Document(sc.chunk.getId(), sc.chunk.getContent(), metadata);
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算查询文本与分块的语义相似度
     * <p>
     * 注意：这里简化实现，使用查询文本与分块 embedding 的余弦相似度。
     * 实际 embedding 需要在外部通过 EmbeddingService 生成后传入。
     * 由于 Spring AI 1.0.0-M4 的 SearchRequest 不携带 queryEmbedding，
     * 此处使用简单的文本匹配分数作为 fallback，
     * 真正的语义搜索由 RetrievalService 调用。
     * </p>
     */
    private double cosineSimilarity(String query, String content, float[] embedding) {
        // 简化的相似度计算：基于文本 TF 匹配度
        // 真正的余弦相似度需要在 RetrievalService 中通过 EmbeddingService 生成 queryEmbedding 后计算
        if (content == null || content.isBlank()) {
            return 0.0;
        }
        String contentLower = content.toLowerCase();
        String queryLower = query.toLowerCase();
        // 简单的关键词匹配得分
        String[] queryWords = queryLower.split("\\s+");
        int matchCount = 0;
        for (String word : queryWords) {
            if (word.length() >= 2 && contentLower.contains(word)) {
                matchCount++;
            }
        }
        return queryWords.length > 0 ? (double) matchCount / queryWords.length : 0.0;
    }

    /**
     * 计算两个向量的余弦相似度（供外部调用）
     */
    public double computeCosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 语义搜索（使用 queryEmbedding 精确计算余弦相似度）
     * <p>
     * 此方法供 RetrievalService 使用，绕过 Spring AI 的 SearchRequest，
     * 直接传入 queryEmbedding 进行精确的向量相似度搜索。
     * </p>
     */
    public List<ScoredChunk> semanticSearchWithEmbedding(float[] queryEmbedding, String baseId, int topK, double threshold) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.warn("Query embedding is empty, returning empty results");
            return List.of();
        }

        List<KnowledgeChunk> chunks;
        if (baseId != null && !baseId.isBlank()) {
            chunks = chunkRepository.findByBaseId(baseId);
        } else {
            chunks = chunkRepository.findAll();
        }

        return chunks.stream()
                .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                .map(chunk -> {
                    double similarity = computeCosineSimilarity(queryEmbedding, chunk.getEmbedding());
                    return new ScoredChunk(chunk, similarity);
                })
                .filter(sc -> sc.score >= threshold)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 从 SearchRequest 中提取 baseId 过滤条件
     */
    private String extractBaseId(SearchRequest request) {
        // Spring AI 1.0.0-M4 的 filter 表达式处理
        // 简化处理：后续可通过 Filter.Expression 解析
        return null;
    }

    /**
     * 按知识库ID删除所有向量
     */
    public void deleteByBaseId(String baseId) {
        chunkRepository.deleteByBaseId(baseId);
        log.info("Deleted all chunks for baseId: {}", baseId);
    }

    /**
     * 按知识条目ID删除所有向量
     */
    public void deleteByItemId(String itemId) {
        chunkRepository.deleteByItemId(itemId);
        log.info("Deleted all chunks for itemId: {}", itemId);
    }

    /**
     * 得分分块内部类
     */
    public record ScoredChunk(KnowledgeChunk chunk, double score) {}
}
