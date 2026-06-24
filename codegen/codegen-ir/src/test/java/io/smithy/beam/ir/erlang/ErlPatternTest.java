package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlPatternTest {
    @Test
    void functionHeadPatternsAsString() {
        List<ErlPattern> patterns = ErlPattern.functionHeadPatterns(List.of(
                "#http_request{query = Query, headers = Headers, body = Body}",
                "LabelMap"));
        assertThat(patterns.get(0).asString())
                .isEqualTo("#http_request{query = Query, headers = Headers, body = Body}");
        assertThat(patterns.get(1).asString()).isEqualTo("LabelMap");
    }
}
