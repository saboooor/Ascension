/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2026 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.discordsrv.common.discord.message;

import com.discordsrv.api.placeholder.format.PlainPlaceholderFormat;
import com.discordsrv.api.placeholder.provider.SinglePlaceholder;
import com.discordsrv.api.discord.entity.message.SendableDiscordMessage;
import com.discordsrv.common.logging.LogLevel;
import com.discordsrv.common.MockDiscordSRV;
import com.discordsrv.common.core.placeholder.PlaceholderServiceImpl;
import com.discordsrv.common.core.placeholder.result.ComponentResultStringifier;
import com.discordsrv.common.core.placeholder.format.DiscordMarkdownFormatImpl;
import com.discordsrv.common.feature.console.message.ConsoleMessage;
import com.discordsrv.common.feature.console.SingleConsoleHandler;
import com.discordsrv.common.feature.console.entry.LogEntry;
import com.discordsrv.common.config.main.ConsoleConfig;
import com.discordsrv.common.config.main.generic.DiscordOutputMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AnsiFormattingTest {

    @Test
    public void ansiCodeBlocksPreserveRgbAndFollowingText() {
        String result = new DiscordMarkdownFormatImpl().map(
                "```ansi\n%player_prefix%```%essentials_nickname% ► %message%", text -> {
                    if (text.equals("%player_prefix%")) {
                        assertEquals(PlainPlaceholderFormat.Formatting.ANSI, PlainPlaceholderFormat.FORMATTING.get());
                        return MockDiscordSRV.getInstance().componentFactory().ansiSerializer().serialize(Component.text("[Admin]", TextColor.color(12, 123, 234)));
                    }
                    return text.replace("%essentials_nickname%", "Nickname").replace("%message%", "Hello");
                });
        assertTrue(result.startsWith("```ansi\n"));
        assertTrue(result.contains("38;2;12;123;234m"));
        assertTrue(result.endsWith("\u001B[0m```Nickname ► Hello"));
    }

    @Test
    public void multilineAndPlainBlocksKeepTheirFormattingModes() {
        DiscordMarkdownFormatImpl format = new DiscordMarkdownFormatImpl();
        assertEquals("```ansi\nprefix\u001B[0m```", format.map("```ansi\nprefix```", text -> {
            if (!text.isEmpty()) {
                assertEquals(PlainPlaceholderFormat.Formatting.ANSI, PlainPlaceholderFormat.FORMATTING.get());
            }
            return text;
        }));
        assertEquals("```ansible\nplain```", format.map("```ansible\nplain```", text -> {
            if (!text.isEmpty()) {
                assertEquals(PlainPlaceholderFormat.Formatting.PLAIN, PlainPlaceholderFormat.FORMATTING.get());
            }
            return text;
        }));
    }

    @Test
    public void ansiPreservesComponentColorsAndLeavesMarkupLiteral() {
        var discordSRV = MockDiscordSRV.getInstance();
        var service = new PlaceholderServiceImpl(discordSRV);
        service.addResultMapper(new ComponentResultStringifier(discordSRV));
        String content = SendableDiscordMessage.builder()
                .setContent("```ansi\n<red>%prefix%</red> %colored%```")
                .toFormatter()
                .applyPlaceholderService(service)
                .addContext(new SinglePlaceholder("prefix", Component.text("<bold>Rank</bold>")),
                        new SinglePlaceholder("colored", Component.text("Color", TextColor.color(12, 123, 234))))
                .build().getContent();
        assertNotNull(content);
        assertTrue(content.contains("<red><bold>Rank</bold></red>"), content);
        assertTrue(content.contains("38;2;12;123;234m"), content);
        String block = content.substring("```ansi\n".length(), content.length() - 3);
        assertEquals("<red><bold>Rank</bold></red> Color", new ConsoleMessage(discordSRV, block).asPlain());
    }

    @Test
    public void errorsAndWarningsUseSeverityColorsWithoutOverridingExplicitColors() {
        var discordSRV = MockDiscordSRV.getInstance();
        ConsoleMessage error = new ConsoleMessage(discordSRV, "[ERROR] Failed\n    at example.Stack.frame(Stack.java:1)");
        assertTrue(error.asAnsi(LogLevel.ERROR).contains("38;2;255;85;85m"));
        assertTrue(error.asAnsi(LogLevel.of("FATAL")).contains("38;2;255;85;85m"));
        assertTrue(error.asAnsi(LogLevel.WARNING).contains("38;2;255;255;85m"));
        assertFalse(error.asAnsi(LogLevel.INFO).contains("38;2;"));
        ConsoleMessage colored = new ConsoleMessage(discordSRV, "Failed §aGreen§r back to error");
        String ansi = colored.asAnsi(LogLevel.ERROR);
        assertTrue(ansi.contains("38;2;85;255;85m"), ansi);
        assertTrue(ansi.lastIndexOf("38;2;255;85;85m") > ansi.indexOf("38;2;85;255;85m"), ansi);
        assertTrue(ansi.endsWith("\u001B[0m"));
    }

    @Test
    public void completeErrorEntryColorsHeaderMessageAndStackTrace() throws ReflectiveOperationException {
        var discordSRV = MockDiscordSRV.getInstance();
        ConsoleConfig config = new ConsoleConfig();
        config.appender.outputMode = DiscordOutputMode.OFF;
        config.appender.lineFormat = "[%log_level%] %message%";
        SingleConsoleHandler handler = new SingleConsoleHandler(discordSRV, discordSRV.logger(), config);
        var formatEntry = SingleConsoleHandler.class.getDeclaredMethod("formatEntry",
                LogEntry.class, String.class, DiscordOutputMode.class, boolean.class);
        formatEntry.setAccessible(true);
        var parts = (java.util.List<?>) formatEntry.invoke(handler,
                new LogEntry("Test", LogLevel.ERROR, "Failed §x§1§2§a§b§3§4Green§r failed again", null),
                "java.lang.RuntimeException: failure\n    at example.Stack.frame(Stack.java:1)",
                DiscordOutputMode.ANSI, false);
        assertEquals(1, parts.size());
        String output = (String) parts.getFirst();
        assertEquals("[ERROR] Failed Green failed again\njava.lang.RuntimeException: failure\n    at example.Stack.frame(Stack.java:1)\n",
                new ConsoleMessage(discordSRV, output).asPlain());
        String[] lines = output.split("\n");
        assertTrue(lines[0].contains("38;2;255;85;85m"), output);
        assertTrue(lines[0].contains("38;2;18;171;52m"), output);
        assertTrue(lines[1].contains("38;2;255;85;85m"), output);
        assertTrue(output.endsWith("\u001B[0m\n"), output);
    }

    @Test
    public void consolePreservesExactHexColors() {
        for (String color : java.util.List.of("§x§1§2§a§b§3§4", "§X§1§2§A§B§3§4", "§#12ab34",
                "\u007Fx\u007F1\u007F2\u007Fa\u007Fb\u007F3\u007F4", "\u007F#12AB34")) {
            ConsoleMessage message = new ConsoleMessage(MockDiscordSRV.getInstance(), color + "Hex§r plain");
            assertEquals("Hex plain", message.asPlain(), color);
            assertTrue(message.asAnsi().contains("38;2;18;171;52m"), message.asAnsi());
            assertTrue(message.asAnsi(LogLevel.ERROR).contains("38;2;18;171;52m"), message.asAnsi(LogLevel.ERROR));
        }
        ConsoleMessage gradient = new ConsoleMessage(MockDiscordSRV.getInstance(),
                "§x§1§2§a§b§3§4A§x§5§6§7§8§9§aB");
        assertEquals("AB", gradient.asPlain());
        assertTrue(gradient.asAnsi().contains("38;2;18;171;52m"), gradient.asAnsi());
        assertTrue(gradient.asAnsi().contains("38;2;86;120;154m"), gradient.asAnsi());
    }

    @Test
    public void consolePreservesTrueColorAndResets() {
        ConsoleMessage message = new ConsoleMessage(MockDiscordSRV.getInstance(),
                "\u001B[38;2;12;123;234mRGB\u001B[0m plain");
        assertEquals("RGB plain", message.asPlain());
        assertTrue(message.asAnsi().contains("38;2;12;123;234m"));
        assertTrue(message.asAnsi().endsWith("\u001B[0m"));
    }
}
