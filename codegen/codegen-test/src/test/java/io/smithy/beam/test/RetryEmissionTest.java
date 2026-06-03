package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class RetryEmissionTest {

    private static final String SERVICE = "smithy.beam.demo.error_shapes#ErrorFixtureService";

    private Model errorFixtureModel() {
        URL resource = RetryEmissionTest.class.getResource("/model/error_shapes.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangRetryModuleWrapsRetryableErrors() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(errorFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String retry = manifest.getFileString("error_fixture_service_retry.erl").orElse("");
        assertThat(retry).contains("-module(error_fixture_service_retry).");
        assertThat(retry).contains("with_retry/2");
        assertThat(retry).contains("should_retry({error, #not_found_error{}}) -> true;");
        assertThat(retry).contains("should_retry({error, #throttling_error{}}) -> true;");
        assertThat(retry).contains("timer:sleep(trunc(Base * math:pow(2, N - 1))),");
    }

    @Test
    void elixirRetryModuleWrapsRetryableErrors() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(errorFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String retry = manifest.getFileString("error_fixture_service_retry.ex").orElse("");
        assertThat(retry).contains("defmodule ErrorFixtureServiceRetry");
        assertThat(retry).contains("def with_retry");
        assertThat(retry).contains("def should_retry?({:error, %ErrorShapesTypes.NotFoundError{}}), do: true");
        assertThat(retry).contains("def should_retry?({:error, %ErrorShapesTypes.ThrottlingError{}}), do: true");
        assertThat(retry).contains("Process.sleep(trunc(base * :math.pow(2, n - 1)))");
    }
}
