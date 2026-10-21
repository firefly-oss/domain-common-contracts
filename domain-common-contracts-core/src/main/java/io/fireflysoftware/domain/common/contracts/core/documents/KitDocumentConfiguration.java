/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.domain.common.contracts.core.documents;

import io.fireflysoftware.documents.DocumentIssuance;
import io.fireflysoftware.documents.DocumentPort;
import io.fireflysoftware.documents.DocumentTemplateCatalog;
import io.fireflysoftware.documents.SandboxDocumentTemplateCatalog;
import io.fireflysoftware.documents.render.DocumentRenderPort;
import io.fireflysoftware.documents.render.FreeMarkerDocumentRenderAdapter;
import io.fireflysoftware.documents.storage.DocumentStoragePort;
import io.fireflysoftware.documents.storage.SandboxDocumentStorageAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sandbox document issuance. The bean name is not {@code documentPort}, which the ECM
 * auto-configuration already owns for the document core client.
 */
@Configuration
public class KitDocumentConfiguration {

    @Bean
    public DocumentTemplateCatalog kitDocumentTemplateCatalog() {
        return new SandboxDocumentTemplateCatalog();
    }

    @Bean
    public DocumentRenderPort kitDocumentRenderPort() {
        return new FreeMarkerDocumentRenderAdapter();
    }

    @Bean
    public DocumentStoragePort kitDocumentStoragePort() {
        return new SandboxDocumentStorageAdapter();
    }

    @Bean
    public DocumentPort kitDocumentPort(DocumentTemplateCatalog kitDocumentTemplateCatalog,
                                         DocumentRenderPort kitDocumentRenderPort,
                                         DocumentStoragePort kitDocumentStoragePort) {
        return new DocumentIssuance(kitDocumentTemplateCatalog, kitDocumentRenderPort, kitDocumentStoragePort);
    }
}
