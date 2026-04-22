package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.model.vo.SearchResultVO;
import com.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识检索 Controller
 */
@RestController
@RequestMapping("/api/retrieval")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    /**
     * 混合检索
     *
     * @param query  查询文本
     * @param baseId 知识库ID（可选）
     * @param topK   返回数量（默认5）
     * @return 检索结果
     */
    @GetMapping("/search")
    public Result<List<SearchResultVO>> search(
            @RequestParam String query,
            @RequestParam(required = false) String baseId,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.success(retrievalService.hybridSearch(query, baseId, topK));
    }
}
