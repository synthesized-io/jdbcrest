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

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public final class PostgreQueryTranspiler implements QueryTranspiler {

    @Override
    public String toSQL(String schema, String table, @Nullable Integer limit, @Nullable Integer offset,
                         QueryAst.@Nullable Expr query, java.util.@Nullable Map<String, @Nullable String> columns) {
        StringBuilder sql = new StringBuilder();
        if (columns == null || columns.isEmpty()) {
            sql.append("select *");
        } else {
            sql.append("select ");
            boolean first = true;
            for (java.util.Map.Entry<String, @Nullable String> e : columns.entrySet()) {
                if (!first) sql.append(", ");
                first = false;
                String col = e.getKey();
                String alias = e.getValue();
                sql.append(ansiQuote(col));
                if (alias != null && !alias.equals(col)) {
                    sql.append(" as ").append(ansiQuote(alias));
                }
            }
        }
        sql.append(" from ").append(
                ansiQuote(schema)).append('.').append(
                ansiQuote(table));
        String whereTerm = query == null ? "" : toWhereConditions(query);
        if (!whereTerm.isEmpty()) {
            sql.append(" where ").append(whereTerm);
        }
        sql.append(" order by 1 ");
        sql.append(toLimitOffsetStatement(limit, offset));
        return sql.toString();
    }

    public String toLimitOffsetStatement(@Nullable Integer limit, @Nullable Integer offset) {
        return String.format(" limit %s offset %s", limit == null ? "all" : limit.toString(),
                offset == null ? "0" : offset.toString());
    }

    public String toWhereConditions(QueryAst.Expr query) {

        Deque<String> stack = new ArrayDeque<>();
        query.accept(expr -> {
            if (expr instanceof QueryAst.And and) {
                List<String> operands = popN(stack, and.args().size());
                stack.push("(" + String.join(" AND ", operands) + ")");
            } else if (expr instanceof QueryAst.Comparison comparison) {
                String sqlOp = switch (comparison.operator()) {
                    case EQ -> "=";
                    case GT -> ">";
                    case GTE -> ">=";
                    case LT -> "<";
                    case LTE -> "<=";
                    case NEQ -> "<>";
                };

                stack.push(comparison.field().raw() + " " + sqlOp + " "
                        + comparison.value().quotedIfNeeded());
            } else if (expr instanceof QueryAst.In in) {
                String values = in.values().stream()
                        .map(QueryAst.Value::quotedIfNeeded)
                        .collect(Collectors.joining(", "));
                stack.push(in.field().raw() + " IN (" + values + ")");
            } else if (expr instanceof QueryAst.Not not) {
                stack.push("NOT (" + stack.pop() + ")");
            } else if (expr instanceof QueryAst.Or or) {
                List<String> operands = popN(stack, or.args().size());
                stack.push("(" + String.join(" OR ", operands) + ")");
            } else {
                throw new IllegalArgumentException("Unsupported expression: " + expr);
            }
        });
        return stack.pop();
    }

    private static List<String> popN(Deque<String> stack, int n) {
        ArrayList<String> operands = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            operands.add(stack.pop());
        }
        return operands;
    }

    private static String ansiQuote(String string) {
        return "\"" + string.replace("\"", "\"\"") + "\"";
    }
}
