package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamElixirStaticRuntimeEmitter;
import io.smithy.beam.core.BeamElixirStaticRuntimeIndex;
import io.smithy.beam.core.BeamSettings;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamElixirStaticRuntimeEmitterTest {

  @Test
  void copiesOnlySelectedModulesIntoManifest() {
    MockManifest manifest = new MockManifest();
    Model model =
        Model.assembler()
            .addImport(getClass().getResource("/model/protocol_rest_json_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forClient(model, service, settings);

    BeamElixirStaticRuntimeEmitter.emit(
        manifest, BeamElixirStaticRuntimeEmitterTest.class.getClassLoader(), requirements);

    assertThat(manifest.getFileString("runtime_http.ex")).isPresent();
    assertThat(manifest.getFileString("runtime_types.ex")).isPresent();
    assertThat(manifest.getFileString("aws_sigv4.ex")).isEmpty();
    assertThat(manifest.expectFileString("runtime_types.ex")).contains("defmodule RuntimeTypes");
  }
}
