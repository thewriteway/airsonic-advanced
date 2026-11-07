package org.airsonic.player.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMarkupTest {

    @Test
    void blankMessageGivesNull() {
        assertThat(MessageMarkup.toSafeHtml(null)).isNull();
        assertThat(MessageMarkup.toSafeHtml("  \n ")).isNull();
    }

    @Test
    void convertsLegacyWikiMarkup() {
        String html = MessageMarkup.toSafeHtml("__Welcome__\n\\\\ \\\\\nSome ~~nice~~ music.\n\nNext part");
        assertThat(html).isEqualTo("<b>Welcome</b>\n<br> <br>\nSome <i>nice</i> music.<br><br>Next part");
    }

    @Test
    void keepsSimpleHtmlAndRelativeLinks() {
        String html = MessageMarkup.toSafeHtml("Go to <a href='settings.view'>Settings</a> or <a href=\"https://example.com/\">here</a>");
        assertThat(html).contains("<a href=\"settings.view\">Settings</a>")
                .contains("<a href=\"https://example.com/\" rel=\"nofollow\">here</a>");
    }

    @Test
    void removesScriptsAndHandlers() {
        String html = MessageMarkup.toSafeHtml("<script>alert(1)</script><img src=x onerror=alert(1)>"
                + "<a href=\"javascript:alert(1)\" onclick=\"alert(1)\">x</a><b style=\"color:red\">y</b>");
        assertThat(html).doesNotContain("script").doesNotContain("onerror").doesNotContain("onclick")
                .doesNotContain("javascript").doesNotContain("<img").doesNotContain("style")
                .contains("<b>y</b>");
    }

    @Test
    void escapesPlainText() {
        assertThat(MessageMarkup.toSafeHtml("Tom & Jerry < 3")).isEqualTo("Tom &amp; Jerry &lt; 3");
    }
}
