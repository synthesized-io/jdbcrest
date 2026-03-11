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
    public String toSQL(String schema, String table, Integer limit, Integer offset, QueryAst.Expr query) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");

        DSLContext ctx = DSL.using(sqlDialect);

        SelectQuery<Record> q = ctx.selectQuery();
        q.addSelect(DSL.asterisk());
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
        return switch (Objects.requireNonNull(expr, "expr")) {
            case QueryAst.And and -> and.args().stream()
                    .map(JooqQueryTranspiler::toCondition)
                    .reduce(Condition::and).orElseThrow();

            case QueryAst.Or or -> or.args().stream()
                    .map(JooqQueryTranspiler::toCondition)
                    .reduce(Condition::or).orElseThrow();

            case QueryAst.Not not -> DSL.not(toCondition(not.arg()));
            case QueryAst.Comparison comparison -> comparisonToCondition(comparison);
            case QueryAst.In in -> inToCondition(in);
        };
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
        return switch (value) {
            case QueryAst.IntLiteral i -> compare(fieldName, op, Integer.class, i.value());
            case QueryAst.DoubleLiteral d -> compare(fieldName, op, Double.class, d.value());
            case QueryAst.StringLiteral s -> compare(fieldName, op, String.class, s.value());
            case QueryAst.DateLiteral d -> compare(fieldName, op, LocalDate.class, d.value());
        };
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
