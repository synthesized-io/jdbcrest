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

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes the tables of a schema as PostgREST does at its API root: an OpenAPI 2.0 document whose
 * {@code definitions} list every table with its columns, each column carrying the canonical type name
 * (see {@link SqlTypeNames}) in {@code format} and the JSON schema type in {@code type}.
 *
 * <p>This is the one place where callers can learn the column types behind the values that
 * {@link DataRetrieval} returns, without a second query against the database.
 */
public final class SchemaRetrieval {
    private static final String[] TABLE_TYPES = {"TABLE", "VIEW"};

    private final DataSource dataSource;

    public SchemaRetrieval(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * The OpenAPI document of {@code schema}.
     *
     * @param schema the database schema to describe
     * @param basePath the path under which the tables of this schema are served
     */
    public Map<String, Object> openApi(String schema, String basePath) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return openApi(connection, schema, basePath);
        }
    }

    public Map<String, Object> openApi(Connection connection, String schema, String basePath)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> definitions = new LinkedHashMap<>();
        try (ResultSet tables = metaData.getTables(null, schema, "%", TABLE_TYPES)) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                definitions.put(table, Map.of("type", "object", "properties", properties(metaData, schema, table)));
                paths.put("/" + table, Map.of("get", Map.of(
                        "tags", List.of(table),
                        "responses", Map.of("200", Map.of(
                                "description", "OK",
                                "schema", Map.of(
                                        "type", "array",
                                        "items", Map.of("$ref", "#/definitions/" + table)))))));
            }
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("swagger", "2.0");
        document.put("info", Map.of("title", "JDBCRest API", "description", "Schema " + schema, "version", ""));
        document.put("basePath", basePath);
        document.put("paths", paths);
        document.put("definitions", definitions);
        return document;
    }

    private static Map<String, Object> properties(DatabaseMetaData metaData, String schema, String table)
            throws SQLException {
        Map<String, Object> properties = new LinkedHashMap<>();
        try (ResultSet columns = metaData.getColumns(null, schema, table, "%")) {
            while (columns.next()) {
                properties.put(columns.getString("COLUMN_NAME"),
                        property(columns.getInt("DATA_TYPE"), columns.getString("TYPE_NAME")));
            }
        }
        return properties;
    }

    static Map<String, Object> property(int jdbcType, @Nullable String driverTypeName) {
        String canonical = SqlTypeNames.canonical(jdbcType, driverTypeName);
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("format", canonical);
        String type = SqlTypeNames.jsonType(canonical);
        if (type != null) {
            property.put("type", type);
        }
        if (canonical.endsWith("[]")) {
            String elementType = SqlTypeNames.jsonType(canonical.substring(0, canonical.length() - 2));
            property.put("items", Map.of("type", elementType == null ? "object" : elementType));
        }
        return property;
    }
}
