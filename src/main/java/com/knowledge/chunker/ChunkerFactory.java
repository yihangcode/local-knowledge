package com.knowledge.chunker;

import com.knowledge.enums.FileFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块器工厂
 * <p>
 * 根据文件格式选择合适的分块策略
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkerFactory {

    private final FixedSizeChunker fixedSizeChunker;
    private final QaPairChunker qaPairChunker;

    /**
     * 根据格式选择分块器并执行分块
     */
    public List<String> chunk(List<String> paragraphs, String format, TextChunker.ChunkOptions options) {
        TextChunker chunker = selectChunker(format);

        List<String> allChunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            List<String> chunks = chunker.chunk(paragraph, options);
            allChunks.addAll(chunks);
        }

        log.info("Chunked {} paragraphs into {} chunks (format={})", paragraphs.size(), allChunks.size(), format);
        return allChunks;
    }

    /**
     * 选择分块器
     */
    private TextChunker selectChunker(String format) {
        if (FileFormat.JSON.name().equalsIgnoreCase(format)) {
            return qaPairChunker;
        }
        return fixedSizeChunker;
    }
}
