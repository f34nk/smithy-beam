package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamGleamStaticRuntimeEmitter;
import io.smithy.beam.core.BeamGleamStaticRuntimeIndex;
import io.smithy.beam.core.BeamSettings;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamGleamStaticRuntimeEmitterTest {

  @Test
  void copiesSelectedModulesIntoManifestUnderPackagePath() {
    MockManifest manifest = new MockManifest();
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "stub.smithy",
                """
                $version: "2"
                namespace com.example

                service StubService {
                    operations: [Ping]
                }

                operation Ping {
                    input := {}
                    output := {}
                }
                """)
            .assemble()
            .unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#StubService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamGleamStaticRuntimeIndex.Requirements requirements =
        BeamGleamStaticRuntimeIndex.forClient(model, service, settings);

    BeamGleamStaticRuntimeEmitter.emit(
        manifest, BeamGleamStaticRuntimeEmitterTest.class.getClassLoader(), requirements);

    assertThat(manifest.getFileString("smithy_beam/http.gleam")).isPresent();
    assertThat(manifest.getFileString("smithy_beam/utils.gleam")).isPresent();
    assertThat(manifest.getFileString("smithy_beam/types.gleam")).isPresent();
    assertThat(manifest.getFileString("smithy_beam/httpc.gleam")).isPresent();
    assertThat(manifest.expectFileString("smithy_beam/http.gleam"))
        .contains("pub fn apply_base_url");
    assertThat(manifest.expectFileString("smithy_beam/httpc.gleam"))
        .contains("pub fn send_bits");
  }
}
