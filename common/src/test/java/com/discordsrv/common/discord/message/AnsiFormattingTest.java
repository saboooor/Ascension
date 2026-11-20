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
import com.discordsrv.common.MockDiscordSRV;
import com.discordsrv.common.core.component.ComponentFactory;
import com.discordsrv.common.core.placeholder.format.DiscordMarkdownFormatImpl;
import com.discordsrv.common.feature.console.message.ConsoleMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AnsiFormattingTest {

    @Test
    public void ansiTagsPreserveRgbAndFollowingText() {
        ComponentFactory factory = MockDiscordSRV.getInstance().componentFactory();
        String result = new DiscordMarkdownFormatImpl().map(
                "[ansi]%player_prefix%[/ansi]%essentials_nickname% ► %message%", text -> {
                    if (text.equals("%player_prefix%")) {
                        assertEquals(PlainPlaceholderFormat.Formatting.ANSI, PlainPlaceholderFormat.FORMATTING.get());
                        return factory.ansiSerializer().serialize(Component.text("[Admin]", TextColor.color(12, 123, 234)));
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
    public void tagsWorkAfterTextAndAcrossLines() {
        DiscordMarkdownFormatImpl format = new DiscordMarkdownFormatImpl();
        assertEquals("Before ```ansi\nfirst\nsecond\u001B[0m``` after ```ansi\nthird\u001B[0m```",
                format.map("Before [ansi]first\nsecond[/ansi] after [ansi]third[/ansi]", text -> text));
        assertEquals("Before  after", format.map("Before [ansi][/ansi] after", text -> text));
        assertEquals("Before [ansi]unclosed", format.map("Before [ansi]unclosed", text -> text));
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
