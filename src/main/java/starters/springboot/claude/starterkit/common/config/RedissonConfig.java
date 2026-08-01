package starters.springboot.claude.starterkit.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 자체 설정을 별도로 두지 않고 Spring Data Redis와 동일한
 * spring.data.redis.host/port를 참조한다 — 두 클라이언트의 접속 정보가 어긋나
 * TestContainers 전환 시 한쪽만 누락되는 실수를 방지하기 위함 (docs/tech-decisions.md D-4).
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}
