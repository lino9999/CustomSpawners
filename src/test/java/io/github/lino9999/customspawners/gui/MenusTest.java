package io.github.lino9999.customspawners.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

final class MenusTest {
    @Test
    void parsesTypedScalarsAndInheritanceReset() throws Exception {
        assertEquals(false, Menus.parseValue("false"));
        assertEquals(1200, Menus.parseValue("1200"));
        assertEquals("Tier One", Menus.parseValue("'Tier One'"));
        assertEquals("null", Menus.parseValue("'null'"));
        assertNull(Menus.parseValue("null"));
        assertNull(Menus.parseValue("~"));
    }

    @Test
    void parsesNestedEditorValuesIntoPlainMapsAndLists() throws Exception {
        Object value =
                Menus.parseValue(
                        "{conditions: {min-y: 40, biomes: [PLAINS]}, outputs: [{kind: ITEM, value:"
                            + " IRON_INGOT, amount: 2}]}");
        Map<?, ?> map = assertInstanceOf(Map.class, value);
        Map<?, ?> conditions = assertInstanceOf(Map.class, map.get("conditions"));
        assertEquals(40, conditions.get("min-y"));
        assertEquals(List.of("PLAINS"), conditions.get("biomes"));
        List<?> outputs = assertInstanceOf(List.class, map.get("outputs"));
        Map<?, ?> output = assertInstanceOf(Map.class, outputs.getFirst());
        assertEquals("ITEM", output.get("kind"));
        assertEquals(2, output.get("amount"));
    }

    @Test
    void rejectsExtraRootKeysAndOversizedValues() {
        assertThrows(Exception.class, () -> Menus.parseValue("true\nother: false"));
        assertThrows(IllegalArgumentException.class, () -> Menus.parseValue("x".repeat(32769)));
        assertThrows(Exception.class, () -> Menus.parseValue(""));
    }

    @Test
    void acceptsEmptyListsForClearingDropsOrBiomeConditions() throws Exception {
        assertEquals(List.of(), Menus.parseValue("[]"));
        assertEquals(Map.of(), Menus.parseValue("{}"));
    }
}
