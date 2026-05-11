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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DataRetrievalSelectParseTest {

    @Test
    void parseSimpleColumns() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("id,price");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "id",
                "price", "price"
        ));
    }

    @Test
    void parseColumnsWithAliases() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("c1:id,c2:price");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "c1",
                "price", "c2"
        ));
    }

    @Test
    void parseIgnoresBlanksAndWhitespace() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("  id , , price  ,  ");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "id",
                "price", "price"
        ));
    }

    @Test
    void parseNullOrEmptyReturnsEmpty() {
        assertThat(DataRetrieval.parseSelectParam(null)).isEmpty();
        assertThat(DataRetrieval.parseSelectParam("")).isEmpty();
        assertThat(DataRetrieval.parseSelectParam("   ")).isEmpty();
    }

    @Test
    void parseSkipsInvalidAliasForms() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("a:, :b, valid");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "valid", "valid"
        ));
    }
}
