package com.securitylab.sessions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutos
public class ModuleSessionsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModuleSessionsApplication.class, args);
    }
}