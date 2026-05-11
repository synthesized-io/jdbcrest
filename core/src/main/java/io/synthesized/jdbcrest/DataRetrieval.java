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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataRetrieval {
    private final DataSource dataSource;
    private final QueryTranspiler queryTranspiler;

    public DataRetrieval(DataSource dataSource, QueryTranspiler queryTranspiler) {
        this.dataSource = dataSource;
        this.queryTranspiler = queryTranspiler;
    }

    public List<Map<String, Object>> readData(String schema, String table, Map<String, String[]> params)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return readData(connection, schema, table, params);
        }
    }

    public List<Map<String, Object>> readData(Connection connection, String schema,
                                              String table, Map<String, String[]> params) throws SQLException {

        QueryBody body = getTerms(params);

        QueryAst.Expr expr;
        if (body.terms.size() > 1) {
            expr = parse("and(" + String.join(",", body.terms) + ")");
        } else if (body.terms.size() == 1) {
            expr = parse(body.terms.get(0));
        } else expr = null;
        String sql = queryTranspiler.toSQL(schema, table, body.limit, body.offset, expr, body.columns);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnLabel(i);
                        Object value = switch (meta.getColumnType(i)) {
                            case Types.NCLOB -> rs.getString(i);
                            default -> rs.getObject(i);
                        };
                        row.put(colName, value);
                    }
                    result.add(row);
                }
            }
        }

        return result;
    }

    private record QueryBody(List<String> terms,
                             Integer limit,
                             Integer offset,
                             //Key is the database column name, value is its alias (==key if not provided)
                             Map<String, String> columns) {
    }

    private static QueryBody getTerms(Map<String, String[]> params) {
        Integer limit = null;
        Integer offset = null;
        List<String> terms = new ArrayList<>();
        Map<String, String> columns = new HashMap<>();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String column = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) continue;
            for (String raw : values) {
                if (raw == null) continue;
                switch (column.toLowerCase()) {
                    case "or", "and", "not.or", "not.and" -> terms.add(column + raw);
                    case "limit" -> limit = Integer.parseInt(raw);
                    case "offset" -> offset = Integer.parseInt(raw);
                    case "select" -> columns.putAll(parseSelectParam(raw));
                    default -> terms.add(column + "." + raw);
                }
            }
        }
        if (columns.isEmpty()) columns = null;
        return new QueryBody(terms, limit, offset, columns);
    }

    static Map<String, String> parseSelectParam(String raw) {
        Map<String, String> columns = new HashMap<>();
        if (raw == null || raw.isBlank()) return columns;
        for (String item : raw.split(",")) {
            if (item.isBlank()) continue;
            String trimmed = item.trim();
            int idx = trimmed.indexOf(":");
            if (idx >= 0) {
                String alias = trimmed.substring(0, idx).trim();
                String dbcol = trimmed.substring(idx + 1).trim();
                if (!alias.isEmpty() && !dbcol.isEmpty()) {
                    columns.put(dbcol, alias);
                }
            } else {
                columns.put(trimmed, trimmed);
            }
        }
        return columns;
    }

    private static QueryAst.Expr parse(String internalQuery) {
        try {
            return QueryParser.parse(internalQuery);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
