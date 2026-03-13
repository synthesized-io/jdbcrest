package io.synthesized.jdbcrest;

import java.util.Map;

public sealed interface QueryTranspiler permits
        PostgreQueryTranspiler, JooqQueryTranspiler {

    default String toSQL(String schema, String table, Integer limit, Integer offset,
                 QueryAst.Expr query) {
        return toSQL(schema, table, limit, offset, query, null);
    }

    String toSQL(String schema, String table, Integer limit, Integer offset,
                 QueryAst.Expr query, Map<String, String> columns);

}
