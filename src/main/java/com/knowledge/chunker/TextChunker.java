package com.knowledge.chunker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文本分块器接口
 * <p>
 * 所有分块策略的统一抽象
 * </p>
 */
public interface TextChunker {

    /**
     * 对单段文本执行分块
     *
     * @param text    原始文本段落
     * @param options 分块选项
     * @return 分块结果列表
     */
    List<String> chunk(String text, ChunkOptions options);

    /**
     * 分块选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkOptions {
        /**
         * 分块大小（字符数）
         */
        @Builder.Default
        private int chunkSize = 500;

        /**
         * 分块重叠（字符数）
         */
        @Builder.Default
        private int overlap = 50;

        /**
         * 是否保留原始格式
         */
        @Builder.Default
        private boolean preserveFormat = false;
    }
}
