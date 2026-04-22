package com.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.model.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识条目 Mapper
 */
@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {
}
