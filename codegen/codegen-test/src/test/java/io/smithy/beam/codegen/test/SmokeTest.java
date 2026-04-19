package io.smithy.beam.codegen.test;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.smithy.beam.elixir.client.ElixirClientCodegenPlugin;
import io.smithy.beam.elixir.server.ElixirServerCodegenPlugin;
import io.smithy.beam.erlang.client.ErlangClientCodegenPlugin;
import io.smithy.beam.erlang.server.ErlangServerCodegenPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Smoke tests that run all four codegen plugins against the canonical
 * {@code weather.smithy} fixture and assert that no exception is thrown.
 *
 * <p>These tests exercise the full integration between {@code codegen-erlang}
 * and {@code codegen-elixir} on a shared classpath — verifying that both
 * language modules co-exist without SPI conflicts or classpath collisions.
 */
class SmokeTest {

    private static Model weatherModel() {
        URL resource = SmokeTest.class.getResource("/model/weather.smithy");
        if (resource == null) {
            throw new IllegalStateException("weather.smithy not found on test classpath");
        }
        return Model.assembler()
                .addImport(resource)
                .assemble()
                .unwrap();
    }

    private static PluginContext erlangClientContext(Model model) {
        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.weather#Weather")
                .withMember("module", "weather")
                .withMember("edition", "2025")
                .build();
        return PluginContext.builder()
                .fileManifest(new MockManifest())
                .model(model)
                .settings(settings)
                .build();
    }

    private static PluginContext elixirClientContext(Model model) {
        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.weather#Weather")
                .withMember("namespace", "Weather")
                .withMember("edition", "2025")
                .build();
        return PluginContext.builder()
                .fileManifest(new MockManifest())
                .model(model)
                .settings(settings)
                .build();
    }

    @Test
    void erlangClientRunsWithoutError() {
        Model model = weatherModel();
        assertThatCode(() -> new ErlangClientCodegenPlugin().execute(erlangClientContext(model)))
                .doesNotThrowAnyException();
    }

    @Test
    void erlangServerRunsWithoutError() {
        Model model = weatherModel();
        assertThatCode(() -> new ErlangServerCodegenPlugin().execute(erlangClientContext(model)))
                .doesNotThrowAnyException();
    }

    @Test
    void elixirClientRunsWithoutError() {
        Model model = weatherModel();
        assertThatCode(() -> new ElixirClientCodegenPlugin().execute(elixirClientContext(model)))
                .doesNotThrowAnyException();
    }

    @Test
    void elixirServerRunsWithoutError() {
        Model model = weatherModel();
        assertThatCode(() -> new ElixirServerCodegenPlugin().execute(elixirClientContext(model)))
                .doesNotThrowAnyException();
    }
}
