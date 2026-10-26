package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamGleamStaticRuntimeIndexTest {

  @Test
  void basicClientRequiresHttpHelpersAndHttpc() {
    Model model = load("/model/protocol_rest_json_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamGleamStaticRuntimeIndex.Requirements requirements =
        BeamGleamStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamGleamStaticRuntimeModule::moduleName)
        .contains(
            "smithy_beam/types",
            "smithy_beam/utils",
            "smithy_beam/http",
            "smithy_beam/httpc");
  }

  @Test
  void serverOmitsHttpcConvenience() {
    Model model = load("/model/protocol_rest_json_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamGleamStaticRuntimeIndex.Requirements requirements =
        BeamGleamStaticRuntimeIndex.forServer(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamGleamStaticRuntimeModule::moduleName)
        .containsExactly("smithy_beam/types", "smithy_beam/utils");
    assertThat(requirements.modules())
        .extracting(BeamGleamStaticRuntimeModule::moduleName)
        .doesNotContain("smithy_beam/http", "smithy_beam/httpc");
  }

  private static Model load(String resourcePath) {
    URL resource = BeamGleamStaticRuntimeIndexTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }
}
