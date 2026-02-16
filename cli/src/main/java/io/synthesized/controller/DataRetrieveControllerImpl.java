package io.synthesized.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.synthesized.jdbcrest.DataRetrieval;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class DataRetrieveControllerImpl implements DataRetrieveController {
    private final DataRetrieval dataRetrieval;

    public DataRetrieveControllerImpl(DataRetrieval dataRetrieval) {
        this.dataRetrieval = dataRetrieval;
    }

    @Override
    @GetMapping(value = "/api/data/{schema}/{table}", produces = "application/json")
    public List<RecordReply> readData(
            @PathVariable(name = "schema") String schema,
            @PathVariable(name = "table") String table,
            HttpServletRequest request,
            HttpServletResponse response) {
        Map<String, String[]> params = request.getParameterMap();
        List<Map<String, Object>> rows = dataRetrieval.readData(schema, table, params);
        return rows.stream().map(row -> {
            RecordReply rr = new RecordReply();
            rr.getAdditionalProperties().putAll(row);
            return rr;
        }).toList();
    }
}
