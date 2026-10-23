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
package io.fireflysoftware.domain.common.contracts.infra.documents.v1;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;

/**
 * Local SDK transport profile; callers supply a fresh SDK mapper with its date/nullable modules.
 */
final class GovernedPeerCodec {
  static final int MAX_BODY_BYTES = 16777216;

  private GovernedPeerCodec() {}

  static ObjectMapper strict(ObjectMapper freshSdkMapper) {
    freshSdkMapper
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxDocumentLength(MAX_BODY_BYTES)
                .maxNestingDepth(64)
                .maxNameLength(1024)
                .maxStringLength(2097152)
                .maxNumberLength(128)
                .build());
    freshSdkMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    freshSdkMapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    freshSdkMapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    freshSdkMapper.enable(
        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
        DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
        DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    freshSdkMapper
        .coercionConfigFor(LogicalType.Textual)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    return freshSdkMapper;
  }
}
