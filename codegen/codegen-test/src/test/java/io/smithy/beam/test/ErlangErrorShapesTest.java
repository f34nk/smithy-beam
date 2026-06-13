package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangErrorShapesTest {

    @Test
    void errorShapesGenerateRecordsWithKindField() {
        URL resource = ErlangErrorShapesTest.class
                .getResource("/model/error_shapes.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.error_shapes#ErrorFixtureService")
                .withMember("edition", "2026")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String types = manifest.expectFileString("error_fixture_service_types.hrl");
        assertThat(types).contains("-record(not_found_error,");
        assertThat(types).contains("-record(validation_error,");
        assertThat(types).contains("field_name :: binary()");
        assertThat(types).contains("-type validation_error() :: #validation_error{}.");
        assertThat(types).contains("-record(throttling_error,");
        assertThat(types).contains("'__beam_error_kind'");
        assertThat(types).contains("client");
        assertThat(types).contains("server");
    }
}
