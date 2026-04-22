package com.knowledge.service;

import com.knowledge.common.BusinessException;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.model.document.KnowledgeChunk;
import com.knowledge.model.vo.SearchResultVO;
import com.knowledge.repository.KnowledgeChunkRepository;
import com.knowledge.vectorstore.MongoVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * <p>
 * 实现关键词检索 + 语义相似度检索的混合搜索策略，
 * 通过加权分数融合两种检索结果。
 * 当最高置信度低于 fallback 阈值时，返回低置信度标记供上层判断是否 fallback 到 LLM。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final MongoVectorStore mongoVectorStore;
    private final EmbeddingService embeddingService;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeProperties knowledgeProperties;

    /**
     * 混合检索：关键词 + 语义相似度
     *
     * @param query  查询文本
     * @param baseId 知识库ID（可选，null 则搜索全部）
     * @param topK   返回结果数
     * @return 检索结果列表（按综合得分降序排列）
     */
    public List<SearchResultVO> hybridSearch(String query, String baseId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        double keywordWeight = knowledgeProperties.getRetrieval().getKeywordWeight();
        double semanticWeight = knowledgeProperties.getRetrieval().getSemanticWeight();

        // 1. 关键词检索
        List<ScoredResult> keywordResults = keywordSearch(query, baseId, topK * 2);
        log.debug("Keyword search returned {} results for query: {}", keywordResults.size(),
                query.substring(0, Math.min(query.length(), 50)));

        // 2. 语义相似度检索
        List<ScoredResult> semanticResults = semanticSearch(query, baseId, topK * 2);
        log.debug("Semantic search returned {} results for query: {}", semanticResults.size(),
                query.substring(0, Math.min(query.length(), 50)));

        // 3. 加权融合
        Map<String, ScoredResult> mergedMap = new LinkedHashMap<>();

        for (ScoredResult kr : keywordResults) {
            ScoredResult merged = mergedMap.computeIfAbsent(kr.chunkId, k -> kr.copy());
            merged.keywordScore = kr.keywordScore;
        }

        for (ScoredResult sr : semanticResults) {
            ScoredResult merged = mergedMap.computeIfAbsent(sr.chunkId, k -> sr.copy());
            merged.semanticScore = sr.semanticScore;
        }

        // 4. 计算综合得分并排序
        List<ScoredResult> fusedResults = new ArrayList<>(mergedMap.values());
        for (ScoredResult r : fusedResults) {
            r.finalScore = keywordWeight * r.keywordScore + semanticWeight * r.semanticScore;
        }

        fusedResults.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        // 5. 截取 topK 并转换为 VO
        return fusedResults.stream()
                .limit(topK)
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 检索并判断是否需要 fallback
     *
     * @param query  查询文本
     * @param baseId 知识库ID
     * @return 检索结果，附带最高置信度信息
     */
    public RetrievalResult retrieveWithFallbackCheck(String query, String baseId) {
        int topK = knowledgeProperties.getRetrieval().getTopK();
        double fallbackThreshold = knowledgeProperties.getRetrieval().getFallbackThreshold();

        List<SearchResultVO> results = hybridSearch(query, baseId, topK);

        double maxScore = results.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .max()
                .orElse(0.0);

        boolean needFallback = maxScore < fallbackThreshold;

        if (needFallback) {
            log.info("Low confidence ({}) below threshold ({}), suggesting LLM fallback for query: {}",
                    String.format("%.3f", maxScore), fallbackThreshold,
                    query.substring(0, Math.min(query.length(), 50)));
        }

        return new RetrievalResult(results, maxScore, needFallback);
    }

    /**
     * 关键词检索
     * <p>
     * 简单实现：在分块内容中查找关键词匹配，按 TF 匹配度评分
     * </p>
     */
    private List<ScoredResult> keywordSearch(String query, String baseId, int limit) {
        List<KnowledgeChunk> chunks;
        if (baseId != null && !baseId.isBlank()) {
            chunks = chunkRepository.findByBaseId(baseId);
        } else {
            chunks = chunkRepository.findAll();
        }

        // 分词（简单按空格和标点拆分）
        Set<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<ScoredResult> results = new ArrayList<>();

        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            // 计算 TF 匹配得分
            String contentLower = chunk.getContent().toLowerCase();
            int matchCount = 0;
            for (String keyword : keywords) {
                if (contentLower.contains(keyword.toLowerCase())) {
                    matchCount++;
                    // 计算词频
                    int freq = countOccurrences(contentLower, keyword.toLowerCase());
                    matchCount += freq - 1; // 额外频率加权
                }
            }

            if (matchCount > 0) {
                double score = (double) matchCount / keywords.size();
                ScoredResult sr = new ScoredResult();
                sr.chunkId = chunk.getId();
                sr.content = chunk.getContent();
                sr.baseId = chunk.getBaseId();
                sr.itemId = chunk.getItemId();
                sr.chunkIndex = chunk.getChunkIndex();
                sr.sourceFileName = chunk.getMetadata() != null
                        ? (String) chunk.getMetadata().get("fileName") : null;
                sr.keywordScore = Math.min(score, 1.0);
                results.add(sr);
            }
        }

        results.sort((a, b) -> Double.compare(b.keywordScore, a.keywordScore));
        return results.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 语义相似度检索
     * <p>
     * 使用 EmbeddingService 生成 queryEmbedding，然后通过 MongoVectorStore
     * 的 semanticSearchWithEmbedding 方法进行精确的余弦相似度搜索。
     * </p>
     */
    private List<ScoredResult> semanticSearch(String query, String baseId, int limit) {
        try {
            float[] queryEmbedding = embeddingService.embed(query);
            if (queryEmbedding.length == 0) {
                log.warn("Empty query embedding, skipping semantic search");
                return List.of();
            }

            // 直接使用 MongoVectorStore 的精确语义搜索方法
            List<MongoVectorStore.ScoredChunk> scoredChunks =
                    mongoVectorStore.semanticSearchWithEmbedding(queryEmbedding, baseId, limit, 0.0);

            return scoredChunks.stream().map(sc -> {
                ScoredResult sr = new ScoredResult();
                sr.chunkId = sc.chunk().getId();
                sr.content = sc.chunk().getContent();
                sr.semanticScore = sc.score();
                sr.baseId = sc.chunk().getBaseId();
                sr.itemId = sc.chunk().getItemId();
                sr.chunkIndex = sc.chunk().getChunkIndex();
                sr.sourceFileName = sc.chunk().getMetadata() != null
                        ? (String) sc.chunk().getMetadata().get("fileName") : null;
                return sr;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Semantic search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 提取关键词（简单分词）
     */
    private Set<String> extractKeywords(String text) {
        // 按常见分隔符拆分
        String[] tokens = text.split("[\\s,.\uff0c\u3002\uff01\uff1f\uff1b\uff1a\u3001()\\[\\]{}<>\u300a\u300b/\\\\|@#$%^&*+=~`]+");
        Set<String> keywords = new HashSet<>();
        for (String token : tokens) {
            if (token.length() >= 2) { // 忽略单字符
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * 统计子串出现次数
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * 将内部得分结果转换为 VO
     */
    private SearchResultVO convertToVO(ScoredResult sr) {
        SearchResultVO vo = new SearchResultVO();
        vo.setChunkId(sr.chunkId);
        vo.setContent(sr.content);
        vo.setScore(sr.finalScore);
        vo.setBaseId(sr.baseId);
        vo.setItemId(sr.itemId);
        vo.setSourceFileName(sr.sourceFileName);
        vo.setChunkIndex(sr.chunkIndex);
        return vo;
    }

    // ====================== 内部数据结构 ======================

    /**
     * 检索结果（含 fallback 标记）
     */
    public record RetrievalResult(
            List<SearchResultVO> results,
            double maxScore,
            boolean needFallback
    ) {}

    /**
     * 内部得分结果
     */
    private static class ScoredResult {
        String chunkId;
        String content;
        String baseId;
        String itemId;
        Integer chunkIndex;
        String sourceFileName;
        double keywordScore = 0.0;
        double semanticScore = 0.0;
        double finalScore = 0.0;

        ScoredResult copy() {
            ScoredResult c = new ScoredResult();
            c.chunkId = this.chunkId;
            c.content = this.content;
            c.baseId = this.baseId;
            c.itemId = this.itemId;
            c.chunkIndex = this.chunkIndex;
            c.sourceFileName = this.sourceFileName;
            return c;
        }
    }
}
