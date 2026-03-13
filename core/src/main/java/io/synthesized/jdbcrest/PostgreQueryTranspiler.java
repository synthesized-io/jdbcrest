package io.synthesized.jdbcrest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public final class PostgreQueryTranspiler implements QueryTranspiler {

    @Override
    public String toSQL(String schema, String table, Integer limit, Integer offset,
                         QueryAst.Expr query, java.util.Map<String, String> columns) {
        StringBuilder sql = new StringBuilder();
        if (columns == null || columns.isEmpty()) {
            sql.append("select *");
        } else {
            sql.append("select ");
            boolean first = true;
            for (java.util.Map.Entry<String, String> e : columns.entrySet()) {
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

    public String toLimitOffsetStatement(Integer limit, Integer offset) {
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
                String sqlOp;
                switch (comparison.operator()) {
                    case EQ:
                        sqlOp = "=";
                        break;
                    case GT:
                        sqlOp = ">";
                        break;
                    case GTE:
                        sqlOp = ">=";
                        break;
                    case LT:
                        sqlOp = "<";
                        break;
                    case LTE:
                        sqlOp = "<=";
                        break;
                    case NEQ:
                        sqlOp = "<>";
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported operator: " + comparison.operator());
                }

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
