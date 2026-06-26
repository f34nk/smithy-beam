package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangHttpChecksumIrTest {
    @Test
    void checksumHelperFunctionsMatchGolden() throws IOException {
        List<ErlFunction> functions = ErlangHttpChecksumIr.checksumHelperFunctions();
        assertThat(helpersAsString(functions)).isEqualTo(readExpectedString("ir/http_checksum_helpers.expected.erl"));
        for (ErlFunction fn : functions) {
            assertThat(fn.name()).isNotBlank();
            assertThat(fn.clauses()).isNotEmpty();
        }
    }

    private static String helpersAsString(List<ErlFunction> functions) {
        return functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangHttpChecksumIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
