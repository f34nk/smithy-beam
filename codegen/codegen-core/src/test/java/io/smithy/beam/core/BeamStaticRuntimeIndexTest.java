package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamStaticRuntimeIndexTest {

  @Test
  void basicClientRequiresHttpRuntime() {
    Model model = load("/model/protocol_rest_json_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamStaticRuntimeIndex.Requirements requirements =
        BeamStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamStaticRuntimeModule::moduleName)
        .contains("runtime_http", "utils", "runtime_types");
    assertThat(requirements.modules())
        .extracting(BeamStaticRuntimeModule::moduleName)
        .doesNotContain("aws_sigv4", "http_checksum", "aws_event_stream");
  }

  @Test
  void sigv4ClientAddsSigningModule() {
    Model model = load("/model/sigv4_unsigned_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.sigv4#Sigv4UnsignedTestService"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamStaticRuntimeIndex.Requirements requirements =
        BeamStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamStaticRuntimeModule::moduleName)
        .contains("aws_sigv4");
  }

  @Test
  void serverOmitsClientDispatchRuntime() {
    Model model = load("/model/protocol_rest_json_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamStaticRuntimeIndex.Requirements requirements =
        BeamStaticRuntimeIndex.forServer(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamStaticRuntimeModule::moduleName)
        .containsExactly("runtime_types");
    assertThat(requirements.modules())
        .extracting(BeamStaticRuntimeModule::moduleName)
        .doesNotContain("runtime_http", "aws_sigv4", "utils");
  }

  @Test
  void checksumClientDoesNotEmitSigV4Module() {
    Model model = load("/model/http_checksum_service_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.checksum#HttpChecksumRestJsonService"),
            ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamStaticRuntimeIndex.Requirements requirements =
        BeamStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamStaticRuntimeModule::moduleName)
        .contains("http_checksum")
        .doesNotContain("aws_sigv4");
  }

  private static Model load(String resourcePath) {
    URL resource = BeamStaticRuntimeIndexTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }
}
