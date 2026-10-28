package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamRouterDslTest {

  @Test
  void emitsRestJsonRouterWithHandlersLabelParsingAndNoPanic() {
    Model model =
        Model.assembler()
            .addImport(getClass().getResource("/model/protocol_rest_json_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
            .withMember("edition", "2026")
            .build();
    new GleamServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

    String router = manifest.expectFileString("demo_rest_json_router.gleam");
    assertThat(router)
        .contains("pub type Handlers")
        .contains("pub fn route(")
        .contains("Request(BitArray)")
        .contains("Response(BitArray)")
        .contains("parse_labels(")
        .contains("fn match_segments(")
        .doesNotContain("panic")
        .doesNotContain("let assert");

    assertThat(manifest.getFileString("demo_rest_json_rest_json_1.gleam")).isPresent();
  }
}
