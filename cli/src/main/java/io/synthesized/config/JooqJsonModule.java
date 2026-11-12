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
package io.synthesized.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.function.Function;

/**
 * Jackson module that writes the jOOQ {@link JSON} and {@link JSONB} values a json or jsonb column
 * yields as raw JSON, so that the column appears as nested JSON in the response, as it does in
 * PostgREST, rather than as an escaped string. Spring Boot registers it with the application's
 * {@code ObjectMapper} because it is a bean.
 */
public final class JooqJsonModule extends SimpleModule {
    private static final long serialVersionUID = 1L;

    public JooqJsonModule() {
        super("jooq-json");
        addSerializer(JSON.class, new RawJsonSerializer<>(JSON.class, JSON::data));
        addSerializer(JSONB.class, new RawJsonSerializer<>(JSONB.class, JSONB::data));
    }

    private static final class RawJsonSerializer<T> extends StdSerializer<T> {
        private static final long serialVersionUID = 1L;

        private final transient Function<T, @Nullable String> data;

        RawJsonSerializer(Class<T> type, Function<T, @Nullable String> data) {
            super(type);
            this.data = data;
        }

        @Override
        public void serialize(T value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            String json = data.apply(value);
            if (json == null) {
                generator.writeNull();
            } else {
                generator.writeRawValue(json);
            }
        }
    }
}
