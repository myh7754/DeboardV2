package org.example.deboardv2.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
//        1. RedisConnectionFactory
//
//                - Redis와의 연결을 위한 'Connection'을 생성하고 관리하는 메서드입니다.
//        - 해당 여기서는 LettuceConnectionFactory를 사용하여 host와 port 정보를 기반으로 연결을 생성합니다.
        return new LettuceConnectionFactory(host,port);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // Redis를 연결합니다.
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        // key값과 value값의 타입을 정합니다.
        // StringRedisSerializer 등등 이런 직렬화 코드들은 그때 그때 필요하다면 가져와서 사용
        // Key: String
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // Value: JSON 직렬화
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        return redisTemplate;
    }





}
