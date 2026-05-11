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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryTranspilerTest {
    @ParameterizedTest
    @CsvSource(
            delimiterString = "|",
            value = {
                    "x.eq.\"hello, world\"          | x = 'hello, world'",
                    "or(and(a.eq.1,b.eq.2),c.gt.0) | (c > 0 OR (b = 2 AND a = 1))",
                    "created_at.gte.2017-01-11     |  created_at >= '2017-01-11'",
                    "name.eq.Bob                   |  name = 'Bob'",
                    "id.in.(100,102,103)           |  id IN (100, 102, 103)"
            })
    void transpileCondition(String input, String output) throws ParseException {
        QueryAst.Expr exp = QueryParser.parse(input);
        PostgreQueryTranspiler transpiler = new PostgreQueryTranspiler();
        assertThat(transpiler.toWhereConditions(exp)).isEqualTo(output);
    }

    @ParameterizedTest
    @CsvSource(
            delimiterString = "|",
            value = {
                    "x.eq.\"hello, world\"         | \"x\" = 'hello, world'",
                    "or(and(a.eq.1,b.eq.2),c.gt.0) | ((\"a\" = 1 and \"b\" = 2) or \"c\" > 0)",
                    "created_at.gte.2017-01-11     |  \"created_at\" >= date '2017-01-11'",
                    "name.eq.Bob                   |  \"name\" = 'Bob'",
                    "id.in.(100,102,103)           |  \"id\" in (100, 102, 103)"
            })
    void transpileSQL(String input, String output) throws ParseException {
        QueryAst.Expr exp = QueryParser.parse(input);
        JooqQueryTranspiler transpiler = new JooqQueryTranspiler(SQLDialect.POSTGRES);
        assertThat(transpiler.toSQL("foo", "bar", null, null, exp))
                .isEqualTo("select * from \"foo\".\"bar\" where " + output);
    }
}
