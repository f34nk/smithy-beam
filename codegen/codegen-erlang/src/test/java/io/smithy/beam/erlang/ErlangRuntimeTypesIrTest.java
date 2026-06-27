package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangRuntimeTypesIrTest {

    @Test
    void runtimeTypesHeaderMatchesResource() throws IOException {
        String expected = loadResource("runtime_types.hrl");
        assertThat(ErlangRuntimeTypesIr.runtimeTypesHeader(
                        "runtime_types", Optional.empty(), Optional.empty())
                .asString())
                .isEqualTo(stripTrailingNewline(expected));
    }

    @Test
    void runtimeTypesHeaderAppendsEndpointRuleSetDefine() {
        String map = "#{'argv' => [<<\"us-east-1\">>]}";
        String output = ErlangRuntimeTypesIr.runtimeTypesHeader(
                        "runtime_types", Optional.of(map), Optional.empty())
                .asString();
        assertThat(output)
                .contains("%% @endpointRuleSet embedded at codegen time.")
                .contains("-type endpoint_rule_set() :: map().")
                .contains("-define(ENDPOINT_RULE_SET, " + map + ").");
    }

    private static String loadResource(String name) throws IOException {
        try (InputStream in = ErlangRuntimeTypesIrTest.class.getResourceAsStream("/" + name)) {
            assertThat(in).as("resource %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String stripTrailingNewline(String text) {
        if (text.endsWith("\n")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }
}
