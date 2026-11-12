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
package io.synthesized.controller;

import io.synthesized.jdbcrest.DataRetrieval;
import io.synthesized.jdbcrest.SchemaRetrieval;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
public class DataRetrieveControllerImpl implements DataRetrieveController {
    private final DataRetrieval dataRetrieval;
    private final SchemaRetrieval schemaRetrieval;

    public DataRetrieveControllerImpl(DataRetrieval dataRetrieval, SchemaRetrieval schemaRetrieval) {
        this.dataRetrieval = dataRetrieval;
        this.schemaRetrieval = schemaRetrieval;
    }

    @Override
    @GetMapping(value = {"/api/data/{schema}", "/api/data/{schema}/"}, produces = "application/json")
    public Map<String, Object> openApi(@PathVariable(name = "schema") String schema) {
        try {
            return schemaRetrieval.openApi(schema, "/api/data/" + schema);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @GetMapping(value = "/api/data/{schema}/{table}", produces = "application/json")
    public List<RecordReply> readData(
            @PathVariable(name = "schema") String schema,
            @PathVariable(name = "table") String table,
            HttpServletRequest request,
            HttpServletResponse response) {
        Map<String, String[]> params = request.getParameterMap();
        // var: whether javac sees readData's Map values as @Nullable here depends on the JDK
        // patch level (JDK-8225377 backports), so the row type must not be spelled out.
        try {
            final var rows = dataRetrieval.readData(schema, table, params);
            return rows.stream().map(row -> {
                RecordReply rr = new RecordReply();
                rr.getAdditionalProperties().putAll(row);
                return rr;
            }).toList();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
