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
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/**
 * Turns the typed values jOOQ reads from a result set into values that serialise to the same JSON
 * PostgREST produces, whichever database they came from.
 *
 * <p>PostgREST hands rows to {@code json_agg}, so every value appears the way Postgres prints it:
 * dates and times in ISO 8601 with trailing zeros of the fraction trimmed, {@code timestamptz} in the
 * session time zone (UTC here) with a {@code +00:00} suffix, {@code timetz} with its own offset,
 * intervals in the {@code postgres} interval style, binary data as {@code \x} followed by hex digits,
 * arrays as JSON arrays and json/jsonb columns as nested JSON. Numbers and booleans stay JSON numbers
 * and booleans; a {@link java.math.BigDecimal} keeps its scale and a {@code bigint} its exact digits.
 *
 * <p>Nested JSON is returned as the jOOQ {@link JSON} and {@link JSONB} values. They hold the JSON
 * text verbatim and must be written to the output as raw JSON, not as a string.
 */
public final class JsonValues {
    private static final int MICROS_PER_NANO = 1000;
    private static final int MICRO_DIGITS = 6;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;

    private JsonValues() {
    }

    /**
     * Converts one column value. Values a driver returns through a still open connection (arrays, XML,
     * character large objects) are read here, so this must be called before the connection is closed.
     */
    public static @Nullable Object toJsonValue(@Nullable Object value) throws SQLException {
        if (value == null) {
            return null;
        } else if (value instanceof YearToSecond interval) {
            // The jOOQ interval types extend Number, so they come before the Number pass-through.
            return formatInterval(interval.getYearToMonth(), interval.getDayToSecond());
        } else if (value instanceof YearToMonth interval) {
            return formatInterval(interval, null);
        } else if (value instanceof DayToSecond interval) {
            return formatInterval(null, interval);
        } else if (value instanceof String || value instanceof Boolean || value instanceof Number
                || value instanceof JSON || value instanceof JSONB) {
            return value;
        } else if (value instanceof LocalDateTime timestamp) {
            return formatTimestamp(timestamp);
        } else if (value instanceof OffsetDateTime timestamp) {
            return formatTimestampWithTimeZone(timestamp);
        } else if (value instanceof Instant instant) {
            return formatTimestampWithTimeZone(instant.atOffset(ZoneOffset.UTC));
        } else if (value instanceof LocalTime time) {
            return formatTime(time);
        } else if (value instanceof OffsetTime time) {
            return formatTime(time.toLocalTime()) + formatOffset(time.getOffset());
        } else if (value instanceof java.sql.Timestamp timestamp) {
            return formatTimestamp(timestamp.toLocalDateTime());
        } else if (value instanceof byte[] bytes) {
            return "\\x" + HexFormat.of().formatHex(bytes);
        } else if (value instanceof XML xml) {
            return xml.data();
        } else if (value instanceof SQLXML xml) {
            return xml.getString();
        } else if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        } else if (value instanceof Array array) {
            return toJsonValue(array.getArray());
        } else if (value instanceof Collection<?> collection) {
            return toJsonList(collection);
        } else if (value.getClass().isArray()) {
            // Object and primitive arrays alike, through reflection.
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(java.lang.reflect.Array.get(value, i));
            }
            return toJsonList(elements);
        } else {
            // Everything else prints its text form: LocalDate, java.sql.Date and java.sql.Time, whose
            // toString() is the ISO form by contract, UUID, and driver wrappers for types jOOQ does not
            // know, such as PGobject for inet.
            return value.toString();
        }
    }

    private static List<@Nullable Object> toJsonList(Collection<?> elements) throws SQLException {
        List<@Nullable Object> list = new ArrayList<>(elements.size());
        for (Object element : elements) {
            list.add(toJsonValue(element));
        }
        return list;
    }

    /** {@code 2024-01-07T04:30:57.319412}, the XSD style Postgres uses inside JSON. */
    static String formatTimestamp(LocalDateTime timestamp) {
        return timestamp.toLocalDate() + "T" + formatTime(timestamp.toLocalTime());
    }

    /** {@code 2024-01-07T02:30:57.319412+00:00}: the instant moved to UTC, as a UTC session prints it. */
    static String formatTimestampWithTimeZone(OffsetDateTime timestamp) {
        return formatTimestamp(timestamp.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()) + "+00:00";
    }

    /** {@code 04:30:57.5}: seconds always shown, the fraction only when non-zero and without trailing zeros. */
    static String formatTime(LocalTime time) {
        return String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond())
                + formatFraction(time.getNano());
    }

    private static String formatFraction(int nanos) {
        int micros = nanos / MICROS_PER_NANO;
        if (micros == 0) {
            return "";
        }
        String digits = String.format("%0" + MICRO_DIGITS + "d", micros);
        int end = digits.length();
        while (digits.charAt(end - 1) == '0') {
            end--;
        }
        return "." + digits.substring(0, end);
    }

    /** {@code +02}, {@code -03:30}: minutes and seconds only when non-zero, as Postgres prints an offset. */
    static String formatOffset(ZoneOffset offset) {
        int total = offset.getTotalSeconds();
        int absolute = Math.abs(total);
        StringBuilder result = new StringBuilder(total < 0 ? "-" : "+");
        result.append(String.format("%02d", absolute / SECONDS_PER_HOUR));
        int minutes = (absolute % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        int seconds = absolute % SECONDS_PER_MINUTE;
        if (minutes != 0 || seconds != 0) {
            result.append(String.format(":%02d", minutes));
        }
        if (seconds != 0) {
            result.append(String.format(":%02d", seconds));
        }
        return result.toString();
    }

    /**
     * The {@code postgres} interval style: {@code 1 year 2 mons 3 days 04:05:06.5}. Zero parts are left out
     * and the time part is shown when it is non-zero or nothing else is.
     */
    static String formatInterval(@Nullable YearToMonth yearToMonth, @Nullable DayToSecond dayToSecond) {
        List<String> parts = new ArrayList<>();
        if (yearToMonth != null) {
            int sign = yearToMonth.getSign();
            addPart(parts, sign * yearToMonth.getYears(), "year", "years");
            addPart(parts, sign * yearToMonth.getMonths(), "mon", "mons");
        }
        if (dayToSecond != null) {
            int sign = dayToSecond.getSign();
            addPart(parts, sign * dayToSecond.getDays(), "day", "days");
            boolean hasTime = dayToSecond.getHours() != 0 || dayToSecond.getMinutes() != 0
                    || dayToSecond.getSeconds() != 0 || dayToSecond.getNano() != 0;
            if (hasTime || parts.isEmpty()) {
                parts.add((sign < 0 && hasTime ? "-" : "")
                        + String.format("%02d:%02d:%02d", dayToSecond.getHours(), dayToSecond.getMinutes(),
                        dayToSecond.getSeconds())
                        + formatFraction(dayToSecond.getNano()));
            }
        } else if (parts.isEmpty()) {
            parts.add("00:00:00");
        }
        return String.join(" ", parts);
    }

    private static void addPart(List<String> parts, int amount, String singular, String plural) {
        if (amount != 0) {
            parts.add(amount + " " + (amount == 1 ? singular : plural));
        }
    }
}
