package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangEventStreamIrTest {
    @Test
    void encodeEventHeadersAsStringMatchesGolden() throws IOException {
        assertThat(ErlangEventStreamIr.encodeEventHeaders().asString())
                .isEqualTo(readExpectedString("ir/event_stream_encode_event_headers.expected.erl"));
    }

    @Test
    void headerValueAsStringMatchesGolden() throws IOException {
        assertThat(ErlangEventStreamIr.headerValue().asString())
                .isEqualTo(readExpectedString("ir/event_stream_header_value.expected.erl"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangEventStreamIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
