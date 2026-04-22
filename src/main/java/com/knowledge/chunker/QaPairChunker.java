package com.knowledge.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 问答对分块器
 * <p>
 * 针对 JSON 问答对格式进行分块。
 * 每个 Q&A 对作为一个独立分块，保持语义完整性。
 * </p>
 */
@Slf4j
@Component
public class QaPairChunker implements TextChunker {

    /** 问答对分隔模板 */
    private static final String QA_TEMPLATE = "Q: %s\nA: %s";

    @Override
    public List<String> chunk(String text, ChunkOptions options) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();

        // 解析 JSON 问答对格式的文本
        // 输入已经是 JsonQaParser 解析后的格式，每行格式为 "Q: xxx\nA: xxx"
        // 或者以 "---" 分隔的问答对
        String[] qaPairs = text.split("(?=\nQ:)|---");

        for (String pair : qaPairs) {
            String trimmed = pair.trim();
            if (!trimmed.isEmpty()) {
                // 如果整块已经包含 Q: 和 A:，直接使用
                if (trimmed.contains("Q:") && trimmed.contains("A:")) {
                    chunks.add(trimmed);
                } else {
                    // 否则作为普通文本处理，按固定大小分块
                    if (trimmed.length() <= options.getChunkSize()) {
                        chunks.add(trimmed);
                    } else {
                        // 超长文本降级为按句子分割
                        splitLongText(trimmed, options.getChunkSize(), chunks);
                    }
                }
            }
        }

        log.debug("QaPairChunker: input pairs={}, output chunks={}", qaPairs.length, chunks.size());
        return chunks;
    }

    /**
     * 对超长问答文本按句子进行二次分割
     */
    private void splitLongText(String text, int chunkSize, List<String> chunks) {
        String[] sentences = text.split("(?<=[。！？.!?])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(sentence);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
    }
}
