package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codeforces")
public record CodeforcesProperties(String baseUrl) {}
