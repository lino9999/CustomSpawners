package io.github.lino9999.customspawners.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SpawnExpressionTest {
    @Test
    void evaluatesBothVariablesCaseInsensitivelyWithoutImplicitStackMultiplication() {
        SpawnExpression expression = SpawnExpression.compile("{rand} * {stack_size} + 2");
        assertEquals(14, expression.evaluate(3, 4));
        assertEquals(5, SpawnExpression.compile("{RAND}").evaluate(5, 1000));
        assertEquals(34, expression.evaluate(8, 4));
    }

    @Test
    void observesArithmeticPrecedenceAndParentheses() {
        assertEquals(14, SpawnExpression.compile("2 + 3 * 4").evaluate(0, 0));
        assertEquals(20, SpawnExpression.compile("(2 + 3) * 4").evaluate(0, 0));
        assertEquals(3, SpawnExpression.compile("17 % 5 + 8 / 8").evaluate(0, 0));
        assertEquals(-1, SpawnExpression.compile("-3 + +2").evaluate(0, 0));
    }

    @Test
    void exponentiationIsRightAssociativeAndPrecedesUnaryNegation() {
        assertEquals(512, SpawnExpression.compile("2^3^2").evaluate(0, 0));
        assertEquals(-4, SpawnExpression.compile("-2^2").evaluate(0, 0));
        assertEquals(0.25, SpawnExpression.compile("2^-2").evaluate(0, 0));
        assertEquals(4, SpawnExpression.compile("(-2)^2").evaluate(0, 0));
    }

    @Test
    void acceptsDecimalAndScientificConstants() {
        assertEquals(102.75, SpawnExpression.compile("1e2 + .25 + 2.5").evaluate(0, 0));
        assertEquals(0.001, SpawnExpression.compile("1E-3").evaluate(0, 0));
    }

    @Test
    void acceptsZeroInputsAndChecksVariableDependentZeroDivisorsAtEvaluation() {
        assertEquals(0, SpawnExpression.compile("{RAND} * {STACK_SIZE}").evaluate(0, 0));
        assertEquals(0, SpawnExpression.compile("{RAND} / {STACK_SIZE}").evaluate(0, 1));
        assertEquals(-2, SpawnExpression.compile("{RAND} - {STACK_SIZE}").evaluate(0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnExpression.compile("{RAND} / {STACK_SIZE}").evaluate(1, 0));
        assertEquals(0, SpawnExpression.compile("{STACK_SIZE} % {RAND}").evaluate(1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnExpression.compile("{STACK_SIZE} % {RAND}").evaluate(0, 1));
    }

    @Test
    void rejectsScriptingSyntaxUnknownVariablesAndIncompleteExpressions() {
        for (String source :
                new String[] {
                    "",
                    "Math.random()",
                    "{OWNER}",
                    "2(3)",
                    "2**3",
                    "2+",
                    "(2",
                    "1e",
                    "0x20",
                    "{RAND",
                    "2;3"
                }) {
            assertThrows(
                    IllegalArgumentException.class, () -> SpawnExpression.compile(source), source);
        }
        assertThrows(IllegalArgumentException.class, () -> SpawnExpression.compile(null));
    }

    @Test
    void rejectsNonFiniteInputsConstantsAndIntermediateResults() {
        assertThrows(IllegalArgumentException.class, () -> SpawnExpression.compile("1e309"));
        SpawnExpression expression = SpawnExpression.compile("{RAND}");
        assertThrows(IllegalArgumentException.class, () -> expression.evaluate(Double.NaN, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> expression.evaluate(1, Double.POSITIVE_INFINITY));
        for (String source :
                new String[] {"1/0", "1%-0", "(-1)^.5", "1e308 * 1e308 * 0", "2^1024"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpawnExpression.compile(source).evaluate(1, 1),
                    source);
        }
    }

    @Test
    void boundsInputLengthAndNesting() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnExpression.compile(" ".repeat(256) + "1"));
        assertEquals(
                1, SpawnExpression.compile("(".repeat(16) + "1" + ")".repeat(16)).evaluate(0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnExpression.compile("(".repeat(17) + "1" + ")".repeat(17)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnExpression.compile("-".repeat(17) + "1"));
    }
}
