package com.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowledge.chunker.ChunkerFactory;
import com.knowledge.chunker.TextChunker;
import com.knowledge.common.BusinessException;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.enums.FileFormat;
import com.knowledge.enums.ImportStatus;
import com.knowledge.mapper.ImportRecordMapper;
import com.knowledge.mapper.KnowledgeBaseMapper;
import com.knowledge.mapper.KnowledgeItemMapper;
import com.knowledge.model.document.KnowledgeChunk;
import com.knowledge.model.dto.KnowledgeBaseCreateDTO;
import com.knowledge.model.dto.KnowledgeImportDTO;
import com.knowledge.model.entity.ImportRecord;
import com.knowledge.model.entity.KnowledgeBase;
import com.knowledge.model.entity.KnowledgeItem;
import com.knowledge.model.vo.KnowledgeBaseVO;
import com.knowledge.model.vo.KnowledgeItemVO;
import com.knowledge.parser.ParserFactory;
import com.knowledge.repository.KnowledgeChunkRepository;
import com.knowledge.vectorstore.MongoVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库核心服务
 * <p>
 * 管理知识库的 CRUD、知识导入（解析→分块→向量化→存储）、向量重建
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeItemMapper knowledgeItemMapper;
    private final ImportRecordMapper importRecordMapper;
    private final KnowledgeChunkRepository chunkRepository;
    private final MongoVectorStore mongoVectorStore;
    private final ParserFactory parserFactory;
    private final ChunkerFactory chunkerFactory;
    private final EmbeddingService embeddingService;
    private final ImportTaskService importTaskService;
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeImportExecutor importExecutor;

    // ====================== 知识库 CRUD ======================

    /**
     * 创建知识库
     */
    @Transactional
    public KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseCreateDTO dto) {
        // 检查名称是否重复
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getName, dto.getName());
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("知识库名称已存在: " + dto.getName());
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setFileCount(0);
        kb.setChunkCount(0);
        kb.setCreatedAt(LocalDateTime.now());
        kb.setUpdatedAt(LocalDateTime.now());

        knowledgeBaseMapper.insert(kb);
        log.info("Created knowledge base: id={}, name={}", kb.getId(), kb.getName());

        return convertToVO(kb);
    }

    /**
     * 获取知识库详情
     */
    public KnowledgeBaseVO getKnowledgeBase(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在: " + id);
        }
        return convertToVO(kb);
    }

    /**
     * 获取所有知识库列表
     */
    public List<KnowledgeBaseVO> listKnowledgeBases() {
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().orderByDesc(KnowledgeBase::getCreatedAt));
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 删除知识库（级联删除所有条目和向量）
     */
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在: " + id);
        }

        // 删除 MongoDB 向量
        mongoVectorStore.deleteByBaseId(String.valueOf(id));

        // 删除知识条目
        knowledgeItemMapper.delete(
                new LambdaQueryWrapper<KnowledgeItem>().eq(KnowledgeItem::getBaseId, id));

        // 删除导入记录
        importRecordMapper.delete(
                new LambdaQueryWrapper<ImportRecord>().eq(ImportRecord::getBaseId, id));

        // 删除知识库
        knowledgeBaseMapper.deleteById(id);

        log.info("Deleted knowledge base: id={}, name={}", id, kb.getName());
    }

    // ====================== 知识条目管理 ======================

    /**
     * 获取知识库下的条目列表
     */
    public List<KnowledgeItemVO> listItems(Long baseId) {
        List<KnowledgeItem> items = knowledgeItemMapper.selectList(
                new LambdaQueryWrapper<KnowledgeItem>()
                        .eq(KnowledgeItem::getBaseId, baseId)
                        .orderByDesc(KnowledgeItem::getCreatedAt));
        return items.stream().map(this::convertToItemVO).collect(Collectors.toList());
    }

    /**
     * 删除知识条目（级联删除向量）
     */
    @Transactional
    public void deleteItem(Long itemId) {
        KnowledgeItem item = knowledgeItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("知识条目不存在: " + itemId);
        }

        // 删除 MongoDB 向量
        mongoVectorStore.deleteByItemId(String.valueOf(itemId));

        // 更新知识库统计
        knowledgeBaseMapper.update(null,
                new LambdaUpdateWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getId, item.getBaseId())
                        .setSql("file_count = file_count - 1, chunk_count = chunk_count - " + item.getChunkCount()));

        // 删除条目
        knowledgeItemMapper.deleteById(itemId);

        log.info("Deleted knowledge item: id={}, fileName={}", itemId, item.getFileName());
    }

    // ====================== 知识导入 ======================

    /**
     * 异步导入知识文件
     *
     * @param baseId       知识库ID
     * @param fileName     文件名
     * @param inputStream  文件输入流
     * @param format       文件格式（可选，自动识别）
     * @return 任务ID
     */
    public String importKnowledge(Long baseId, String fileName, InputStream inputStream, String format) {
        // 校验知识库
        KnowledgeBase kb = knowledgeBaseMapper.selectById(baseId);
        if (kb == null) {
            throw new BusinessException("知识库不存在: " + baseId);
        }

        // 自动识别格式
        String fileFormat = (format != null && !format.isBlank())
                ? format.toUpperCase()
                : parserFactory.detectFormat(fileName);

        if (!parserFactory.supports(fileFormat)) {
            throw new BusinessException("不支持的文件格式: " + fileFormat);
        }

        // 创建导入任务
        String taskId = importTaskService.createTask(baseId, fileName, fileFormat);

        // 创建知识条目
        KnowledgeItem item = new KnowledgeItem();
        item.setBaseId(baseId);
        item.setFileName(fileName);
        item.setFileFormat(fileFormat);
        item.setFileSize(0L);
        item.setChunkCount(0);
        item.setStatus(ImportStatus.PROCESSING.getCode());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        knowledgeItemMapper.insert(item);

        // 提前读取 InputStream 为 byte[]，避免请求结束后流失效
        byte[] fileBytes;
        try {
            fileBytes = inputStream.readAllBytes();
            item.setFileSize((long) fileBytes.length);
            knowledgeItemMapper.updateById(item);
        } catch (Exception e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        // 委托给独立 Bean 执行异步导入（确保 @Async 代理生效）
        importExecutor.asyncImport(taskId, baseId, item.getId(), fileName, fileBytes, fileFormat);

        return taskId;
    }

    // ====================== 向量重建 ======================

    /**
     * 重建知识库向量
     * <p>
     * 当切换 Embedding 模型后，需要重新生成所有向量。
     * 正确流程：先读取所有分块内容 → 再删除旧向量 → 用新模型重新生成 → 写回。
     * 避免先删后读导致数据丢失。
     * </p>
     */
    @Async("importExecutor")
    public void rebuildVectors(Long baseId) {
        log.info("Starting vector rebuild for baseId={}", baseId);

        // Step 1: 先读取该知识库下所有条目的分块（在删除之前！）
        List<KnowledgeItem> items = knowledgeItemMapper.selectList(
                new LambdaQueryWrapper<KnowledgeItem>()
                        .eq(KnowledgeItem::getBaseId, baseId)
                        .eq(KnowledgeItem::getStatus, ImportStatus.COMPLETED.getCode()));

        // 预读取所有分块内容到内存
        List<List<KnowledgeChunk>> allItemChunks = new ArrayList<>();
        for (KnowledgeItem item : items) {
            List<KnowledgeChunk> chunks = chunkRepository.findByItemId(String.valueOf(item.getId()));
            allItemChunks.add(chunks);
        }

        // Step 2: 删除旧向量（此时数据已安全读到内存）
        mongoVectorStore.deleteByBaseId(String.valueOf(baseId));
        log.info("Deleted old vectors for baseId={}, now rebuilding...", baseId);

        // Step 3: 用新模型重新生成向量并写回
        int totalChunks = 0;

        for (int itemIdx = 0; itemIdx < items.size(); itemIdx++) {
            KnowledgeItem item = items.get(itemIdx);
            List<KnowledgeChunk> oldChunks = allItemChunks.get(itemIdx);

            try {
                List<String> chunkTexts = oldChunks.stream()
                        .map(KnowledgeChunk::getContent)
                        .collect(Collectors.toList());

                if (chunkTexts.isEmpty()) {
                    log.warn("No chunks found for item {}, skipping", item.getId());
                    continue;
                }

                // 重新向量化
                List<float[]> newEmbeddings = embeddingService.embedBatch(chunkTexts);

                // 更新分块的 embedding
                for (int i = 0; i < oldChunks.size(); i++) {
                    KnowledgeChunk chunk = oldChunks.get(i);
                    chunk.setEmbedding(newEmbeddings.get(i));
                    chunkRepository.save(chunk);
                }

                totalChunks += oldChunks.size();
                log.info("Rebuilt vectors for item {}: {} chunks", item.getId(), oldChunks.size());

            } catch (Exception e) {
                log.error("Failed to rebuild vectors for item {}: {}", item.getId(), e.getMessage(), e);
            }
        }

        log.info("Vector rebuild completed for baseId={}, totalChunks={}", baseId, totalChunks);
    }

    // ====================== 转换方法 ======================

    private KnowledgeBaseVO convertToVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setItemCount(kb.getFileCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setCreateTime(kb.getCreatedAt());
        vo.setUpdateTime(kb.getUpdatedAt());
        return vo;
    }

    private KnowledgeItemVO convertToItemVO(KnowledgeItem item) {
        KnowledgeItemVO vo = new KnowledgeItemVO();
        vo.setId(item.getId());
        vo.setBaseId(item.getBaseId());
        vo.setFileName(item.getFileName());
        vo.setFileFormat(item.getFileFormat());
        vo.setChunkCount(item.getChunkCount());
        vo.setStatus(item.getStatus());
        vo.setCreateTime(item.getCreatedAt());
        return vo;
    }
}
