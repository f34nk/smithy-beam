package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.core.BeamStaticRuntimeEmitter;
import io.smithy.beam.core.BeamStaticRuntimeIndex;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamStaticRuntimeEmitterTest {

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
        model.expectShape(ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamStaticRuntimeIndex.Requirements requirements =
        BeamStaticRuntimeIndex.forClient(model, service, settings);

    BeamStaticRuntimeEmitter.emit(
        manifest, BeamStaticRuntimeEmitterTest.class.getClassLoader(), requirements);

    assertThat(manifest.getFileString("runtime_http.erl")).isPresent();
    assertThat(manifest.getFileString("runtime_types.hrl")).isPresent();
    assertThat(manifest.getFileString("aws_sigv4.erl")).isEmpty();
    assertThat(manifest.expectFileString("runtime_types.hrl"))
        .contains("-record(http_request");
  }
}
