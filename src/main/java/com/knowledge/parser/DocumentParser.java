package com.knowledge.parser;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器接口
 * <p>
 * Strategy 模式：不同格式文档对应不同的解析实现
 * </p>
 */
public interface DocumentParser {

    /**
     * 支持的文件格式
     */
    String supportedFormat();

    /**
     * 解析文档，返回提取的文本内容列表
     *
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @return 解析出的文本段落列表
     */
    List<String> parse(InputStream inputStream, String fileName);
}
