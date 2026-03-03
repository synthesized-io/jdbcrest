package io.synthesized.jdbcrest;

import java.util.List;
import java.util.Objects;

public final class QueryAst {
    private QueryAst() {
    }

    // ---- AST ----

    public sealed interface Expr permits Or, And, Not, Comparison {
    }

    public record Or(List<Expr> args) implements Expr {
        public Or {
            Objects.requireNonNull(args, "args");
            args = List.copyOf(args);
            if (args.isEmpty()) throw new IllegalArgumentException("or() requires at least one argument");
        }
    }

    public record And(List<Expr> args) implements Expr {
        public And {
            Objects.requireNonNull(args, "args");
            args = List.copyOf(args);
            if (args.isEmpty()) throw new IllegalArgumentException("and() requires at least one argument");
        }
    }

    public record Not(Expr arg) implements Expr {
        public Not {
            Objects.requireNonNull(arg, "arg");
        }
    }

    public record Comparison(FieldPath field, ComparisonOperator operator, Value value) implements Expr {
        public Comparison {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
        }
    }

    public record FieldPath(List<String> segments) {
        public FieldPath {
            Objects.requireNonNull(segments, "segments");
            segments = List.copyOf(segments);
            if (segments.isEmpty()) throw new IllegalArgumentException("field path must have at least one segment");
            for (String s : segments) Objects.requireNonNull(s, "field segment");
        }

        @Override
        public String toString() {
            return String.join(".", segments);
        }
    }

    public enum ComparisonOperator {
        EQ, GT, GTE, LT, LTE
        // Extend later with PostgREST operators as needed (like: LIKE, ILIKE, IN, IS, etc.)
    }

    public sealed interface Value permits Atom, Quoted {
        String text();
    }

    /**
     * Unquoted value atom (cannot contain reserved chars like '.', ',', '(', ')', ':').
     */
    public record Atom(String text) implements Value {
        public Atom {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * Quoted value that may contain reserved chars (decoded/unescaped by parser).
     */
    public record Quoted(String text) implements Value {
        public Quoted {
            Objects.requireNonNull(text, "text");
        }
    }

    // ---- helpers ----

    /**
     * tokenImage includes surrounding quotes, e.g. "\"hello\\\"world\""
     */
    public static String unescapeQuoted(String tokenImage) {
        Objects.requireNonNull(tokenImage, "tokenImage");
        if (tokenImage.length() < 2
                || tokenImage.charAt(0) != '"'
                || tokenImage.charAt(tokenImage.length() - 1) != '"') {
            throw new IllegalArgumentException("Not a quoted token: " + tokenImage);
        }

        StringBuilder out = new StringBuilder(tokenImage.length() - 2);
        for (int i = 1; i < tokenImage.length() - 1; i++) {
            char c = tokenImage.charAt(i);
            if (c == '\\' && i + 1 < tokenImage.length() - 1) {
                char n = tokenImage.charAt(++i);
                out.append(
                        switch (n) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            case 'r' -> '\r';
                            case '\\' -> '\\';
                            case '"' -> '"';
                            default -> n; // keep unknown escapes as-is
                        }
                );
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
