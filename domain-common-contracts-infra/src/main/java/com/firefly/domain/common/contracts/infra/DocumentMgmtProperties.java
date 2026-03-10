package com.firefly.domain.common.contracts.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the core-common-document-mgmt service connection.
 * Maps the base path from application.yaml under api-configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "api-configuration.common-platform.document-mgmt")
@Data
public class DocumentMgmtProperties {

    private String basePath;

}
