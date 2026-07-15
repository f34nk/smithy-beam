package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamElixirStaticRuntimeIndexTest {

  @Test
  void basicClientRequiresHttpRuntime() {
    Model model = load("/model/protocol_rest_json_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamElixirStaticRuntimeModule::moduleName)
        .contains("RuntimeHttp", "RuntimeUtils", "RuntimeTypes");
    assertThat(requirements.modules())
        .extracting(BeamElixirStaticRuntimeModule::moduleName)
        .doesNotContain("AwsSigv4", "HttpChecksum", "AwsEventStream");
  }

  @Test
  void sigv4ClientAddsSigningModule() {
    Model model = load("/model/sigv4_unsigned_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.sigv4#Sigv4UnsignedTestService"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamElixirStaticRuntimeModule::moduleName)
        .contains("AwsSigv4");
  }

  @Test
  void serverOmitsClientDispatchRuntime() {
    Model model = load("/model/protocol_rest_json_fixture.smithy");
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson"), ServiceShape.class);

    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forServer(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamElixirStaticRuntimeModule::moduleName)
        .containsExactly("RuntimeTypes");
    assertThat(requirements.modules())
        .extracting(BeamElixirStaticRuntimeModule::moduleName)
        .doesNotContain("RuntimeHttp", "AwsSigv4", "RuntimeUtils");
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
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forClient(model, service, settings);

    assertThat(requirements.modules())
        .extracting(BeamElixirStaticRuntimeModule::moduleName)
        .contains("HttpChecksum")
        .doesNotContain("AwsSigv4");
  }

  private static Model load(String resourcePath) {
    URL resource = BeamElixirStaticRuntimeIndexTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }
}
