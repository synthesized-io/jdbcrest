import io.synthesized.jdbcrest.ParseException;
import io.synthesized.jdbcrest.QueryAst;
import io.synthesized.jdbcrest.QueryParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryParserTest {

    @Test
    void comparisonTest() throws ParseException {
        QueryAst.Expr exp =  QueryParser.parse("x.gt.42");
        assertThat(exp).isInstanceOf(QueryAst.Comparison.class);
        var comp = (QueryAst.Comparison) exp;
        assertThat(comp.field().toString()).isEqualTo("x");
        assertThat(comp.operator()).isEqualTo(QueryAst.ComparisonOperator.GT);
        assertThat(comp.value().text()).isEqualTo("42");
    }
}
