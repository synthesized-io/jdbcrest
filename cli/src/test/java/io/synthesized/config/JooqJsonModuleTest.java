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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The jOOQ JSON values a json or jsonb column yields are written as nested JSON, not as strings. */
public class JooqJsonModuleTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JooqJsonModule());

    @Test
    void jsonIsWrittenRaw() throws JsonProcessingException {
        assertThat(mapper.writeValueAsString(Map.of("c", JSON.valueOf("{\"tier\":\"gold\"}"))))
                .isEqualTo("{\"c\":{\"tier\":\"gold\"}}");
    }

    @Test
    void jsonbIsWrittenRaw() throws JsonProcessingException {
        assertThat(mapper.writeValueAsString(Map.of("c", JSONB.valueOf("[1, 2]")))).isEqualTo("{\"c\":[1, 2]}");
    }

    @Test
    void jsonWithoutDataIsNull() throws JsonProcessingException {
        assertThat(mapper.writeValueAsString(Map.of("c", JSON.valueOf(null)))).isEqualTo("{\"c\":null}");
        assertThat(mapper.writeValueAsString(Map.of("c", JSONB.valueOf(null)))).isEqualTo("{\"c\":null}");
    }
}
