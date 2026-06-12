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

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.SelectQuery;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transpiles QueryAst to SQL using jOOQ DSL (without executing).
 */
public final class JooqQueryTranspiler implements QueryTranspiler {
    private final SQLDialect sqlDialect;

    public JooqQueryTranspiler(SQLDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    private static final Pattern AGGREGATE_PATTERN = Pattern.compile("^([^.]+)\\.(avg|count|max|min|sum)\\(\\)$");

    @Override
    public String toSQL(String schema, String table,
                        @Nullable Integer limit,
                        @Nullable Integer offset,
                        QueryAst.@Nullable Expr query,
                        @Nullable Map<String, @Nullable String> columns) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        DSLContext ctx = DSL.using(sqlDialect);

        SelectQuery<Record> q = ctx.selectQuery();
        List<Field<?>> groupByFields = new ArrayList<>();
        boolean hasAggregates = false;

        if (columns == null || columns.isEmpty()) {
            q.addSelect(DSL.asterisk());
        } else {
            for (java.util.Map.Entry<String, @Nullable String> e : columns.entrySet()) {
                String col = e.getKey();
                String alias = e.getValue();
                Field<?> f;
                boolean isAggregate = false;

                if ("count()".equals(col)) {
                    f = DSL.count();
                    isAggregate = true;
                } else {
                    Matcher matcher = AGGREGATE_PATTERN.matcher(col);
                    if (matcher.matches()) {
                        String fieldName = matcher.group(1);
                        String function = matcher.group(2);
                        Field<Object> arg = DSL.field(DSL.name(fieldName));
                        f = switch (function) {
                            case "avg" -> DSL.avg(arg.cast(Double.class));
                            case "count" -> DSL.count(arg);
                            case "max" -> DSL.max(arg);
                            case "min" -> DSL.min(arg);
                            case "sum" -> DSL.sum(arg.cast(Double.class));
                            default -> throw new IllegalStateException("Unexpected function: " + function);
                        };
                        isAggregate = true;
                    } else {
                        f = DSL.field(DSL.name(col));
                    }
                }

                if (alias != null && !alias.equals(col)) {
                    f = f.as(DSL.name(alias));
                }
                q.addSelect(f);

                if (isAggregate) {
                    hasAggregates = true;
                } else {
                    groupByFields.add(DSL.field(DSL.name(col)));
                }
            }
        }

        if (hasAggregates && !groupByFields.isEmpty()) {
            q.addGroupBy(groupByFields);
        }

        q.addFrom(DSL.table(DSL.name(schema, table)));

        if (query != null) {
            q.addConditions(toCondition(query));
        }
        if (limit != null) {
            q.addLimit(limit);
        }
        if (offset != null) {
            q.addOffset(offset);
        }

        return q.getSQL(ParamType.INLINED);
    }

    private static Condition toCondition(QueryAst.Expr expr) {
        //NB: convert this to pattern matching in switch after upgrade to Java 21
        Objects.requireNonNull(expr, "expr");

        if (expr instanceof QueryAst.And and) {
            return and.args().stream()
                    .map(JooqQueryTranspiler::toCondition)
                    .reduce(Condition::and)
                    .orElseThrow();
        } else if (expr instanceof QueryAst.Or or) {
            return or.args().stream()
                    .map(JooqQueryTranspiler::toCondition)
                    .reduce(Condition::or)
                    .orElseThrow();
        } else if (expr instanceof QueryAst.Not not) {
            return DSL.not(toCondition(not.arg()));
        } else if (expr instanceof QueryAst.Comparison comparison) {
            return comparisonToCondition(comparison);
        } else if (expr instanceof QueryAst.In in) {
            return inToCondition(in);
        } else {
            throw new IllegalArgumentException("Unsupported expression: " + expr);
        }
    }

    private static Condition comparisonToCondition(QueryAst.Comparison comparison) {
        return comparisonToCondition(
                DSL.name(comparison.field().value()),
                comparison.operator(),
                comparison.value()
        );
    }

    private static Condition comparisonToCondition(
            Name fieldName,
            QueryAst.ComparisonOperator op,
            QueryAst.Value value
    ) {
        //NB: convert this to pattern matching in switch after upgrade to Java 21
        if (value instanceof QueryAst.IntLiteral i) {
            return compare(fieldName, op, Integer.class, i.value());
        } else if (value instanceof QueryAst.DoubleLiteral d) {
            return compare(fieldName, op, Double.class, d.value());
        } else if (value instanceof QueryAst.StringLiteral s) {
            return compare(fieldName, op, String.class, s.value());
        } else if (value instanceof QueryAst.DateLiteral d) {
            return compare(fieldName, op, LocalDate.class, d.value());
        } else {
            throw new IllegalArgumentException("Unsupported literal: " + value);
        }
    }

    private static Condition inToCondition(QueryAst.In in) {
        List<Object> values = in.values().stream()
                .map(QueryAst.Value::objectValue).toList();
        if (values.isEmpty()) {
            return DSL.falseCondition();
        }
        return DSL.field(DSL.name(in.field().value())).in(values);
    }

    private static <T> Condition compare(
            Name fieldName,
            QueryAst.ComparisonOperator op,
            Class<T> type,
            T value
    ) {
        return compare(DSL.field(fieldName, type), op, value);
    }

    private static <T> Condition compare(Field<T> field, QueryAst.ComparisonOperator op, T value) {
        return switch (op) {
            case EQ -> field.eq(value);
            case NEQ -> field.ne(value);
            case GT -> field.gt(value);
            case GTE -> field.ge(value);
            case LT -> field.lt(value);
            case LTE -> field.le(value);
        };
    }
}
