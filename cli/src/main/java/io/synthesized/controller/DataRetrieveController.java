package io.synthesized.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface DataRetrieveController {
    @GetMapping(
            value = "/api/data/{schema}/{table}",
            produces = "application/json"
    )
    List<RecordReply> readData(
            @PathVariable(name = "schema") String schema,
            @PathVariable(name = "table") String table,
            HttpServletRequest request,
            HttpServletResponse response
    );
}
