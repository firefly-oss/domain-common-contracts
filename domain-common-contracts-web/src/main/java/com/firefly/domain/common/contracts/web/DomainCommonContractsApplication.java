package com.firefly.domain.common.contracts.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Spring Boot application for the domain-common-contracts service.
 * Orchestrates contract management operations by composing core-common-contract-mgmt
 * and core-common-document-mgmt services.
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.firefly.domain.common.contracts",
                "com.firefly.common.web"
        }
)
@EnableWebFlux
@ConfigurationPropertiesScan
@OpenAPIDefinition(
        info = @Info(
                title = "${spring.application.name}",
                version = "${spring.application.version}",
                description = "${spring.application.description}",
                contact = @Contact(
                        name = "${spring.application.team.name}",
                        email = "${spring.application.team.email}"
                )
        ),
        servers = {
                @Server(
                        url = "http://core.getfirefly.io/domain-common-contracts",
                        description = "Development Environment"
                ),
                @Server(
                        url = "/",
                        description = "Local Development Environment"
                )
        }
)
public class DomainCommonContractsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomainCommonContractsApplication.class, args);
    }
}
