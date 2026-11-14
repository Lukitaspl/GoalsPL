package com.plgroup.goalspl.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Las variables nunca pueden meter otro comando, saltos de línea ni selectores en un comando de consola. */
class PlaceholdersTest {

    @Test
    void messagesKeepTextAsIs() {
        Placeholders p = new Placeholders().set("player", "Steve").set("goal_name", "Caza: zombis").set("contribution", 12);
        assertEquals("Steve aportó 12 a Caza: zombis", p.apply("{player} aportó {contribution} a {goal_name}"));
    }

    @Test
    void commandValuesAreSanitized() {
        Placeholders p = new Placeholders().set("player", "Steve\nop Steve").set("goal_name", "x; stop @a[r=5] \"q\"");
        assertEquals("give Steveop Steve diamond", p.applyForCommand("give {player} diamond"));
        assertEquals("say x stop ar5 q", p.applyForCommand("say {goal_name}"));
        assertEquals("a b", p.applyForCommand("a\nb"));
    }

    @Test
    void unknownPlaceholdersAreFound() {
        assertEquals(List.of("jugador"), Placeholders.unknown("{jugador} y {player} y {jugador}"));
        assertEquals(List.of(), Placeholders.unknown("{goal_id} {rank}"));
    }
}
