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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canonical type names {@link SqlTypeNames} gives the spellings of the Postgres, H2 and SAP HANA
 * drivers, and the fallback on the JDBC type code for names it does not know.
 */
public class SqlTypeNamesTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "int2, smallint",
            "int4, integer",
            "INTEGER, integer",
            "int8, bigint",
            "BIGINT, bigint",
            "float4, real",
            "float8, double precision",
            "DOUBLE, double precision",
            "DOUBLE PRECISION, double precision",
            "numeric, numeric",
            "DECIMAL, numeric",
            "bool, boolean",
            "BOOLEAN, boolean",
            "text, text",
            "NCLOB, text",
            "varchar, character varying",
            "NVARCHAR, character varying",
            "CHARACTER VARYING, character varying",
            "bpchar, character",
            "date, date",
            "timestamp, timestamp without time zone",
            "SECONDDATE, timestamp without time zone",
            "timestamptz, timestamp with time zone",
            "TIMESTAMP WITH TIME ZONE, timestamp with time zone",
            "time, time without time zone",
            "timetz, time with time zone",
            "TIME WITH TIME ZONE, time with time zone",
            "bytea, bytea",
            "VARBINARY, bytea",
            "BINARY VARYING, bytea",
            "json, json",
            "jsonb, jsonb",
            "uuid, uuid",
            "xml, xml",
            "inet, inet",
            "interval, interval",
            "INTERVAL DAY TO SECOND, interval",
            "INTERVAL YEAR TO MONTH, interval",
    })
    void driverNamesMapToPostgresNames(String driverName, String canonical) {
        assertThat(SqlTypeNames.canonical(Types.OTHER, driverName)).isEqualTo(canonical);
    }

    @Test
    void driverNamesAreTrimmedAndCaseInsensitive() {
        assertThat(SqlTypeNames.canonical(Types.OTHER, "  Int4 ")).isEqualTo("integer");
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "_int4, integer[]",
            "_text, text[]",
            "_timestamptz, timestamp with time zone[]",
            "__status, _status[]",
            "INTEGER ARRAY, integer[]",
            "CHARACTER VARYING ARRAY, character varying[]",
            "INTEGER ARRAY ARRAY, integer[][]",
    })
    void arraysInEitherSpelling(String driverName, String canonical) {
        assertThat(SqlTypeNames.canonical(Types.ARRAY, driverName)).isEqualTo(canonical);
    }

    @Test
    void arrayMarkersInTheNameOfAScalarTypeAreKept() {
        // A user defined type may be called _status; only the ARRAY type code makes the underscore a marker.
        // (The Postgres driver reports an enum under the VARCHAR code, so the name falls back on that.)
        assertThat(SqlTypeNames.canonical(Types.VARCHAR, "_status")).isEqualTo("character varying");
        assertThat(SqlTypeNames.canonical(Types.OTHER, "_status")).isEqualTo("_status");
        assertThat(SqlTypeNames.canonical(Types.OTHER, "my array")).isEqualTo("my array");
    }

    @ParameterizedTest(name = "{1} reported as JDBC type {0} is {2}")
    @CsvSource({
            Types.DOUBLE + ", money, money",
            Types.BIGINT + ", oid, oid",
            Types.VARCHAR + ", name, name",
            Types.BIT + ", bit, bit",
            Types.OTHER + ", varbit, bit varying",
            Types.BIT + ", bool, boolean",
    })
    void postgresNamesWinOverTheGenericJdbcCode(int jdbcType, String driverName, String canonical) {
        assertThat(SqlTypeNames.canonical(jdbcType, driverName)).isEqualTo(canonical);
    }

    @ParameterizedTest(name = "JDBC type {0} is {1}")
    @CsvSource({
            Types.TINYINT + ", smallint",
            Types.SMALLINT + ", smallint",
            Types.INTEGER + ", integer",
            Types.BIGINT + ", bigint",
            Types.REAL + ", real",
            Types.FLOAT + ", double precision",
            Types.DOUBLE + ", double precision",
            Types.NUMERIC + ", numeric",
            Types.DECIMAL + ", numeric",
            Types.BIT + ", boolean",
            Types.BOOLEAN + ", boolean",
            Types.CHAR + ", character",
            Types.NCHAR + ", character",
            Types.VARCHAR + ", character varying",
            Types.LONGNVARCHAR + ", character varying",
            Types.CLOB + ", text",
            Types.NCLOB + ", text",
            Types.DATE + ", date",
            Types.TIME + ", time without time zone",
            Types.TIME_WITH_TIMEZONE + ", time with time zone",
            Types.TIMESTAMP + ", timestamp without time zone",
            Types.TIMESTAMP_WITH_TIMEZONE + ", timestamp with time zone",
            Types.BINARY + ", bytea",
            Types.LONGVARBINARY + ", bytea",
            Types.BLOB + ", bytea",
            Types.SQLXML + ", xml",
            Types.ARRAY + ", text[]",
    })
    void unknownNamesFallBackOnTheJdbcTypeCode(int jdbcType, String canonical) {
        assertThat(SqlTypeNames.canonical(jdbcType, "vendor_specific")).isEqualTo(canonical);
    }

    @Test
    void unknownNameAndCodeKeepTheDriverName() {
        assertThat(SqlTypeNames.canonical(Types.OTHER, "GEOMETRY")).isEqualTo("geometry");
    }

    @Test
    void missingNameAndUnknownCodeIsText() {
        assertThat(SqlTypeNames.canonical(Types.OTHER, null)).isEqualTo("text");
        assertThat(SqlTypeNames.canonical(Types.OTHER, "")).isEqualTo("text");
        assertThat(SqlTypeNames.canonical(Types.JAVA_OBJECT, " ")).isEqualTo("text");
    }

    @ParameterizedTest(name = "{0} has JSON type {1}")
    @CsvSource({
            "smallint, integer",
            "integer, integer",
            "bigint, integer",
            "numeric, number",
            "real, number",
            "double precision, number",
            "boolean, boolean",
            "text, string",
            "date, string",
            "bytea, string",
            "uuid, string",
            "integer[], array",
            "jsonb[], array",
    })
    void jsonTypeFollowsPostgrest(String canonical, String jsonType) {
        assertThat(SqlTypeNames.jsonType(canonical)).isEqualTo(jsonType);
    }

    @Test
    void jsonColumnsHaveNoJsonType() {
        assertThat(SqlTypeNames.jsonType("json")).isNull();
        assertThat(SqlTypeNames.jsonType("jsonb")).isNull();
    }
}
