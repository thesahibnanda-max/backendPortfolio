package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leetcode")
public record LeetcodeProperties(String baseUrl) {}
