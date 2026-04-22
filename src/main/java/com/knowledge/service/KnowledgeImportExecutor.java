package com.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowledge.chunker.ChunkerFactory;
import com.knowledge.chunker.TextChunker;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.enums.ImportStatus;
import com.knowledge.mapper.ImportRecordMapper;
import com.knowledge.mapper.KnowledgeBaseMapper;
import com.knowledge.mapper.KnowledgeItemMapper;
import com.knowledge.model.document.KnowledgeChunk;
import com.knowledge.model.entity.ImportRecord;
import com.knowledge.model.entity.KnowledgeBase;
import com.knowledge.model.entity.KnowledgeItem;
import com.knowledge.parser.ParserFactory;
import com.knowledge.repository.KnowledgeChunkRepository;
import com.knowledge.vectorstore.MongoVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识导入异步执行器
 * <p>
 * 独立 Bean，确保 @Async 代理生效（同类内部调用 @Async 不会走代理）。
 * Controller 上传时先落临时文件/字节数组，再投递到此执行器，
 * 避免 InputStream 在请求结束后失效的问题。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeImportExecutor {

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

    /**
     * 异步执行知识导入流程
     *
     * @param taskId     任务ID
     * @param baseId     知识库ID
     * @param itemId     知识条目ID
     * @param fileName   文件名
     * @param fileBytes  文件字节数组（已提前读取，避免 InputStream 失效）
     * @param fileFormat 文件格式
     */
    @Async("importExecutor")
    public void asyncImport(String taskId, Long baseId, Long itemId,
                            String fileName, byte[] fileBytes, String fileFormat) {
        long startTime = System.currentTimeMillis();
        ImportRecord record = new ImportRecord();
        record.setItemId(itemId);
        record.setBaseId(baseId);
        record.setFileName(fileName);
        record.setFileFormat(fileFormat);
        record.setStatus(ImportStatus.PROCESSING.getCode());

        try {
            // Phase 1: 解析文档（从字节数组创建 InputStream）
            importTaskService.markParsing(taskId);
            List<String> paragraphs = parserFactory.parse(
                    new java.io.ByteArrayInputStream(fileBytes), fileName);
            log.info("Parsed {} paragraphs from {}", paragraphs.size(), fileName);

            // Phase 2: 分块
            TextChunker.ChunkOptions options = TextChunker.ChunkOptions.builder()
                    .chunkSize(knowledgeProperties.getChunk().getSize())
                    .overlap(knowledgeProperties.getChunk().getOverlap())
                    .build();

            List<String> chunks = chunkerFactory.chunk(paragraphs, fileFormat, options);
            importTaskService.markChunking(taskId, chunks.size());
            log.info("Chunked into {} pieces for {}", chunks.size(), fileName);

            // Phase 3: 向量化并存储
            List<Document> documents = new ArrayList<>();
            List<float[]> embeddings = embeddingService.embedBatch(chunks);

            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("baseId", String.valueOf(baseId));
                metadata.put("itemId", String.valueOf(itemId));
                metadata.put("chunkIndex", i);
                metadata.put("fileName", fileName);
                metadata.put("fileFormat", fileFormat);
                metadata.put("_embedding", embeddings.get(i));

                Document doc = new Document(chunks.get(i), metadata);
                documents.add(doc);

                importTaskService.markEmbedding(taskId, i + 1, chunks.size());
            }

            mongoVectorStore.add(documents);

            // Phase 4: 更新统计信息
            KnowledgeItem item = knowledgeItemMapper.selectById(itemId);
            item.setChunkCount(chunks.size());
            item.setStatus(ImportStatus.COMPLETED.getCode());
            item.setUpdatedAt(LocalDateTime.now());
            knowledgeItemMapper.updateById(item);

            knowledgeBaseMapper.update(null,
                    new LambdaUpdateWrapper<KnowledgeBase>()
                            .eq(KnowledgeBase::getId, baseId)
                            .setSql("file_count = file_count + 1, chunk_count = chunk_count + " + chunks.size()));

            record.setStatus(ImportStatus.COMPLETED.getCode());
            record.setChunkCount(chunks.size());
            record.setDurationMs(System.currentTimeMillis() - startTime);

            importTaskService.markCompleted(taskId);

            log.info("Import completed: taskId={}, chunks={}, duration={}ms", taskId, chunks.size(),
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Import failed: taskId={}, error={}", taskId, e.getMessage(), e);

            record.setStatus(ImportStatus.FAILED.getCode());
            record.setErrorMessage(e.getMessage());
            record.setDurationMs(System.currentTimeMillis() - startTime);

            KnowledgeItem item = knowledgeItemMapper.selectById(itemId);
            if (item != null) {
                item.setStatus(ImportStatus.FAILED.getCode());
                item.setErrorMessage(e.getMessage());
                item.setUpdatedAt(LocalDateTime.now());
                knowledgeItemMapper.updateById(item);
            }

            importTaskService.markFailed(taskId, e.getMessage());
        } finally {
            importRecordMapper.insert(record);
        }
    }
}
