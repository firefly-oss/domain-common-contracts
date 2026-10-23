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

package io.fireflysoftware.domain.common.contracts.core.handlers;

import io.fireflysoftware.domain.common.contracts.core.commands.GenerateContractDocumentCommand;
import io.fireflysoftware.domain.common.contracts.core.documents.LegacyDocumentMigration;
import java.util.UUID;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import reactor.core.publisher.Mono;

/** Preserves the legacy command shape while requiring migration to a complete governed intent. */
@CommandHandlerComponent
public class GenerateContractDocumentHandler
    extends CommandHandler<GenerateContractDocumentCommand, UUID> {
  public GenerateContractDocumentHandler(SagaEngine sagaEngine) {}

  @Override
  protected Mono<UUID> doHandle(GenerateContractDocumentCommand command) {
    return Mono.error(LegacyDocumentMigration.required());
  }
}
