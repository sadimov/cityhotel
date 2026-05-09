package com.cityprojects.citybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Classe principale de l'application City Backend
 * Système de gestion hôtelière multi-tenant
 *
 * Note (Tour 3A) : @EnableJpaAuditing est desormais porte par
 * {@link com.cityprojects.citybackend.config.JpaAuditingConfig} avec un
 * auditorAwareRef explicite. Ne pas le redeclarer ici (double activation).
 *
 * @author City Projects
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableTransactionManagement
public class CitybackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CitybackendApplication.class, args);
    }
}