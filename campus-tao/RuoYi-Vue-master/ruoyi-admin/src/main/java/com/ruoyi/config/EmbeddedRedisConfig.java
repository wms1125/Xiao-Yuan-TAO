package com.ruoyi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.embedded.RedisServer;

/**
 * 本地开发 / 演示用：在 Spring 容器刷新之前拉起一个真实的 Redis 实例（端口 6379）。
 * <p>
 * 背景：本机禁用 WSL/Docker、GitHub 不通，无法安装外部 Redis for Windows。
 * 采用 embedded-redis（jar 内已打包各平台 redis-server 二进制），对业务代码完全透明，
 * RedisTemplate / 若依缓存 / 限流 / Session 照常工作。
 * <p>
 * 关键：必须在 SpringApplication.run 之前调用 start()，否则若依的
 * SysConfigServiceImpl.init() 在 Bean 初始化阶段就会去连 Redis 而失败。
 * <p>
 * 关闭采用 JVM 关闭钩子；若端口已被占用（已有真实 Redis）则视为已有实例，忽略。
 */
public class EmbeddedRedisConfig
{
    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisConfig.class);
    private static final int REDIS_PORT = 6379;
    private static RedisServer redisServer;

    public static void start()
    {
        // 容器化部署时由外部提供 Redis（docker-compose 的 redis 服务），
        // 通过 EMBEDDED_REDIS_ENABLED=false 关闭内嵌实例，避免在容器里多起一个进程。
        String enabled = System.getenv("EMBEDDED_REDIS_ENABLED");
        if (enabled != null && ("false".equalsIgnoreCase(enabled) || "0".equals(enabled)))
        {
            log.info("[EmbeddedRedis] 已通过 EMBEDDED_REDIS_ENABLED={} 禁用内嵌 Redis，使用外部实例", enabled);
            return;
        }
        try
        {
            redisServer = RedisServer.newRedisServer()
                    .port(REDIS_PORT)
                    .setting("maxmemory 256mb")
                    .setting("maxmemory-policy allkeys-lru")
                    .build();
            redisServer.start();
            log.info("[EmbeddedRedis] 本地 Redis 已启动，端口 {}", REDIS_PORT);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try
                {
                    if (redisServer != null)
                    {
                        redisServer.stop();
                        log.info("[EmbeddedRedis] 本地 Redis 已停止");
                    }
                }
                catch (Exception ignored)
                {
                    // 外部实例场景下无需停止
                }
            }));
        }
        catch (Exception e)
        {
            // 端口已被占用（例如已手动起了真实 Redis）= 视为已有实例，忽略
            log.warn("[EmbeddedRedis] 未能在 {} 启动（可能已有 Redis 在运行）：{}", REDIS_PORT, e.getMessage());
        }
    }
}
