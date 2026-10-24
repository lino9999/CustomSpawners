package io.github.lino9999.customspawners.command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class SpawnerCommandTest {
    @Test
    void durationUsesExplicitUnitsAndHonorsItsInclusiveYearLimit() {
        assertEquals(1000, SpawnerCommand.duration("1s"));
        assertEquals(120_000, SpawnerCommand.duration("2m"));
        assertEquals(7_200_000, SpawnerCommand.duration("2H"));
        assertEquals(86_400_000, SpawnerCommand.duration("1d"));
        assertEquals(365L * 86_400_000, SpawnerCommand.duration("365d"));
    }

    @Test
    void durationRejectsAmbiguousNegativeOverflowAndUnboundedInputs() {
        for (String invalid :
                List.of(
                        "",
                        "0s",
                        "-1h",
                        "1",
                        "1w",
                        "1h30m",
                        " 1h",
                        "1h ",
                        "1.5h",
                        "366d",
                        "99999999d",
                        "999999999999999999999s")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpawnerCommand.duration(invalid),
                    invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> SpawnerCommand.duration(null));
    }

    @Test
    void exactArityPreventsIgnoredAdministrativeArguments() {
        for (String command :
                List.of("save", "reload", "remove", "spawn", "inspect", "shop", "stats")) {
            assertDoesNotThrow(() -> SpawnerCommand.validateArguments(command, new String[0]));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpawnerCommand.validateArguments(command, new String[] {"unexpected"}));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SpawnerCommand.validateArguments(
                                "convert", new String[] {"zombie", "extra"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SpawnerCommand.validateArguments(
                                "give",
                                new String[] {"player", "zombie", "tier_1", "1", "1", "extra"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SpawnerCommand.validateArguments(
                                "playerboost", new String[] {"player", "1", "1h", "extra"}));
    }

    @Test
    void upgradeArgumentsCannotSilentlyChooseAnotherPurchaseMode() {
        assertDoesNotThrow(() -> SpawnerCommand.validateArguments("upgrade", new String[0]));
        assertDoesNotThrow(
                () -> SpawnerCommand.validateArguments("upgrade", new String[] {"XP", "tier"}));
        assertDoesNotThrow(
                () -> SpawnerCommand.validateArguments("upgrade", new String[] {"money", "stack"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnerCommand.validateArguments("upgrade", new String[] {"xp", "mistyped"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnerCommand.validateArguments("upgrade", new String[] {"tier"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SpawnerCommand.validateArguments(
                                "upgrade", new String[] {"money", "tier", "extra"}));
    }

    @Test
    void boostRequiresEitherTheMenuOrBothPurchaseArguments() {
        assertDoesNotThrow(() -> SpawnerCommand.validateArguments("boost", new String[0]));
        assertDoesNotThrow(
                () -> SpawnerCommand.validateArguments("boost", new String[] {"2", "5"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpawnerCommand.validateArguments("boost", new String[] {"2"}));
    }
}
