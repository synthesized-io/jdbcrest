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

import java.sql.Types;
import java.util.Locale;
import java.util.Map;

/**
 * Names column types the way PostgREST does in its OpenAPI document, that is with the names Postgres'
 * {@code format_type()} prints, whichever database the column lives in. Each JDBC driver reports its
 * own type names ({@code int4}, {@code INTEGER}, {@code BINARY VARYING}, {@code VARBINARY}), so a name
 * the driver reports is looked up first and the JDBC type code decides when the name is unknown.
 */
public final class SqlTypeNames {
    private static final String TIMESTAMP = "timestamp without time zone";
    private static final String TIMESTAMPTZ = "timestamp with time zone";
    private static final String TIME = "time without time zone";
    private static final String TIMETZ = "time with time zone";
    private static final String VARCHAR = "character varying";
    private static final String DOUBLE = "double precision";
    private static final String ARRAY_SUFFIX = " array";

    private static final Map<String, String> BY_DRIVER_NAME = Map.ofEntries(
            Map.entry("int2", "smallint"),
            Map.entry("smallint", "smallint"),
            Map.entry("tinyint", "smallint"),
            Map.entry("int4", "integer"),
            Map.entry("int", "integer"),
            Map.entry("integer", "integer"),
            Map.entry("serial", "integer"),
            Map.entry("int8", "bigint"),
            Map.entry("bigint", "bigint"),
            Map.entry("bigserial", "bigint"),
            Map.entry("float4", "real"),
            Map.entry("real", "real"),
            Map.entry("float8", DOUBLE),
            Map.entry("double precision", DOUBLE),
            Map.entry("double", DOUBLE),
            Map.entry("float", DOUBLE),
            Map.entry("numeric", "numeric"),
            Map.entry("decimal", "numeric"),
            Map.entry("bool", "boolean"),
            Map.entry("boolean", "boolean"),
            Map.entry("text", "text"),
            Map.entry("clob", "text"),
            Map.entry("nclob", "text"),
            Map.entry("varchar", VARCHAR),
            Map.entry("nvarchar", VARCHAR),
            Map.entry("character varying", VARCHAR),
            Map.entry("bpchar", "character"),
            Map.entry("char", "character"),
            Map.entry("nchar", "character"),
            Map.entry("character", "character"),
            Map.entry("date", "date"),
            Map.entry("timestamp", TIMESTAMP),
            Map.entry("seconddate", TIMESTAMP),
            Map.entry(TIMESTAMP, TIMESTAMP),
            Map.entry("timestamptz", TIMESTAMPTZ),
            Map.entry(TIMESTAMPTZ, TIMESTAMPTZ),
            Map.entry("time", TIME),
            Map.entry(TIME, TIME),
            Map.entry("timetz", TIMETZ),
            Map.entry(TIMETZ, TIMETZ),
            Map.entry("bytea", "bytea"),
            Map.entry("binary", "bytea"),
            Map.entry("varbinary", "bytea"),
            Map.entry("binary varying", "bytea"),
            Map.entry("blob", "bytea"),
            Map.entry("json", "json"),
            Map.entry("jsonb", "jsonb"),
            Map.entry("uuid", "uuid"),
            Map.entry("xml", "xml"),
            Map.entry("inet", "inet"),
            Map.entry("interval", "interval"));

    private SqlTypeNames() {
    }

    /**
     * The canonical name of a column type.
     *
     * @param jdbcType the {@link Types} code the driver reports
     * @param driverTypeName the type name the driver reports, if any
     */
    public static String canonical(int jdbcType, @Nullable String driverTypeName) {
        String name = driverTypeName == null ? "" : driverTypeName.toLowerCase(Locale.ROOT).trim();
        if (name.startsWith("_")) {
            return canonical(Types.OTHER, name.substring(1)) + "[]";
        }
        if (name.endsWith(ARRAY_SUFFIX)) {
            return canonical(Types.OTHER, name.substring(0, name.length() - ARRAY_SUFFIX.length())) + "[]";
        }
        if (name.startsWith("interval")) {
            return "interval";
        }
        String known = BY_DRIVER_NAME.get(name);
        if (known != null) {
            return known;
        }
        return switch (jdbcType) {
            case Types.TINYINT, Types.SMALLINT -> "smallint";
            case Types.INTEGER -> "integer";
            case Types.BIGINT -> "bigint";
            case Types.REAL -> "real";
            case Types.FLOAT, Types.DOUBLE -> DOUBLE;
            case Types.NUMERIC, Types.DECIMAL -> "numeric";
            case Types.BIT, Types.BOOLEAN -> "boolean";
            case Types.CHAR, Types.NCHAR -> "character";
            case Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR -> VARCHAR;
            case Types.CLOB, Types.NCLOB -> "text";
            case Types.DATE -> "date";
            case Types.TIME -> TIME;
            case Types.TIME_WITH_TIMEZONE -> TIMETZ;
            case Types.TIMESTAMP -> TIMESTAMP;
            case Types.TIMESTAMP_WITH_TIMEZONE -> TIMESTAMPTZ;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "bytea";
            case Types.SQLXML -> "xml";
            case Types.ARRAY -> "text[]";
            default -> name.isEmpty() ? "text" : name;
        };
    }

    /** The JSON schema {@code type} PostgREST gives a column of the canonical type, or null for json. */
    public static @Nullable String jsonType(String canonical) {
        if (canonical.endsWith("[]")) {
            return "array";
        }
        return switch (canonical) {
            case "smallint", "integer", "bigint" -> "integer";
            case "numeric", "real", DOUBLE -> "number";
            case "boolean" -> "boolean";
            case "json", "jsonb" -> null;
            default -> "string";
        };
    }
}
