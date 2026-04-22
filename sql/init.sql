-- 本地知识库系统 MySQL 初始化脚本
-- Database: knowledge_base

CREATE DATABASE IF NOT EXISTS knowledge_base DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE knowledge_base;

-- 知识库元数据表
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) DEFAULT '' COMMENT '知识库描述',
    file_count INT DEFAULT 0 COMMENT '文件数量',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_name (name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库元数据';

-- 知识条目元数据表
CREATE TABLE IF NOT EXISTS kb_knowledge_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    base_id BIGINT NOT NULL COMMENT '关联知识库ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_format VARCHAR(20) NOT NULL COMMENT '文件格式(TXT/MD/PDF/DOCX/JSON/QA)',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态(PENDING/PROCESSING/COMPLETED/FAILED)',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_base_id (base_id),
    INDEX idx_status (status),
    INDEX idx_file_format (file_format)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识条目元数据';

-- 导入记录表
CREATE TABLE IF NOT EXISTS kb_import_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    item_id BIGINT COMMENT '关联知识条目ID',
    base_id BIGINT NOT NULL COMMENT '关联知识库ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_format VARCHAR(20) NOT NULL COMMENT '文件格式',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '导入状态',
    duration_ms BIGINT DEFAULT 0 COMMENT '耗时(毫秒)',
    chunk_count INT DEFAULT 0 COMMENT '生成的分块数',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_base_id (base_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入记录';

-- 异步导入任务表
CREATE TABLE IF NOT EXISTS kb_import_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务唯一标识(UUID)',
    base_id BIGINT NOT NULL COMMENT '关联知识库ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_format VARCHAR(20) NOT NULL COMMENT '文件格式',
    status VARCHAR(20) DEFAULT 'CREATED' COMMENT '任务状态(CREATED/PARSING/CHUNKING/EMBEDDING/COMPLETED/FAILED)',
    progress INT DEFAULT 0 COMMENT '进度百分比(0-100)',
    current_stage VARCHAR(50) COMMENT '当前阶段描述',
    total_chunks INT DEFAULT 0 COMMENT '总分块数',
    processed_chunks INT DEFAULT 0 COMMENT '已处理分块数',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX idx_task_id (task_id),
    INDEX idx_base_id (base_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步导入任务';

-- 系统配置表
CREATE TABLE IF NOT EXISTS kb_system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    config_group VARCHAR(50) NOT NULL COMMENT '配置分组(llm/embedding/retrieval/chunk)',
    description VARCHAR(255) DEFAULT '' COMMENT '配置描述',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX idx_config_key (config_key),
    INDEX idx_config_group (config_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- 初始化默认配置
INSERT INTO kb_system_config (config_key, config_value, config_group, description) VALUES
('llm.provider', 'openai', 'llm', '当前 LLM Provider'),
('llm.model', 'gpt-3.5-turbo', 'llm', '当前 LLM 模型'),
('llm.temperature', '0.7', 'llm', 'LLM Temperature'),
('llm.max_tokens', '2048', 'llm', 'LLM 最大 Token 数'),
('embedding.provider', 'openai', 'embedding', '当前 Embedding Provider'),
('embedding.model', 'text-embedding-ada-002', 'embedding', '当前 Embedding 模型'),
('retrieval.fallback_threshold', '0.6', 'retrieval', 'Fallback 阈值'),
('retrieval.top_k', '5', 'retrieval', 'Top-K 结果数'),
('retrieval.keyword_weight', '0.3', 'retrieval', '关键词检索权重'),
('retrieval.semantic_weight', '0.7', 'retrieval', '语义检索权重'),
('chunk.size', '500', 'chunk', '分块大小(字符数)'),
('chunk.overlap', '50', 'chunk', '分块重叠(字符数)');
