package com.knowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 基础设施健康检查配置
 * <p>
 * 在应用启动完成后（ApplicationReadyEvent）执行 MySQL / Redis / MongoDB 连接检测。
 * 如果任一组件连接失败，打印明确的错误日志并终止应用（System.exit），
 * 避免应用在数据库不可用的情况下静默运行，导致运行时才发现问题。
 * <p>
 * 注意：dynamic-datasource 的 strict=true 已保证数据源配置错误时启动报错，
 * 这里额外检查运行时连接是否真正可用。
 */
@Slf4j
@Configuration
public class InfrastructureHealthConfig {

    @Bean
    public ApplicationListener<ApplicationReadyEvent> infrastructureHealthChecker(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            MongoTemplate mongoTemplate) {

        return event -> {
            log.info("🔍 开始基础设施健康检查...");

            boolean mysqlOk = checkMySQL(dataSource);
            boolean redisOk = checkRedis(redisConnectionFactory);
            boolean mongoOk = checkMongoDB(mongoTemplate);

            if (!mysqlOk || !redisOk || !mongoOk) {
                log.error("");
                log.error("╔════════════════════════════════════════════════════════╗");
                log.error("║  ❌ 基础设施健康检查失败，应用即将关闭！                 ║");
                log.error("║  请检查以下组件的连接配置：                              ║");
                log.error("║  MySQL: {}                            ║", mysqlOk ? "✓ 正常" : "✗ 失败");
                log.error("║  Redis: {}                            ║", redisOk ? "✓ 正常" : "✗ 失败");
                log.error("║  MongoDB: {}                          ║", mongoOk ? "✓ 正常" : "✗ 失败");
                log.error("╚════════════════════════════════════════════════════════╝");
                log.error("");
                System.exit(1);
            } else {
                log.info("✅ 基础设施健康检查通过：MySQL ✓ | Redis ✓ | MongoDB ✓");
            }
        };
    }

    private boolean checkMySQL(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            if (valid) {
                log.info("  ✅ MySQL 连接正常: {}", conn.getMetaData().getURL());
            } else {
                log.error("  ❌ MySQL 连接验证失败（isValid=false），请检查数据库地址/账号/密码");
            }
            return valid;
        } catch (Exception e) {
            log.error("  ❌ MySQL 连接失败: {}，请检查数据库地址/账号/密码/网络", e.getMessage());
            return false;
        }
    }

    private boolean checkRedis(RedisConnectionFactory factory) {
        try {
            var conn = factory.getConnection();
            String pong = conn.ping();
            conn.close();
            boolean healthy = "PONG".equalsIgnoreCase(pong);
            if (healthy) {
                log.info("  ✅ Redis 连接正常: PING → {}", pong);
            } else {
                log.error("  ❌ Redis PING 返回异常: {}，请检查 Redis 服务状态", pong);
            }
            return healthy;
        } catch (Exception e) {
            log.error("  ❌ Redis 连接失败: {}，请检查 Redis 地址/密码/网络", e.getMessage());
            return false;
        }
    }

    private boolean checkMongoDB(MongoTemplate mongoTemplate) {
        try {
            var result = mongoTemplate.executeCommand("{ ping: 1 }");
            double ok = result.containsKey("ok") ? ((Number) result.get("ok")).doubleValue() : 0;
            boolean healthy = ok == 1.0;
            if (healthy) {
                log.info("  ✅ MongoDB 连接正常: ping → ok={}", ok);
            } else {
                log.error("  ❌ MongoDB ping 返回异常: ok={}，请检查 MongoDB 服务状态", ok);
            }
            return healthy;
        } catch (Exception e) {
            log.error("  ❌ MongoDB 连接失败: {}，请检查 MongoDB 地址/账号/网络", e.getMessage());
            return false;
        }
    }
}
