package io.synthesized.jdbcrest;

public sealed interface QueryTranspiler permits
        PostgreQueryTranspiler {
    default String toSQL(String schema, String table,
                         QueryAst.Expr query) {
        StringBuilder sql = new StringBuilder("select * from ").append(
                ansiQuote(schema)).append('.').append(
                ansiQuote(table));
        String whereTerm = query == null ? "" : toWhereConditions(query);
        if (!whereTerm.isEmpty()) {
            sql.append(" where ").append(whereTerm);
        }
        sql.append(" order by 1");
        return sql.toString();
    }

    String toWhereConditions(QueryAst.Expr query);

    private static String ansiQuote(String string) {
        return "\"" + string.replace("\"", "\"\"") + "\"";
    }
}
