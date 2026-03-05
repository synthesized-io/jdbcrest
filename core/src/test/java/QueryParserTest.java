import io.synthesized.jdbcrest.ParseException;
import io.synthesized.jdbcrest.QueryAst.*;
import io.synthesized.jdbcrest.QueryParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryParserTest {

    @Test
    void comparisonTest() throws ParseException {
        Expr exp = QueryParser.parse("x.gt.42");
        assertThat(exp).isInstanceOf(Comparison.class);
        var comp = (Comparison) exp;
        assertThat(comp).isEqualTo(
                new Comparison(new StringLiteral("x"),
                        ComparisonOperator.GT,
                        new IntLiteral(42))
        );
    }

    @Test
    void orWithTwoComparisons() throws ParseException {
        Expr exp = QueryParser.parse("or(x.gt.42,y.lt.10)");

        assertThat(exp).isEqualTo(
                new Or(List.of(
                        new Comparison(new StringLiteral("x"), ComparisonOperator.GT, new IntLiteral(42)),
                        new Comparison(new StringLiteral("y"), ComparisonOperator.LT, new IntLiteral(10))
                ))
        );
    }

    @Test
    void andWithThreeComparisons() throws ParseException {
        Expr exp = QueryParser.parse("and(a.eq.1,b.gte.2,c.lte.3)");

        assertThat(exp).isEqualTo(
                new And(List.of(
                        new Comparison(new StringLiteral("a"), ComparisonOperator.EQ, new IntLiteral(1)),
                        new Comparison(new StringLiteral("b"), ComparisonOperator.GTE, new IntLiteral(2)),
                        new Comparison(new StringLiteral("c"), ComparisonOperator.LTE, new IntLiteral(3))
                ))
        );
    }

    @Test
    void notOnComparison_dotForm() throws ParseException {
        Expr exp = QueryParser.parse("x.not.gt.42");

        assertThat(exp).isEqualTo(
                new Not(new Comparison(new StringLiteral("x"), ComparisonOperator.GT, new IntLiteral(42)))
        );
    }

    @Test
    void notAndCombination_chainedForm() throws ParseException {
        Expr exp = QueryParser.parse("not.and(age.gte.11,age.lte.17)");

        assertThat(exp).isEqualTo(
                new Not(new And(List.of(
                        new Comparison(new StringLiteral("age"), ComparisonOperator.GTE, new IntLiteral(11)),
                        new Comparison(new StringLiteral("age"), ComparisonOperator.LTE, new IntLiteral(17))
                )))
        );
    }

    @Test
    void nestedOrNotAndLikeExample() throws ParseException {
        Expr exp = QueryParser.parse("or(age.eq.14,not.and(age.gte.11,age.lte.17))");

        assertThat(exp).isEqualTo(
                new Or(List.of(
                        new Comparison(new StringLiteral("age"), ComparisonOperator.EQ, new IntLiteral(14)),
                        new Not(new And(List.of(
                                new Comparison(new StringLiteral("age"), ComparisonOperator.GTE, new IntLiteral(11)),
                                new Comparison(new StringLiteral("age"), ComparisonOperator.LTE, new IntLiteral(17))
                        )))
                ))
        );
    }


    @Test
    void nestedAndInsideOr() throws ParseException {
        Expr exp = QueryParser.parse("or(and(a.eq.1,b.eq.2),c.gt.0)");

        assertThat(exp).isEqualTo(
                new Or(List.of(
                        new And(List.of(
                                new Comparison(new StringLiteral("a"), ComparisonOperator.EQ, new IntLiteral(1)),
                                new Comparison(new StringLiteral("b"), ComparisonOperator.EQ, new IntLiteral(2))
                        )),
                        new Comparison(new StringLiteral("c"), ComparisonOperator.GT, new IntLiteral(0))
                ))
        );
    }

    @Test
    void quotedValue_allowsReservedChars() throws ParseException {
        Expr exp = QueryParser.parse("x.eq.\"hello, world\"");

        assertThat(exp).isEqualTo(
                new Comparison(new StringLiteral("x"),
                        ComparisonOperator.EQ, new StringLiteral("hello, world"))
        );
    }

    @Test
    void quotedValue_unescapesBackslashAndQuote() throws ParseException {
        Expr exp = QueryParser.parse("x.eq.\"a\\b\\c\"");
        assertThat(exp).isEqualTo(
                new Comparison(new StringLiteral("x"), ComparisonOperator.EQ, new StringLiteral("a\\b\\c"))
        );
    }

    @Test
    void dateAtom_isAccepted() throws ParseException {
        Expr exp = QueryParser.parse("created_at.gte.2017-01-11");
        assertThat(exp).isEqualTo(
                new Comparison(new StringLiteral("created_at"),
                        ComparisonOperator.GTE,
                        new DateLiteral(LocalDate.of(2017, 01, 11)))
        );
    }
}
