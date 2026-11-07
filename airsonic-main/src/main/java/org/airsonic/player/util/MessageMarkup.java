/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.util;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * Turns a message written by an administrator, such as the welcome message, into HTML that is safe to show to
 * every user. Simple HTML (bold, italic, lists, links) is kept and everything else is removed. The wiki markup of
 * older versions is still understood: {@code __bold__}, {@code ~~italic~~}, {@code \\} for a line break and an
 * empty line for a new paragraph.
 */
public final class MessageMarkup {

    private static final Pattern BOLD = Pattern.compile("__(.+?)__", Pattern.DOTALL);
    private static final Pattern ITALIC = Pattern.compile("~~(.+?)~~", Pattern.DOTALL);
    private static final Pattern LINE_BREAK = Pattern.compile("\\\\\\\\");
    private static final Pattern PARAGRAPH = Pattern.compile("\\R[ \\t]*\\R\\s*");

    // only used to check relative links, which are kept as they are
    private static final String BASE_URI = "http://localhost/";

    private static final Safelist SAFELIST = Safelist.basic().preserveRelativeLinks(true);

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    private MessageMarkup() {
    }

    /**
     * @return the message as sanitized HTML, or {@code null} if it is blank
     */
    public static String toSafeHtml(String message) {
        if (StringUtils.isBlank(message)) {
            return null;
        }
        String html = BOLD.matcher(message).replaceAll("<b>$1</b>");
        html = ITALIC.matcher(html).replaceAll("<i>$1</i>");
        html = LINE_BREAK.matcher(html).replaceAll("<br>");
        html = PARAGRAPH.matcher(html).replaceAll("<br><br>");
        return StringUtils.trimToNull(Jsoup.clean(html, BASE_URI, SAFELIST, OUTPUT));
    }
}
