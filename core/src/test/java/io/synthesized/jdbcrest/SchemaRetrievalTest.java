/*
 * Copyright 2026 Synthesized Ltd
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
package io.synthesized.jdbcrest;

import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAPI property {@link SchemaRetrieval} writes for a column: the canonical type in
 * {@code format}, the JSON schema type where PostgREST gives one, and {@code items} for arrays.
 */
public class SchemaRetrievalTest {

    @Test
    void scalarColumnHasFormatThenType() {
        Map<String, Object> property = SchemaRetrieval.property(Types.DATE, "date");
        assertThat(property).containsExactly(Map.entry("format", "date"), Map.entry("type", "string"));
    }

    @Test
    void numericColumnIsANumber() {
        assertThat(SchemaRetrieval.property(Types.DECIMAL, "DECIMAL"))
                .containsExactly(Map.entry("format", "numeric"), Map.entry("type", "number"));
    }

    @Test
    void jsonColumnHasOnlyAFormat() {
        assertThat(SchemaRetrieval.property(Types.OTHER, "jsonb")).containsExactly(Map.entry("format", "jsonb"));
    }

    @Test
    void arrayColumnDescribesItsItems() {
        assertThat(SchemaRetrieval.property(Types.ARRAY, "_int4")).containsExactly(
                Map.entry("format", "integer[]"),
                Map.entry("type", "array"),
                Map.entry("items", Map.of("type", "integer")));
    }

    @Test
    void arrayOfJsonHasObjectItems() {
        assertThat(SchemaRetrieval.property(Types.ARRAY, "_jsonb")).containsExactly(
                Map.entry("format", "jsonb[]"),
                Map.entry("type", "array"),
                Map.entry("items", Map.of("type", "object")));
    }
}
