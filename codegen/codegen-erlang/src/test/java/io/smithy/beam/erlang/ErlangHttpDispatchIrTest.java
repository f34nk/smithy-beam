package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangHttpDispatchIrTest {
    @Test
    void splitBaseUrlAsStringMatchesGolden() throws IOException {
        ErlFunction fn = ErlangHttpDispatchIr.splitBaseUrl();
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/http_dispatch_split_base_url.expected.erl"));
    }

    @Test
    void mimeAsStringMatchesGolden() throws IOException {
        ErlFunction fn = ErlangHttpDispatchIr.mime();
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/http_dispatch_mime.expected.erl"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangHttpDispatchIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
