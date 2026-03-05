package io.synthesized.jdbcrest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataRetrieval {
    private final DataSource dataSource;
    private final DatabaseType databaseType;

    public DataRetrieval(DataSource dataSource, DatabaseType databaseType) {
        this.dataSource = dataSource;
        this.databaseType = databaseType;
    }

    public List<Map<String, Object>> readData(
            String schema, String table, Map<String, String[]> params
    ) {

        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        List<String> terms = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String column = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) continue;
            for (String raw : values) {
                if (raw == null) continue;
                terms.add(column + "." + raw);
            }
        }

        QueryAst.Expr expr;
        if (terms.size() > 1) {
            expr = parse("and(" + String.join(",", terms) + ")");
        } else if (terms.size() == 1) {
            expr = parse(terms.getFirst());
        } else expr = null;

        String sql = databaseType.getTranspiler().toSQL(schema, table, expr);


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
                        Object value = rs.getObject(i);
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

    private static QueryAst.Expr parse(String internalQuery) {
        try {
            return QueryParser.parse(internalQuery);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
