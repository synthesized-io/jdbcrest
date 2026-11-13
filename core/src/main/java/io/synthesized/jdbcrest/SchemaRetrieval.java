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
import java.util.HashMap;
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
    /**
     * The relation types the data endpoint can read, by the names the drivers report. Postgres also
     * distinguishes materialized views, partitioned tables and foreign tables; a driver that does not
     * know a name ignores it.
     */
    private static final String[] TABLE_TYPES = {
            "TABLE", "VIEW", "MATERIALIZED VIEW", "PARTITIONED TABLE", "FOREIGN TABLE"};

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
        String schemaPattern = literalPattern(schema, metaData.getSearchStringEscape());
        Map<String, Map<String, Object>> columnsByTable = columnsByTable(metaData, schemaPattern);
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> definitions = new LinkedHashMap<>();
        try (ResultSet tables = metaData.getTables(null, schemaPattern, "%", TABLE_TYPES)) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                Map<String, Object> properties = columnsByTable.getOrDefault(table, Map.of());
                definitions.put(table, Map.of("type", "object", "properties", properties));
                paths.put("/" + table, Map.of("get", Map.of(
                        "tags", List.of(table),
                        "responses", Map.of("200", Map.of(
                                "description", "OK",
                                "schema", Map.of(
                                        "type", "array",
                                        "items", Map.of("$ref", definitionRef(table))))))));
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

    /**
     * The properties of every table of the schema from a single metadata query, grouped by the exact
     * table name. The driver returns the columns ordered by table and ordinal position.
     */
    private static Map<String, Map<String, Object>> columnsByTable(DatabaseMetaData metaData, String schemaPattern)
            throws SQLException {
        Map<String, Map<String, Object>> result = new HashMap<>();
        try (ResultSet columns = metaData.getColumns(null, schemaPattern, "%", "%")) {
            while (columns.next()) {
                result.computeIfAbsent(columns.getString("TABLE_NAME"), table -> new LinkedHashMap<>())
                        .put(columns.getString("COLUMN_NAME"),
                                property(columns.getInt("DATA_TYPE"), columns.getString("TYPE_NAME")));
            }
        }
        return result;
    }

    /**
     * {@code name} as a metadata search pattern that matches it literally: the pattern characters
     * {@code _} and {@code %}, and the escape itself, are escaped with the driver's escape string.
     */
    static String literalPattern(String name, @Nullable String escape) {
        if (escape == null || escape.isEmpty()) {
            return name;
        }
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    /** The JSON reference to the definition of {@code table}, a JSON Pointer with {@code ~} and {@code /} escaped. */
    static String definitionRef(String table) {
        return "#/definitions/" + table.replace("~", "~0").replace("/", "~1");
    }

    /** The property of a column: the canonical type in {@code format}, then the JSON schema of its values. */
    static Map<String, Object> property(int jdbcType, @Nullable String driverTypeName) {
        String canonical = SqlTypeNames.canonical(jdbcType, driverTypeName);
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("format", canonical);
        property.putAll(schema(canonical));
        return property;
    }

    /**
     * The JSON schema of a value of the canonical type: its {@code type} where PostgREST gives one and,
     * for an array, the schema of its elements, nested as deep as the array is. A json element has no
     * type, since a JSON value may be a scalar as well as an object.
     */
    private static Map<String, Object> schema(String canonical) {
        Map<String, Object> schema = new LinkedHashMap<>();
        String type = SqlTypeNames.jsonType(canonical);
        if (type != null) {
            schema.put("type", type);
        }
        if (canonical.endsWith("[]")) {
            schema.put("items", schema(canonical.substring(0, canonical.length() - 2)));
        }
        return schema;
    }
}
