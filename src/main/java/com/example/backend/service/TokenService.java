package com.example.backend.service;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenService {

    private final RedisTemplate<String, String> redisTemplate;

    public TokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 레디스에 리프레시 토큰 저장
    public void saveRefreshToken(String account, String refreshToken) {
    redisTemplate.opsForValue().set("RT:" + account, refreshToken, Duration.ofDays(7));
    }

    // 레디스에서 리프레시 토큰 꺼내기
    public String getRefreshToken(String account) {
        return redisTemplate.opsForValue().get("RT:" + account);
    }

    // 리프레시 토큰 삭제
    public void deleteRefreshToken(String account) {
        redisTemplate.delete("RT:" + account);

    }
}


//refresh token이 유효할 겨우 새로운 access token을 발급하고, redis를 통해 refresh token 관리