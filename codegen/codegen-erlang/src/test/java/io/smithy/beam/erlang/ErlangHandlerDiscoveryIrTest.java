package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangHandlerDiscoveryIrTest {
    @Test
    void resolveImplAsStringMatchesGolden() throws IOException {
        assertThat(ErlangHandlerDiscoveryIr.resolveImpl("basic_service_behaviour").asString())
                .isEqualTo(readExpectedString("ir/handler_discovery_resolve_impl.expected.erl"));
    }

    @Test
    void makeHandlerAsStringMatchesGolden() throws IOException {
        assertThat(ErlangHandlerDiscoveryIr.makeHandler().asString())
                .isEqualTo(readExpectedString("ir/handler_discovery_make_handler.expected.erl"));
    }

    @Test
    void initHandlersAsStringMatchesGolden() throws IOException {
        assertThat(ErlangHandlerDiscoveryIr.initHandlers().asString())
                .isEqualTo(readExpectedString("ir/handler_discovery_init_handlers.expected.erl"));
    }

    @Test
    void dispatchHandlerAsStringMatchesGolden() throws IOException {
        assertThat(ErlangHandlerDiscoveryIr.dispatchHandler().asString())
                .isEqualTo(readExpectedString("ir/handler_discovery_dispatch_handler.expected.erl"));
    }

    @Test
    void operationDispatchAsStringMatchesGolden() throws IOException {
        assertThat(ErlangHandlerDiscoveryIr.operationDispatch("handle_get_name").asString())
                .isEqualTo(readExpectedString("ir/handler_discovery_handle_get_name.expected.erl"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangHandlerDiscoveryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
