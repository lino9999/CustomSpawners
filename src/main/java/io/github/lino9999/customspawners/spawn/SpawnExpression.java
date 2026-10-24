package io.github.lino9999.customspawners.spawn;

import java.util.Locale;

public final class SpawnExpression {
    private final Node root;

    private SpawnExpression(Node root) {
        this.root = root;
    }

    public static SpawnExpression compile(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > 256) {
            throw new IllegalArgumentException(
                    "Spawn expressions must contain between 1 and 256 characters.");
        }
        Parser parser = new Parser(expression);
        Node root = parser.expression(0);
        parser.space();
        if (parser.position != expression.length()) throw parser.invalid("Unexpected character");
        return new SpawnExpression(root);
    }

    public double evaluate(double random, double stack) {
        finite(random);
        finite(stack);
        return finite(root.evaluate(random, stack));
    }

    private static double finite(double value) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Spawn expression values must be finite.");
        return value;
    }

    private interface Node {
        double evaluate(double random, double stack);
    }

    private record Constant(double value) implements Node {
        public double evaluate(double random, double stack) {
            return value;
        }
    }

    private record Variable(boolean randomValue) implements Node {
        public double evaluate(double random, double stack) {
            return randomValue ? random : stack;
        }
    }

    private record Unary(char operator, Node operand) implements Node {
        public double evaluate(double random, double stack) {
            double value = operand.evaluate(random, stack);
            return operator == '-' ? -value : value;
        }
    }

    private record Binary(char operator, Node left, Node right) implements Node {
        public double evaluate(double random, double stack) {
            double first = left.evaluate(random, stack);
            double second = right.evaluate(random, stack);
            if ((operator == '/' || operator == '%') && second == 0) {
                throw new IllegalArgumentException("Spawn expressions cannot divide by zero.");
            }
            return finite(
                    switch (operator) {
                        case '+' -> first + second;
                        case '-' -> first - second;
                        case '*' -> first * second;
                        case '/' -> first / second;
                        case '%' -> first % second;
                        case '^' -> Math.pow(first, second);
                        default ->
                                throw new IllegalArgumentException(
                                        "Unknown spawn expression operator.");
                    });
        }
    }

    private static final class Parser {
        private final String source;
        private int position;
        private int operators;

        private Parser(String source) {
            this.source = source;
        }

        private Node expression(int depth) {
            depth(depth);
            Node result = term(depth);
            while (true) {
                if (take('+')) result = binary('+', result, term(depth));
                else if (take('-')) result = binary('-', result, term(depth));
                else return result;
            }
        }

        private Node term(int depth) {
            Node result = unary(depth);
            while (true) {
                if (take('*')) result = binary('*', result, unary(depth));
                else if (take('/')) result = binary('/', result, unary(depth));
                else if (take('%')) result = binary('%', result, unary(depth));
                else return result;
            }
        }

        private Node unary(int depth) {
            depth(depth);
            if (take('+')) {
                operation();
                return new Unary('+', unary(depth + 1));
            }
            if (take('-')) {
                operation();
                return new Unary('-', unary(depth + 1));
            }
            return power(depth);
        }

        private Node power(int depth) {
            Node result = primary(depth);
            if (take('^')) result = binary('^', result, unary(depth + 1));
            return result;
        }

        private Node primary(int depth) {
            space();
            if (take('(')) {
                Node result = expression(depth + 1);
                if (!take(')')) throw invalid("Expected a closing parenthesis");
                return result;
            }
            if (take('{')) {
                int start = position;
                while (position < source.length() && source.charAt(position) != '}') position++;
                if (position == source.length()) throw invalid("Expected a closing variable brace");
                String name = source.substring(start, position++).trim().toUpperCase(Locale.ROOT);
                return switch (name) {
                    case "RAND" -> new Variable(true);
                    case "STACK_SIZE" -> new Variable(false);
                    default ->
                            throw invalid("Only {RAND} and {STACK_SIZE} variables are supported");
                };
            }
            int start = position;
            boolean digits = digits();
            if (position < source.length() && source.charAt(position) == '.') {
                position++;
                digits = digits() || digits;
            }
            if (!digits) throw invalid("Expected a number, variable or parenthesis");
            if (position < source.length()
                    && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                position++;
                if (position < source.length()
                        && (source.charAt(position) == '+' || source.charAt(position) == '-'))
                    position++;
                if (!digits()) throw invalid("Expected an exponent");
            }
            try {
                return new Constant(finite(Double.parseDouble(source.substring(start, position))));
            } catch (NumberFormatException exception) {
                throw invalid("Invalid number");
            }
        }

        private boolean digits() {
            int start = position;
            while (position < source.length()
                    && source.charAt(position) >= '0'
                    && source.charAt(position) <= '9') position++;
            return position > start;
        }

        private Node binary(char operator, Node left, Node right) {
            operation();
            return new Binary(operator, left, right);
        }

        private void operation() {
            if (++operators > 128) throw invalid("At most 128 operators are allowed");
        }

        private void depth(int depth) {
            if (depth > 16) throw invalid("At most 16 nested constructs are allowed");
        }

        private boolean take(char value) {
            space();
            if (position >= source.length() || source.charAt(position) != value) return false;
            position++;
            return true;
        }

        private void space() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position)))
                position++;
        }

        private IllegalArgumentException invalid(String reason) {
            return new IllegalArgumentException(
                    "Invalid spawn expression at position " + (position + 1) + ": " + reason + ".");
        }
    }
}
