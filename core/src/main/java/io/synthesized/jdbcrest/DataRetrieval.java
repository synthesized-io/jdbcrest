package io.synthesized.jdbcrest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataRetrieval {
    private final DataSource dataSource;

    public DataRetrieval(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> readData(
            String schema, String table, Map<String, String[]> params
    ) {
        StringBuilder sql = new StringBuilder("select * from ").append(schema).append('.').append(table);

        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String column = entry.getKey();
            String[] values = entry.getValue();
            if (values == null || values.length == 0) continue;
            for (String raw : values) {
                if (raw == null) continue;
                QueryAst.Comparison cmp;
                try {
                    cmp = (QueryAst.Comparison) QueryParser.parse(column + "." + raw);
                } catch (ParseException e) {
                    throw new IllegalStateException(e);
                }
                String sqlOp = switch (cmp.operator()) {
                    case EQ -> "=";
                    case GT -> ">";
                    case GTE -> ">=";
                    case LT -> "<";
                    case LTE -> "<=";
                    case NEQ -> "<>";
                };
                conditions.add(column + " " + sqlOp + " ?");

                QueryAst.Value val = cmp.value();
                Object arg;
                try {
                    arg = Integer.valueOf(val.text());
                } catch (NumberFormatException e) {
                    arg = val.text();
                }
                args.add(arg);
            }
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }
        sql.append(" order by 1");

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
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
}
