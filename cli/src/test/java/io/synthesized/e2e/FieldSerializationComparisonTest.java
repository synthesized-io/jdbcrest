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
package io.synthesized.e2e;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.nullValue;

/**
 * How each column type of the {@code type_samples} table is rendered in the JSON body, run against
 * PostgREST and against JDBCRest on Postgres, H2 and (with the pro jOOQ edition) SAP HANA.
 *
 * <p>Reading a row through plain JDBC and handing it to a JSON serialiser silently changes values:
 * dates become epoch numbers, driver wrappers become nested objects, fractions and offsets are lost,
 * arrays and XML fail to serialise. PostgREST is the reference: it returns every value in the text
 * form Postgres itself prints (ISO 8601 for dates and times, exact numerics, nested JSON for
 * json/jsonb, JSON arrays for arrays), so the expectations below describe that form. The table holds
 * one column per type that is prone to such changes, one row with a value that exercises precision,
 * offset or scale, and one row of NULLs. Each test selects only the column under test so that a
 * column one backend cannot serialise at all does not mask the result for the other types.
 *
 * <p>Not every backend has every type. A column a database cannot store with the same value is left
 * out of its {@code type_samples} table (see {@code db/inith2.sql} and {@code db/inithana.sql}) and
 * the test for it is skipped by assumption, keyed on the invocation display name in {@link TestInfo}.
 */
@ExtendWith(ComparisonTestExtension.class)
public class FieldSerializationComparisonTest {

    private static final String TABLE = "type_samples";

    /** Columns absent from a backend's table, with the reason, keyed by the invocation display name. */
    private static final Map<String, Map<String, String>> MISSING_COLUMNS = Map.of(
            "jooq_h2", Map.of(
                    "c_jsonb", "H2 has a single JSON type, covered by c_json",
                    "c_inet", "H2 has no network address type",
                    "c_interval_hours", "H2 folds the hours of a DAY TO SECOND interval into days",
                    "c_interval_mixed", "H2 keeps a single sign for the whole interval",
                    "c_time_array", "H2 hands out the elements of a TIME array as java.sql.Time, without the fraction",
                    "c_xml", "H2 has no XML type"),
            "jooq_hana", Map.ofEntries(
                    Map.entry("c_json", "SAP HANA has no JSON column type"),
                    Map.entry("c_jsonb", "SAP HANA has no JSON column type"),
                    Map.entry("c_inet", "SAP HANA has no network address type"),
                    Map.entry("c_timestamptz", "SAP HANA has no time zone aware timestamp"),
                    Map.entry("c_time", "SAP HANA TIME has no fractional seconds"),
                    Map.entry("c_timetz", "SAP HANA has no time zone aware time"),
                    Map.entry("c_interval", "SAP HANA has no interval type"),
                    Map.entry("c_interval_hours", "SAP HANA has no interval type"),
                    Map.entry("c_interval_mixed", "SAP HANA has no interval type"),
                    Map.entry("c_int_array", "SAP HANA array columns are not readable through jOOQ"),
                    Map.entry("c_text_array", "SAP HANA array columns are not readable through jOOQ"),
                    Map.entry("c_time_array", "SAP HANA array columns are not readable through jOOQ"),
                    Map.entry("c_xml", "SAP HANA has no XML type"),
                    Map.entry("c_uuid", "SAP HANA has no UUID type")));

    /** The invocations backed by Postgres, for what only Postgres has. */
    private static final Set<String> POSTGRES_BACKENDS = Set.of("postgrest", "jooq_postgres");

    private static void assumeColumnExists(TestInfo info, String column) {
        String backend = info.getDisplayName();
        String reason = MISSING_COLUMNS.getOrDefault(backend, Map.of()).get(column);
        Assumptions.assumeTrue(reason == null, () -> backend + ": column " + column + " not created, " + reason);
    }

    /** Fetches column {@code column} of row 1 and returns the validatable response. */
    private static ValidatableResponse column(RequestSpecification request, TestInfo info, String column) {
        assumeColumnExists(info, column);
        return request
                .when()
                .get(TABLE + "?select=" + column + "&id=eq.1")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("size()", equalTo(1));
    }

    /** Raw JSON text of column {@code column} of row 1, for assertions that depend on the number syntax. */
    private static String rawColumn(RequestSpecification request, TestInfo info, String column) {
        return column(request, info, column).extract().asString();
    }

    // --- Row level -----------------------------------------------------------------------------

    @TestTemplate
    void wholeRowIsReturned(RequestSpecification request) {
        request
                .when()
                .get(TABLE + "?id=eq.1")
                .then()
                .log().body()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(1));
    }

    @TestTemplate
    void columnsKeepTableOrder(RequestSpecification request) {
        String body = request.when().get(TABLE + "?select=id,c_date,c_numeric,c_bigint,c_bool&id=eq.1")
                .then().log().ifValidationFails().statusCode(200).extract().asString();
        List<String> expectedOrder = List.of("\"id\"", "\"c_date\"", "\"c_numeric\"", "\"c_bigint\"", "\"c_bool\"");
        int previous = -1;
        for (String key : expectedOrder) {
            int position = body.indexOf(key);
            assertThat("column " + key + " present in " + body, position, Matchers.greaterThanOrEqualTo(0));
            assertThat("column " + key + " keeps table order in " + body, position, Matchers.greaterThan(previous));
            previous = position;
        }
    }

    @TestTemplate
    void nullsAreJsonNull(RequestSpecification request) {
        request
                .when()
                .get(TABLE + "?select=c_date,c_timestamp,c_bytea,c_numeric&id=eq.2")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].c_date", nullValue())
                .body("[0].c_timestamp", nullValue())
                .body("[0].c_bytea", nullValue())
                .body("[0].c_numeric", nullValue());
    }

    // --- Types a driver returns as a wrapper object rather than a plain value -------------------

    @TestTemplate
    void date(RequestSpecification request, TestInfo info) {
        column(request, info, "c_date").body("[0].c_date", equalTo("2024-01-07"));
    }

    @TestTemplate
    void json(RequestSpecification request, TestInfo info) {
        column(request, info, "c_json").body("[0].c_json.tier", equalTo("gold"));
    }

    @TestTemplate
    void jsonb(RequestSpecification request, TestInfo info) {
        column(request, info, "c_jsonb").body("[0].c_jsonb.tier", equalTo("gold"));
    }

    @TestTemplate
    void inet(RequestSpecification request, TestInfo info) {
        column(request, info, "c_inet").body("[0].c_inet", equalTo("192.168.0.1/24"));
    }

    // --- Types where precision, offset or the textual form is easily lost ----------------------

    @TestTemplate
    void timestamp(RequestSpecification request, TestInfo info) {
        column(request, info, "c_timestamp").body("[0].c_timestamp", equalTo("2024-01-07T04:30:57.319412"));
    }

    @TestTemplate
    void timestamptz(RequestSpecification request, TestInfo info) {
        // Stored as 04:30:57.319412+02; the session time zone of the database containers is UTC.
        column(request, info, "c_timestamptz")
                .body("[0].c_timestamptz", equalTo("2024-01-07T02:30:57.319412+00:00"));
    }

    @TestTemplate
    void time(RequestSpecification request, TestInfo info) {
        column(request, info, "c_time").body("[0].c_time", equalTo("04:30:57.5"));
    }

    @TestTemplate
    void timetz(RequestSpecification request, TestInfo info) {
        column(request, info, "c_timetz").body("[0].c_timetz", equalTo("04:30:57.5+02"));
    }

    @TestTemplate
    void interval(RequestSpecification request, TestInfo info) {
        column(request, info, "c_interval").body("[0].c_interval", equalTo("1 day 02:03:04"));
    }

    @TestTemplate
    void intervalKeepsHoursBeyondADay(RequestSpecification request, TestInfo info) {
        // Postgres does not fold 25 hours into a day; a typed interval binding would.
        column(request, info, "c_interval_hours").body("[0].c_interval_hours", equalTo("25:00:00"));
    }

    @TestTemplate
    void intervalKeepsTheSignOfEachPart(RequestSpecification request, TestInfo info) {
        column(request, info, "c_interval_mixed").body("[0].c_interval_mixed", equalTo("1 day -02:00:00"));
    }

    @TestTemplate
    void bytea(RequestSpecification request, TestInfo info) {
        column(request, info, "c_bytea").body("[0].c_bytea", equalTo("\\xdeadbeef"));
    }

    // --- Numbers that must stay exact, and types that may fail to serialise at all -------------

    @TestTemplate
    void numericKeepsScale(RequestSpecification request, TestInfo info) {
        assertThat(rawColumn(request, info, "c_numeric"),
                matchesRegex("(?s).*\"c_numeric\"\\s*:\\s*1\\.50\\s*[,}].*"));
    }

    @TestTemplate
    void bigintAbove2Pow53IsExact(RequestSpecification request, TestInfo info) {
        assertThat(rawColumn(request, info, "c_bigint"), containsString("9007199254740993"));
    }

    @TestTemplate
    void intArray(RequestSpecification request, TestInfo info) {
        column(request, info, "c_int_array").body("[0].c_int_array", Matchers.contains(1, 2, 3));
    }

    @TestTemplate
    void textArray(RequestSpecification request, TestInfo info) {
        column(request, info, "c_text_array").body("[0].c_text_array", Matchers.contains("a", "b"));
    }

    @TestTemplate
    void timeArrayKeepsTheFraction(RequestSpecification request, TestInfo info) {
        // The elements of a time array reach the driver as java.sql.Time, which has no fraction.
        column(request, info, "c_time_array")
                .body("[0].c_time_array", Matchers.contains("04:30:57.5", "04:30:58"));
    }

    @TestTemplate
    void xml(RequestSpecification request, TestInfo info) {
        column(request, info, "c_xml").body("[0].c_xml", equalTo("<a>1</a>"));
    }

    // --- Control group: types that serialise correctly without any special handling ------------

    @TestTemplate
    void uuid(RequestSpecification request, TestInfo info) {
        column(request, info, "c_uuid").body("[0].c_uuid", equalTo("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
    }

    @TestTemplate
    void bool(RequestSpecification request, TestInfo info) {
        column(request, info, "c_bool").body("[0].c_bool", equalTo(true));
    }

    @TestTemplate
    void float8(RequestSpecification request, TestInfo info) {
        column(request, info, "c_float8").body("[0].c_float8", equalTo(1.5F));
    }

    // --- Column type metadata -------------------------------------------------------------------

    /**
     * PostgREST has no per-response type information. The one standard place that exposes column
     * types is the OpenAPI document served at the API root, whose {@code definitions.<table>}
     * entry lists every column with its Postgres type in {@code format}.
     */
    @TestTemplate
    void openApiRootDescribesColumnTypes(RequestSpecification request) {
        request
                .when()
                .get("/")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("definitions.type_samples.properties.c_date.format", equalTo("date"))
                .body("definitions.type_samples.properties.c_date.type", equalTo("string"))
                .body("definitions.type_samples.properties.c_timestamp.format",
                        equalTo("timestamp without time zone"))
                .body("definitions.type_samples.properties.c_numeric.format", equalTo("numeric"))
                .body("definitions.type_samples.properties.c_numeric.type", equalTo("number"))
                .body("definitions.type_samples.properties.c_bigint.format", equalTo("bigint"))
                .body("definitions.type_samples.properties.c_bigint.type", equalTo("integer"))
                .body("definitions.type_samples.properties.c_bytea.format", equalTo("bytea"));
    }

    /** Every relation the data endpoint can read is described, not only plain tables and views. */
    @TestTemplate
    void openApiRootListsPartitionedTablesAndMaterializedViews(RequestSpecification request, TestInfo info) {
        Assumptions.assumeTrue(POSTGRES_BACKENDS.contains(info.getDisplayName()),
                () -> info.getDisplayName() + ": partitioned tables and materialized views exist in Postgres only");
        request
                .when()
                .get("/")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("paths", Matchers.hasKey("/events"))
                .body("definitions.events.properties.happened_on.format", equalTo("date"))
                .body("paths", Matchers.hasKey("/product_count"))
                .body("definitions.product_count.properties.n.format", equalTo("bigint"));
    }
}
