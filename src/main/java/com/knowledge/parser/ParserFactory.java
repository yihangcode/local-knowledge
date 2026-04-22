package com.knowledge.parser;

import com.knowledge.parser.impl.*;
import com.knowledge.enums.FileFormat;
import com.knowledge.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析器工厂
 * <p>
 * 根据文件格式自动选择对应的解析器
 * </p>
 */
@Slf4j
@Component
public class ParserFactory {

    private final List<DocumentParser> parserList;
    private final Map<String, DocumentParser> parserMap = new HashMap<>();

    public ParserFactory(List<DocumentParser> parserList) {
        this.parserList = parserList;
    }

    @PostConstruct
    public void init() {
        for (DocumentParser parser : parserList) {
            parserMap.put(parser.supportedFormat().toUpperCase(), parser);
            log.info("Registered document parser: {} -> {}", parser.supportedFormat(), parser.getClass().getSimpleName());
        }
    }

    /**
     * 根据文件格式获取解析器
     */
    public DocumentParser getParser(String format) {
        DocumentParser parser = parserMap.get(format.toUpperCase());
        if (parser == null) {
            throw new BusinessException("不支持的文件格式: " + format);
        }
        return parser;
    }

    /**
     * 根据文件名自动识别格式并解析
     */
    public List<String> parse(InputStream inputStream, String fileName) {
        String format = detectFormat(fileName);
        return getParser(format).parse(inputStream, fileName);
    }

    /**
     * 根据文件扩展名识别格式
     */
    public String detectFormat(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase();
        // 特殊处理
        if ("DOC".equals(extension)) {
            return FileFormat.DOCX.name();
        }
        return extension;
    }

    /**
     * 判断格式是否支持
     */
    public boolean supports(String format) {
        return parserMap.containsKey(format.toUpperCase());
    }
}
