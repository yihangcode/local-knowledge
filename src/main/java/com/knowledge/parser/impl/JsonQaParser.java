package com.knowledge.parser.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
 * JSON Q&A 格式解析器
 * <p>
 * 支持以下 JSON 格式：
 * <pre>
 * [
 *   {"question": "问题1", "answer": "答案1"},
 *   {"question": "问题2", "answer": "答案2"}
 * ]
 * </pre>
 * 也支持带 "q"/"a" 简写键名的格式
 * </p>
 */
@Slf4j
@Component
public class JsonQaParser implements DocumentParser {

    @Override
    public String supportedFormat() {
        return "JSON";
    }

    @Override
    public List<String> parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            List<String> qaPairs = new ArrayList<>();

            JSONArray array = JSONUtil.parseArray(content);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String question = extractField(obj, "question", "q");
                String answer = extractField(obj, "answer", "a");

                if (question != null && answer != null) {
                    qaPairs.add("Q: " + question + "\nA: " + answer);
                }
            }

            log.info("Parsed JSON Q&A file '{}': {} pairs extracted", fileName, qaPairs.size());
            return qaPairs;
        } catch (Exception e) {
            log.error("Failed to parse JSON Q&A file: {}", fileName, e);
            throw new RuntimeException("JSON Q&A文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JSON 对象中提取字段值，支持多个候选键名
     */
    private String extractField(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.containsKey(key)) {
                return obj.getStr(key);
            }
        }
        return null;
    }
}
