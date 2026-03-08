package io.synthesized.jdbcrest;

public sealed interface QueryTranspiler permits
        PostgreQueryTranspiler {
    default String toSQL(String schema, String table, Integer limit, Integer offset,
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

    String toLimitOffsetStatement(Integer limit, Integer offset);

    String toWhereConditions(QueryAst.Expr query);

    private static String ansiQuote(String string) {
        return "\"" + string.replace("\"", "\"\"") + "\"";
    }
}
