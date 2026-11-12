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

import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.exception.SQLDialectNotSupportedException;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataRetrieval {
    private final DataSource dataSource;
    private final QueryTranspiler queryTranspiler;

    public DataRetrieval(DataSource dataSource, QueryTranspiler queryTranspiler) {
        this.dataSource = dataSource;
        this.queryTranspiler = queryTranspiler;
    }

    public List<Map<String, @Nullable Object>> readData(String schema, String table,
                                                        Map<String, ? extends String @Nullable []> params)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return readData(connection, schema, table, params);
        }
    }

    public List<Map<String, @Nullable Object>> readData(Connection connection, String schema, String table,
                                                        Map<String, ? extends String @Nullable []> params)
            throws SQLException {

        QueryBody body = getTerms(params);

        QueryAst.@Nullable Expr expr;
        if (body.terms.size() > 1) {
            expr = parse("and(" + String.join(",", body.terms) + ")");
        } else if (body.terms.size() == 1) {
            expr = parse(body.terms.get(0));
        } else expr = null;
        String sql = queryTranspiler.toSQL(schema, table, body.limit, body.offset, expr, body.columns);
        List<Map<String, @Nullable Object>> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                // jOOQ maps each column to a Java type from the result set metadata, which hides the
                // differences between drivers (timestamptz, timetz, intervals, json, arrays, clobs).
                // The values are then rendered the way PostgREST prints them while the connection is open.
                DSLContext dsl = DSL.using(connection);
                Result<Record> records = dsl.fetch(rs, columnTypes(dsl.family(), rs.getMetaData()));
                for (Record record : records) {
                    Field<?>[] fields = record.fields();
                    Map<String, @Nullable Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < fields.length; i++) {
                        row.put(fields[i].getName(), JsonValues.toJsonValue(record.get(i)));
                    }
                    result.add(row);
                }
            }
        }

        return result;
    }

    /**
     * The jOOQ data type of each column, as jOOQ itself derives it from the metadata, except for the
     * temporal columns. Dates and timestamps are read as {@code java.time} types so that they do not
     * round-trip through the JVM time zone. Times are read as text, because every binding of a TIME
     * column goes through {@code java.sql.Time}, which has no fraction of a second; the text form the
     * driver returns is the one the database prints, {@code 04:30:57.5}.
     */
    private static DataType<?>[] columnTypes(SQLDialect family, ResultSetMetaData meta) throws SQLException {
        DataType<?>[] types = new DataType<?>[meta.getColumnCount()];
        for (int i = 0; i < types.length; i++) {
            DataType<?> type = SQLDataType.OTHER;
            try {
                type = DefaultDataType.getDataType(family, meta.getColumnTypeName(i + 1),
                        meta.getPrecision(i + 1), meta.getScale(i + 1));
            } catch (SQLDialectNotSupportedException e) {
                // A type jOOQ does not know for this dialect is read with getObject(), as jOOQ does.
            }
            if (type.getType() == java.sql.Date.class) {
                type = SQLDataType.LOCALDATE;
            } else if (type.getType() == java.sql.Time.class) {
                type = SQLDataType.VARCHAR;
            } else if (type.getType() == java.sql.Timestamp.class) {
                type = SQLDataType.LOCALDATETIME;
            }
            types[i] = type;
        }
        return types;
    }

    private record QueryBody(List<String> terms,
                             @Nullable Integer limit,
                             @Nullable Integer offset,
                             //Key is the database column name, value is its alias (==key if not provided)
                             @Nullable Map<String, @Nullable String> columns) {
    }

    private static QueryBody getTerms(Map<String, ? extends String @Nullable []> params) {
        Integer limit = null;
        Integer offset = null;
        List<String> terms = new ArrayList<>();
        // Insertion ordered: the select list decides the column order of the response.
        Map<String, @Nullable String> columns = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends String @Nullable []> entry : params.entrySet()) {
            String column = entry.getKey();
            String @Nullable[] values = entry.getValue();
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
        return new QueryBody(terms, limit, offset, columns.isEmpty() ? null : columns);
    }

    static Map<String, String> parseSelectParam(@Nullable String raw) {
        Map<String, String> columns = new LinkedHashMap<>();
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
