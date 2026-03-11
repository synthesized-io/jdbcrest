package io.synthesized.jdbcrest;

public sealed interface QueryTranspiler permits
        PostgreQueryTranspiler, JooqQueryTranspiler {


    String toSQL(String schema, String table, Integer limit, Integer offset,
                 QueryAst.Expr query);

}
