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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Legacy template-only calls cannot supply a governed contract/action and complete retained intent.
 */
public final class LegacyDocumentMigration {
  private LegacyDocumentMigration() {}

  public static ResponseStatusException required() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "GOVERNED_DOCUMENT_INTENT_REQUIRED");
  }
}
