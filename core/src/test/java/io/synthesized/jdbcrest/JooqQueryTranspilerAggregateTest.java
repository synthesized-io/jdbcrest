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

import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JooqQueryTranspilerAggregateTest {
    private final JooqQueryTranspiler transpiler = new JooqQueryTranspiler(SQLDialect.POSTGRES);

    @Test
    void testCountAll() {
        Map<String, String> columns = Map.of("count()", "count");
        String sql = transpiler.toSQL("public", "products", null, null, null, columns);
        assertThat(sql).containsIgnoringCase("select count(*) as \"count\" from \"public\".\"products\"");
    }

    @Test
    void testColumnSum() {
        Map<String, String> columns = Map.of("price.sum()", "total_price");
        String sql = transpiler.toSQL("public", "products", null, null, null, columns);
        assertThat(sql).containsIgnoringCase("sum(");
        assertThat(sql).containsIgnoringCase("cast(\"price\" as double precision)");
    }

    @Test
    void testAutomaticGroupBy() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("category", "category");
        columns.put("price.avg()", "avg_price");

        String sql = transpiler.toSQL("public", "products", null, null, null, columns);
        assertThat(sql).containsIgnoringCase("avg(");
        assertThat(sql).containsIgnoringCase("group by \"category\"");
    }

    @Test
    void testMultipleAggregatesAndGroupBy() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("category", "cat");
        columns.put("brand", "brand");
        columns.put("price.min()", "min_p");
        columns.put("price.max()", "max_p");
        columns.put("id.count()", "cnt");

        String sql = transpiler.toSQL("public", "products", null, null, null, columns);
        assertThat(sql).containsIgnoringCase(
                "select \"category\" as \"cat\", \"brand\", min(\"price\") as \"min_p\", max(\"price\") as "
                        + "\"max_p\", count(\"id\") as \"cnt\" from \"public\".\"products\" "
                        + "group by \"category\", \"brand\"");
    }

    @Test
    void testNoAggregatesNoGroupBy() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id");
        columns.put("name", "name");

        String sql = transpiler.toSQL("public", "products", null, null, null, columns);
        assertThat(sql).doesNotContainIgnoringCase("group by");
        assertThat(sql).containsIgnoringCase("select \"id\", \"name\" from \"public\".\"products\"");
    }
}
