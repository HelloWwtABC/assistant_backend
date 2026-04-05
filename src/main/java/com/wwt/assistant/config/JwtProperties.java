package com.wwt.assistant.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥。
     */
    private String secret;

    /**
     * Token 发行者。
     */
    private String issuer = "assistant";

    /**
     * Token 有效期，单位秒。
     */
    private long expireSeconds = 86400L;

    /**
     * Token 自动刷新阈值，单位秒。
     */
    private long refreshThresholdSeconds = 1800L;

    /**
     * 白名单，支持 Ant 风格。
     */
    private List<String> whitelist = new ArrayList<>();
}
