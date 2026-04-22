package com.knowledge.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块器
 * <p>
 * 按指定字符数对文本进行分块，支持重叠窗口。
 * 适用于 TXT、PDF、Word 等无结构化标记的文本。
 * 分块时会尽量在句子边界处切分，避免截断句子。
 * </p>
 */
@Slf4j
@Component
public class FixedSizeChunker implements TextChunker {

    /** 句子结束标记 */
    private static final String[] SENTENCE_DELIMITERS = {"。", "！", "？", ".", "!", "?", "\n", "；", ";"};

    @Override
    public List<String> chunk(String text, ChunkOptions options) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(options.getChunkSize(), 1); // 确保正值
        int overlap = Math.max(Math.min(options.getOverlap(), chunkSize - 1), 0); // overlap 必须 < chunkSize

        // 如果文本小于分块大小，直接返回
        if (text.length() <= chunkSize) {
            return List.of(text.trim());
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // 如果不是最后一块，尝试在句子边界处切分
            if (end < text.length()) {
                int sentenceEnd = findSentenceBoundary(text, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 移动起点：保证至少前进 1 个字符，防止死循环
            int step = end - overlap;
            start = Math.max(step, end); // 如果 step < end（overlap >= chunkSize），则直接用 end
        }

        log.debug("FixedSizeChunker: text length={}, chunks={}", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 在指定范围内查找最近的句子边界
     *
     * @param text  完整文本
     * @param start 搜索起点
     * @param end   搜索终点
     * @return 句子边界位置（包含分隔符），如果没有找到则返回 end
     */
    private int findSentenceBoundary(String text, int start, int end) {
        // 在 [start + chunkSize/2, end] 范围内从后向前查找句子边界
        int searchStart = start + (end - start) / 2;
        int bestPos = -1;

        for (int i = end; i >= searchStart; i--) {
            char c = text.charAt(i);
            for (String delimiter : SENTENCE_DELIMITERS) {
                if (delimiter.length() == 1 && c == delimiter.charAt(0)) {
                    bestPos = i + 1; // 包含分隔符
                    return bestPos;
                }
            }
        }

        // 没有找到句子边界，尝试空格
        for (int i = end; i >= searchStart; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }

        return end;
    }
}
