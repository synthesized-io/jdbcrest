package io.synthesized.jdbcrest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class QueryAst {
    private QueryAst() {
    }

    // ---- AST ----

    public sealed interface Expr permits And, Comparison, In, Not, Or {
        void accept(Consumer<Expr> visitor);
    }

    public record Or(Collection<Expr> args) implements Expr {
        public Or {
            Objects.requireNonNull(args, "args");
            args = List.copyOf(args);
            if (args.isEmpty()) throw new IllegalArgumentException("or() requires at least one argument");
        }

        @Override
        public void accept(Consumer<Expr> visitor) {
            for (Expr arg : args) {
                arg.accept(visitor);
            }
            visitor.accept(this);
        }
    }

    public record And(Collection<Expr> args) implements Expr {
        public And {
            Objects.requireNonNull(args, "args");
            args = List.copyOf(args);
            if (args.isEmpty()) throw new IllegalArgumentException("and() requires at least one argument");
        }

        @Override
        public void accept(Consumer<Expr> visitor) {
            for (Expr arg : args) {
                arg.accept(visitor);
            }
            visitor.accept(this);
        }
    }

    public record Not(Expr arg) implements Expr {
        public Not {
            Objects.requireNonNull(arg, "arg");
        }

        @Override
        public void accept(Consumer<Expr> visitor) {
            arg.accept(visitor);
            visitor.accept(this);
        }
    }

    public record Comparison(StringLiteral field, ComparisonOperator operator, Value value) implements Expr {
        public Comparison {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public void accept(Consumer<Expr> visitor) {
            visitor.accept(this);
        }
    }

    public record In(StringLiteral field, Collection<Value> values) implements Expr {
        public In {
            Objects.requireNonNull(values, "values");
            values = List.copyOf(values);
            if (values.isEmpty()) throw new IllegalArgumentException("in() requires at least one argument");
        }

        @Override
        public void accept(Consumer<Expr> visitor) {
            visitor.accept(this);
        }
    }

    public enum ComparisonOperator {
        EQ, NEQ, GT, GTE, LT, LTE
    }

    public sealed interface Value permits IntLiteral, DoubleLiteral,
            StringLiteral, DateLiteral {
        Object objectValue();
        String raw();
        default String quotedIfNeeded() {
            return "'" + raw().replace("'", "''") + "'";
        }
    }

    public record IntLiteral(int value) implements Value {
        @Override
        public Object objectValue() {
            return value;
        }

        @Override
        public String raw() {
            return Integer.toString(value);
        }

        @Override
        public String quotedIfNeeded() {
            return raw();
        }
    }

    public record DoubleLiteral(double value) implements Value {
        @Override
        public Object objectValue() {
            return value;
        }

        @Override
        public String raw() {
            return Double.toString(value);
        }

        @Override
        public String quotedIfNeeded() {
            return raw();
        }
    }

    public record StringLiteral(String value) implements Value {
        @Override
        public Object objectValue() {
            return value;
        }

        @Override
        public String raw() {
            return value;
        }
    }

    public record DateLiteral(LocalDate value) implements Value {
        @Override
        public Object objectValue() {
            return value;
        }

        @Override
        public String raw() {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(value);
        }
    }

}
