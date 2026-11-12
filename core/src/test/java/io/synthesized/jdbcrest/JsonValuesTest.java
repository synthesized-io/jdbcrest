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

import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.XML;
import org.jooq.types.DayToSecond;
import org.jooq.types.YearToMonth;
import org.jooq.types.YearToSecond;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialClob;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every value class {@link JsonValues} knows, rendered as Postgres prints it. The end-to-end comparison
 * tests pin the rendering against PostgREST on real databases; this test covers each branch, including
 * the value classes no driver of those databases happens to return.
 */
public class JsonValuesTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2024, 1, 7, 4, 30, 57, 319_412_000);

    @Test
    void nullStaysNull() throws SQLException {
        assertThat(JsonValues.toJsonValue(null)).isNull();
    }

    @Test
    void plainJsonValuesPassThroughUnchanged() throws SQLException {
        BigDecimal scaled = new BigDecimal("1.50");
        JSON json = JSON.valueOf("{\"tier\":\"gold\"}");
        JSONB jsonb = JSONB.valueOf("{\"tier\":\"gold\"}");
        assertThat(JsonValues.toJsonValue("text")).isEqualTo("text");
        assertThat(JsonValues.toJsonValue(true)).isEqualTo(true);
        assertThat(JsonValues.toJsonValue(9007199254740993L)).isEqualTo(9007199254740993L);
        assertThat(JsonValues.toJsonValue(scaled)).isSameAs(scaled);
        assertThat(JsonValues.toJsonValue(json)).isSameAs(json);
        assertThat(JsonValues.toJsonValue(jsonb)).isSameAs(jsonb);
    }

    @Test
    void dateIsIsoText() throws SQLException {
        assertThat(JsonValues.toJsonValue(LocalDate.of(2024, 1, 7))).isEqualTo("2024-01-07");
        assertThat(JsonValues.toJsonValue(java.sql.Date.valueOf("2024-01-07"))).isEqualTo("2024-01-07");
    }

    @Test
    void timestampUsesTSeparatorAndTrimmedFraction() throws SQLException {
        assertThat(JsonValues.toJsonValue(TIMESTAMP)).isEqualTo("2024-01-07T04:30:57.319412");
        assertThat(JsonValues.toJsonValue(TIMESTAMP.withNano(0))).isEqualTo("2024-01-07T04:30:57");
        assertThat(JsonValues.toJsonValue(TIMESTAMP.withNano(500_000_000))).isEqualTo("2024-01-07T04:30:57.5");
        assertThat(JsonValues.toJsonValue(java.sql.Timestamp.valueOf(TIMESTAMP)))
                .isEqualTo("2024-01-07T04:30:57.319412");
    }

    @Test
    void timestampWithTimeZoneIsMovedToUtc() throws SQLException {
        OffsetDateTime plusTwo = TIMESTAMP.atOffset(ZoneOffset.ofHours(2));
        assertThat(JsonValues.toJsonValue(plusTwo)).isEqualTo("2024-01-07T02:30:57.319412+00:00");
        Instant instant = plusTwo.toInstant();
        assertThat(JsonValues.toJsonValue(instant)).isEqualTo("2024-01-07T02:30:57.319412+00:00");
    }

    @Test
    void timeKeepsOnlySignificantFractionDigits() throws SQLException {
        assertThat(JsonValues.toJsonValue(LocalTime.of(4, 30, 57, 500_000_000))).isEqualTo("04:30:57.5");
        assertThat(JsonValues.toJsonValue(LocalTime.of(4, 30, 57))).isEqualTo("04:30:57");
        assertThat(JsonValues.toJsonValue(LocalTime.of(4, 30, 57, 1_000))).isEqualTo("04:30:57.000001");
        // Below a microsecond is beyond what Postgres stores, so it is dropped.
        assertThat(JsonValues.toJsonValue(LocalTime.of(4, 30, 57, 999))).isEqualTo("04:30:57");
        assertThat(JsonValues.toJsonValue(java.sql.Time.valueOf("04:30:57"))).isEqualTo("04:30:57");
    }

    @Test
    void timeWithTimeZoneKeepsItsOffsetInPostgresForm() throws SQLException {
        LocalTime time = LocalTime.of(4, 30, 57, 500_000_000);
        assertThat(JsonValues.toJsonValue(time.atOffset(ZoneOffset.ofHours(2)))).isEqualTo("04:30:57.5+02");
        assertThat(JsonValues.toJsonValue(time.atOffset(ZoneOffset.ofHours(-3)))).isEqualTo("04:30:57.5-03");
        assertThat(JsonValues.toJsonValue(time.atOffset(ZoneOffset.UTC))).isEqualTo("04:30:57.5+00");
        assertThat(JsonValues.toJsonValue(time.atOffset(ZoneOffset.ofHoursMinutes(5, 30))))
                .isEqualTo("04:30:57.5+05:30");
        assertThat(JsonValues.toJsonValue(time.atOffset(ZoneOffset.ofHoursMinutesSeconds(1, 2, 3))))
                .isEqualTo("04:30:57.5+01:02:03");
        assertThat(JsonValues.toJsonValue(time.atOffset(ZoneOffset.ofHoursMinutesSeconds(0, 0, 30))))
                .isEqualTo("04:30:57.5+00:00:30");
    }

    @Test
    void binaryIsHexWithBackslashXPrefix() throws SQLException {
        assertThat(JsonValues.toJsonValue(new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef}))
                .isEqualTo("\\xdeadbeef");
        assertThat(JsonValues.toJsonValue(new byte[0])).isEqualTo("\\x");
    }

    @Test
    void uuidAndXmlAreText() throws SQLException {
        UUID uuid = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        assertThat(JsonValues.toJsonValue(uuid)).isEqualTo("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        assertThat(JsonValues.toJsonValue(XML.valueOf("<a>1</a>"))).isEqualTo("<a>1</a>");
    }

    @Test
    void jdbcLargeObjectsAreReadWhileOpen() throws SQLException {
        SQLXML sqlxml = proxy(SQLXML.class, "getString", "<a>2</a>");
        assertThat(JsonValues.toJsonValue(sqlxml)).isEqualTo("<a>2</a>");
        assertThat(JsonValues.toJsonValue(new SerialClob("long text".toCharArray()))).isEqualTo("long text");
    }

    @Test
    void dayToSecondIntervalInPostgresStyle() throws SQLException {
        assertThat(JsonValues.toJsonValue(new DayToSecond(1, 2, 3, 4))).isEqualTo("1 day 02:03:04");
        assertThat(JsonValues.toJsonValue(new DayToSecond(2, 0, 0, 0))).isEqualTo("2 days");
        assertThat(JsonValues.toJsonValue(new DayToSecond(0, 0, 0, 0, 500_000_000))).isEqualTo("00:00:00.5");
        assertThat(JsonValues.toJsonValue(new DayToSecond(1, 0, 0, 0, 500_000_000))).isEqualTo("1 day 00:00:00.5");
        assertThat(JsonValues.toJsonValue(new DayToSecond(1, 0, 0, 7))).isEqualTo("1 day 00:00:07");
        assertThat(JsonValues.toJsonValue(new DayToSecond(0, 0, 0, 0))).isEqualTo("00:00:00");
        assertThat(JsonValues.toJsonValue(new DayToSecond(0, 0, 5, 0))).isEqualTo("00:05:00");
        assertThat(JsonValues.toJsonValue(new DayToSecond(1, 2, 0, 0).neg())).isEqualTo("-1 days -02:00:00");
        assertThat(JsonValues.toJsonValue(new DayToSecond(1, 0, 0, 0).neg())).isEqualTo("-1 days");
    }

    @Test
    void yearToMonthIntervalInPostgresStyle() throws SQLException {
        assertThat(JsonValues.toJsonValue(new YearToMonth(1, 2))).isEqualTo("1 year 2 mons");
        assertThat(JsonValues.toJsonValue(new YearToMonth(2, 1))).isEqualTo("2 years 1 mon");
        assertThat(JsonValues.toJsonValue(new YearToMonth(0, 3))).isEqualTo("3 mons");
        assertThat(JsonValues.toJsonValue(new YearToMonth(1, 1).neg())).isEqualTo("-1 years -1 mons");
        assertThat(JsonValues.toJsonValue(new YearToMonth(0, 0))).isEqualTo("00:00:00");
    }

    @Test
    void yearToSecondIntervalCombinesBothParts() throws SQLException {
        YearToSecond full = new YearToSecond(new YearToMonth(1, 2), new DayToSecond(3, 4, 5, 6));
        assertThat(JsonValues.toJsonValue(full)).isEqualTo("1 year 2 mons 3 days 04:05:06");
        YearToSecond daysOnly = new YearToSecond(new YearToMonth(0, 0), new DayToSecond(3, 0, 0, 0));
        assertThat(JsonValues.toJsonValue(daysOnly)).isEqualTo("3 days");
        YearToSecond zero = new YearToSecond(new YearToMonth(0, 0), new DayToSecond(0, 0, 0, 0));
        assertThat(JsonValues.toJsonValue(zero)).isEqualTo("00:00:00");
    }

    @Test
    void arraysBecomeListsOfRenderedElements() throws SQLException {
        assertThat(JsonValues.toJsonValue(new Integer[] {1, 2, 3})).isEqualTo(List.of(1, 2, 3));
        assertThat(JsonValues.toJsonValue(new Object[] {LocalDate.of(2024, 1, 7), null}))
                .isEqualTo(Arrays.asList("2024-01-07", null));
        assertThat(JsonValues.toJsonValue(new int[] {1, 2, 3})).isEqualTo(List.of(1, 2, 3));
        assertThat(JsonValues.toJsonValue(List.of("a", "b"))).isEqualTo(List.of("a", "b"));
        Array jdbcArray = proxy(Array.class, "getArray", new String[] {"a", "b"});
        assertThat(JsonValues.toJsonValue(jdbcArray)).isEqualTo(List.of("a", "b"));
    }

    @Test
    void unknownDriverWrappersUseTheirTextForm() throws SQLException {
        Object inet = new Object() {
            @Override
            public String toString() {
                return "192.168.0.1/24";
            }
        };
        assertThat(JsonValues.toJsonValue(inet)).isEqualTo("192.168.0.1/24");
    }

    /** A stub of a JDBC interface whose method {@code method} returns {@code result}. */
    private static <T> T proxy(Class<T> type, String method, Object result) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, invoked, args) -> {
                    if (invoked.getName().equals(method)) {
                        return result;
                    }
                    throw new UnsupportedOperationException(invoked.getName());
                }));
    }
}
