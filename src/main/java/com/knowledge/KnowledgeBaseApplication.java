package com.knowledge;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 本地知识库系统启动类
 * <p>
 * 数据源由 dynamic-datasource-spring-boot3-starter 接管，
 * 配置路径为 spring.datasource.dynamic.datasource.master.*。
 * 通过 @Import 显式导入 DynamicDataSourceAutoConfiguration，
 * 确保它在 DataSourceAutoConfiguration 之前加载并创建 DataSource Bean。
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.knowledge.mapper")
@Import(DynamicDataSourceAutoConfiguration.class)
public class KnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBaseApplication.class, args);
        System.out.println("本地知识库系统成功！");
    }
}
