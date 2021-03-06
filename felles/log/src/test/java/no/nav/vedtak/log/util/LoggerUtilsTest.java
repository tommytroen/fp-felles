package no.nav.vedtak.log.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class LoggerUtilsTest {

    @Test
    public void removeLineBreaksSkalFjerneCR() {

        assertThat(LoggerUtils.removeLineBreaks("\r")).isEqualTo("");
        assertThat(LoggerUtils.removeLineBreaks("a\rb")).isEqualTo("ab");
        assertThat(LoggerUtils.removeLineBreaks("\ra\rb")).isEqualTo("ab");
    }

    @Test
    public void removeLineBreaksSkalFjerneLF() {

        assertThat(LoggerUtils.removeLineBreaks("\n")).isEqualTo("");
        assertThat(LoggerUtils.removeLineBreaks("a\nb")).isEqualTo("ab");
        assertThat(LoggerUtils.removeLineBreaks("\na\n\nb")).isEqualTo("ab");
    }

    @Test
    public void removeLineBreaksSkalTakleNull() {
        assertThat(LoggerUtils.removeLineBreaks(null)).isNull();
    }
}
