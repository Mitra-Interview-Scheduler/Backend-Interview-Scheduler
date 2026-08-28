package com.nemal.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Railway Postgres often exposes DATABASE_URL. If SPRING_DATASOURCE_* is missing or still
 * contains unresolved template tokens, derive JDBC settings from DATABASE_URL.
 */
public class RailwayDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }

        String springUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        if (isUsableJdbcUrl(springUrl)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "railwayDatabaseSsl",
                    Map.of("SPRING_DATASOURCE_URL", withSslMode(springUrl))));
            return;
        }

        String databaseUrl = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("DATABASE_PRIVATE_URL"));
        if (databaseUrl == null) {
            return;
        }

        Map<String, Object> derived = deriveFromDatabaseUrl(databaseUrl);
        if (!derived.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("railwayDatabase", derived));
        }
    }

    private static boolean isUsableJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.startsWith("jdbc:postgresql://")
                && !url.contains("${");
    }

    private static Map<String, Object> deriveFromDatabaseUrl(String databaseUrl) {
        try {
            URI uri = URI.create(databaseUrl.replace("postgresql://", "postgres://"));
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Map.of();
            }

            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            String database = path != null && path.length() > 1 ? path.substring(1) : "railway";

            String username = "";
            String password = "";
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                int separator = userInfo.indexOf(':');
                if (separator >= 0) {
                    username = userInfo.substring(0, separator);
                    password = userInfo.substring(separator + 1);
                } else {
                    username = userInfo;
                }
            }

            Map<String, Object> values = new HashMap<>();
            values.put(
                    "SPRING_DATASOURCE_URL",
                    withSslMode("jdbc:postgresql://" + host + ":" + port + "/" + database));
            if (!username.isBlank()) {
                values.put("SPRING_DATASOURCE_USERNAME", username);
            }
            if (!password.isBlank()) {
                values.put("SPRING_DATASOURCE_PASSWORD", password);
            }
            return values;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String withSslMode(String jdbcUrl) {
        if (jdbcUrl.contains("sslmode=")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
