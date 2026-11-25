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
import io.synthesized.e2e.comparison.JdbcrestBackends;
import org.atpfivt.comparison.api.ComparisonTest;
import org.atpfivt.comparison.api.Skip;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.List;

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
 * the corresponding test declares which backends to skip with {@link Skip}.
 */
@ComparisonTest(source = JdbcrestBackends.class)
public class FieldSerializationComparisonTest {

    private static final String TABLE = "type_samples";

    /** Fetches column {@code column} of row 1 and returns the validatable response. */
    private static ValidatableResponse column(RequestSpecification request, String column) {
        return request
                .when()
                .get(TABLE + "?select=" + column + "&id=eq.1")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("size()", equalTo(1));
    }

    /** Raw JSON text of column {@code column} of row 1, for assertions that depend on the number syntax. */
    private static String rawColumn(RequestSpecification request, String column) {
        return column(request, column).extract().asString();
    }

    // --- Row level -----------------------------------------------------------------------------

    @Test
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

    @Test
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

    @Test
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

    @Test
    void date(RequestSpecification request) {
        column(request, "c_date").body("[0].c_date", equalTo("2024-01-07"));
    }

    // SAP HANA has no JSON column type.
    @Test
    @Skip("SAP_HANA")
    void json(RequestSpecification request) {
        column(request, "c_json").body("[0].c_json.tier", equalTo("gold"));
    }

    // SAP HANA has no JSON type. H2 has a single JSON type, covered by json().
    @Test
    @Skip({"SAP_HANA", "H2"})
    void jsonb(RequestSpecification request) {
        column(request, "c_jsonb").body("[0].c_jsonb.tier", equalTo("gold"));
    }

    // Neither SAP HANA nor H2 has a network address type.
    @Test
    @Skip({"SAP_HANA", "H2"})
    void inet(RequestSpecification request) {
        column(request, "c_inet").body("[0].c_inet", equalTo("192.168.0.1/24"));
    }

    // --- Types where precision, offset or the textual form is easily lost ----------------------

    @Test
    void timestamp(RequestSpecification request) {
        column(request, "c_timestamp").body("[0].c_timestamp", equalTo("2024-01-07T04:30:57.319412"));
    }

    // SAP HANA has no time zone aware timestamp.
    @Test
    @Skip("SAP_HANA")
    void timestamptz(RequestSpecification request) {
        // Stored as 04:30:57.319412+02; the session time zone of the database containers is UTC.
        column(request, "c_timestamptz")
                .body("[0].c_timestamptz", equalTo("2024-01-07T02:30:57.319412+00:00"));
    }

    // SAP HANA TIME has no fractional seconds.
    @Test
    @Skip("SAP_HANA")
    void time(RequestSpecification request) {
        column(request, "c_time").body("[0].c_time", equalTo("04:30:57.5"));
    }

    // SAP HANA has no time zone aware time.
    @Test
    @Skip("SAP_HANA")
    void timetz(RequestSpecification request) {
        column(request, "c_timetz").body("[0].c_timetz", equalTo("04:30:57.5+02"));
    }

    // SAP HANA has no interval type.
    @Test
    @Skip("SAP_HANA")
    void interval(RequestSpecification request) {
        column(request, "c_interval").body("[0].c_interval", equalTo("1 day 02:03:04"));
    }

    // SAP HANA has no interval type. H2 folds hours into days.
    @Test
    @Skip({"SAP_HANA", "H2"})
    void intervalKeepsHoursBeyondADay(RequestSpecification request) {
        // Postgres does not fold 25 hours into a day; a typed interval binding would.
        column(request, "c_interval_hours").body("[0].c_interval_hours", equalTo("25:00:00"));
    }

    // SAP HANA has no interval type. H2 keeps one sign for the whole interval.
    @Test
    @Skip({"SAP_HANA", "H2"})
    void intervalKeepsTheSignOfEachPart(RequestSpecification request) {
        column(request, "c_interval_mixed").body("[0].c_interval_mixed", equalTo("1 day -02:00:00"));
    }

    @Test
    void bytea(RequestSpecification request) {
        column(request, "c_bytea").body("[0].c_bytea", equalTo("\\xdeadbeef"));
    }

    // --- Numbers that must stay exact, and types that may fail to serialise at all -------------

    @Test
    void numericKeepsScale(RequestSpecification request) {
        assertThat(rawColumn(request, "c_numeric"),
                matchesRegex("(?s).*\"c_numeric\"\\s*:\\s*1\\.50\\s*[,}].*"));
    }

    @Test
    void bigintAbove2Pow53IsExact(RequestSpecification request) {
        assertThat(rawColumn(request, "c_bigint"), containsString("9007199254740993"));
    }

    // jOOQ cannot read array columns on SAP HANA.
    @Test
    @Skip("SAP_HANA")
    void intArray(RequestSpecification request) {
        column(request, "c_int_array").body("[0].c_int_array", Matchers.contains(1, 2, 3));
    }

    // jOOQ cannot read array columns on SAP HANA.
    @Test
    @Skip("SAP_HANA")
    void textArray(RequestSpecification request) {
        column(request, "c_text_array").body("[0].c_text_array", Matchers.contains("a", "b"));
    }

    // jOOQ cannot read HANA arrays. H2 returns TIME array elements without fractions.
    @Test
    @Skip({"SAP_HANA", "H2"})
    void timeArrayKeepsTheFraction(RequestSpecification request) {
        // The elements of a time array reach the driver as java.sql.Time, which has no fraction.
        column(request, "c_time_array")
                .body("[0].c_time_array", Matchers.contains("04:30:57.5", "04:30:58"));
    }

    // Neither SAP HANA nor H2 has an XML type.
    @Test
    @Skip({"SAP_HANA", "H2"})
    void xml(RequestSpecification request) {
        column(request, "c_xml").body("[0].c_xml", equalTo("<a>1</a>"));
    }

    // --- Control group: types that serialise correctly without any special handling ------------

    // SAP HANA has no UUID type.
    @Test
    @Skip("SAP_HANA")
    void uuid(RequestSpecification request) {
        column(request, "c_uuid").body("[0].c_uuid", equalTo("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
    }

    @Test
    void bool(RequestSpecification request) {
        column(request, "c_bool").body("[0].c_bool", equalTo(true));
    }

    @Test
    void float8(RequestSpecification request) {
        column(request, "c_float8").body("[0].c_float8", equalTo(1.5F));
    }

    // --- Column type metadata -------------------------------------------------------------------

    /**
     * PostgREST has no per-response type information. The one standard place that exposes column
     * types is the OpenAPI document served at the API root, whose {@code definitions.<table>}
     * entry lists every column with its Postgres type in {@code format}.
     */
    @Test
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
    // The test relations are available only on PostgreSQL.
    @Test
    @Skip({"SAP_HANA", "H2"})
    void openApiRootListsPartitionedTablesAndMaterializedViews(RequestSpecification request) {
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
