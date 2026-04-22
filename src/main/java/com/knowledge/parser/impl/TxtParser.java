package com.knowledge.parser.impl;

import com.knowledge.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TXT 纯文本解析器
 */
@Slf4j
@Component
public class TxtParser implements DocumentParser {

    @Override
    public String supportedFormat() {
        return "TXT";
    }

    @Override
    public List<String> parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            List<String> paragraphs = new ArrayList<>();

            // 按空行分段
            StringBuilder current = new StringBuilder();
            for (String line : content.split("\n")) {
                if (line.trim().isEmpty()) {
                    if (current.length() > 0) {
                        paragraphs.add(current.toString().trim());
                        current = new StringBuilder();
                    }
                } else {
                    if (current.length() > 0) {
                        current.append("\n");
                    }
                    current.append(line);
                }
            }
            if (current.length() > 0) {
                paragraphs.add(current.toString().trim());
            }

            // 过滤空段落
            paragraphs.removeIf(String::isEmpty);

            log.info("Parsed TXT file '{}': {} paragraphs extracted", fileName, paragraphs.size());
            return paragraphs;
        } catch (Exception e) {
            log.error("Failed to parse TXT file: {}", fileName, e);
            throw new RuntimeException("TXT文件解析失败: " + e.getMessage(), e);
        }
    }
}
