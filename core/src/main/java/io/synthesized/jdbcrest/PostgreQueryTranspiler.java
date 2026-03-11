package io.synthesized.jdbcrest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public final class PostgreQueryTranspiler implements QueryTranspiler {

    @Override
    public String toSQL(String schema, String table, Integer limit, Integer offset,
                         QueryAst.Expr query) {
        StringBuilder sql = new StringBuilder("select * from ").append(
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
            switch (expr) {
                case QueryAst.And and -> {
                    List<String> operands = popN(stack, and.args().size());
                    stack.push("(" + String.join(" AND ", operands) + ")");
                }
                case QueryAst.Comparison comparison -> {
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
                }
                case QueryAst.In in -> {
                    String values = in.values().stream().map(QueryAst.Value::quotedIfNeeded)
                            .collect(Collectors.joining(", "));
                    stack.push(in.field().raw() + " IN (" + values + ")");
                }
                case QueryAst.Not not -> {
                    stack.push("NOT (" + stack.pop() + ")");
                }
                case QueryAst.Or or -> {
                    List<String> operands = popN(stack, or.args().size());
                    stack.push("(" + String.join(" OR ", operands) + ")");
                }
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
