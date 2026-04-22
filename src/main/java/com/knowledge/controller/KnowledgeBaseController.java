package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.model.dto.KnowledgeBaseCreateDTO;
import com.knowledge.model.vo.KnowledgeBaseVO;
import com.knowledge.model.vo.KnowledgeItemVO;
import com.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 Controller
 */
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeService knowledgeService;

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<KnowledgeBaseVO> create(@RequestBody @Valid KnowledgeBaseCreateDTO dto) {
        return Result.success(knowledgeService.createKnowledgeBase(dto));
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> get(@PathVariable Long id) {
        return Result.success(knowledgeService.getKnowledgeBase(id));
    }

    /**
     * 获取知识库列表
     */
    @GetMapping
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.success(knowledgeService.listKnowledgeBases());
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledgeBase(id);
        return Result.success(null);
    }

    /**
     * 获取知识库下的条目列表
     */
    @GetMapping("/{id}/items")
    public Result<List<KnowledgeItemVO>> listItems(@PathVariable Long id) {
        return Result.success(knowledgeService.listItems(id));
    }

    /**
     * 删除知识条目
     */
    @DeleteMapping("/items/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        knowledgeService.deleteItem(itemId);
        return Result.success(null);
    }

    /**
     * 重建知识库向量
     */
    @PostMapping("/{id}/rebuild-vectors")
    public Result<Void> rebuildVectors(@PathVariable Long id) {
        knowledgeService.rebuildVectors(id);
        return Result.success(null);
    }
}
