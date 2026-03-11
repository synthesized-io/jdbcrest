package io.synthesized.jdbcrest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataRetrieval {
    private final DataSource dataSource;
    private final QueryTranspiler queryTranspiler;

    public DataRetrieval(DataSource dataSource, QueryTranspiler queryTranspiler) {
        this.dataSource = dataSource;
        this.queryTranspiler = queryTranspiler;
    }

    public List<Map<String, Object>> readData(
            String schema, String table, Map<String, String[]> params
    ) {

        QueryBody body = getTerms(params);

        QueryAst.Expr expr;
        if (body.terms.size() > 1) {
            expr = parse("and(" + String.join(",", body.terms) + ")");
        } else if (body.terms.size() == 1) {
            expr = parse(body.terms.getFirst());
        } else expr = null;
        String sql = queryTranspiler.toSQL(schema, table, body.limit, body.offset, expr);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement();
        ) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnLabel(i);
                        Object value = switch (meta.getColumnType(i)) {
                            case Types.NCLOB -> rs.getString(i);
                            default -> rs.getObject(i);
                        };
                        row.put(colName, value);
                    }
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private record QueryBody(List<String> terms, Integer limit, Integer offset) {
    }

    private static QueryBody getTerms(Map<String, String[]> params) {
        Integer limit = null;
        Integer offset = null;
        List<String> terms = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String column = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) continue;
            for (String raw : values) {
                if (raw == null) continue;
                switch (column.toLowerCase()) {
                    case "or", "and", "not.or", "not.and" -> terms.add(column + raw);
                    case "limit" -> limit = Integer.parseInt(raw);
                    case "offset" -> offset = Integer.parseInt(raw);
                    case "select" -> throw new IllegalArgumentException("SELECT not implemented");
                    default -> terms.add(column + "." + raw);
                }
            }
        }
        return new QueryBody(terms, limit, offset);
    }

    private static QueryAst.Expr parse(String internalQuery) {
        try {
            return QueryParser.parse(internalQuery);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
