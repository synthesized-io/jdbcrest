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

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Transpiles QueryAst to SQL using jOOQ DSL (without executing).
 */
public final class JooqQueryTranspiler implements QueryTranspiler {
    private final SQLDialect sqlDialect;

    public JooqQueryTranspiler(SQLDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    @Override
    public String toSQL(String schema, String table, Integer limit, Integer offset, QueryAst.Expr query, java.util.Map<String, String> columns) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        DSLContext ctx = DSL.using(sqlDialect);

        SelectQuery<Record> q = ctx.selectQuery();
        if (columns == null || columns.isEmpty()) {
            q.addSelect(DSL.asterisk());
        } else {
            for (java.util.Map.Entry<String, String> e : columns.entrySet()) {
                String col = e.getKey();
                String alias = e.getValue();
                Field<Object> f = DSL.field(DSL.name(col));
                if (alias != null && !alias.equals(col)) {
                    q.addSelect(f.as(DSL.name(alias)));
                } else {
                    q.addSelect(f);
                }
            }
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
