package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class SigV4SigningTest {

  private static final ShapeId SERVICE = ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  private static Model loadModel() {
    URL resource = SigV4SigningTest.class.getResource("/model/sigv4_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void beamSigV4MetadataReadsTraitValues() {
    Model model = loadModel();
    ServiceShape service = model.expectShape(SERVICE, ServiceShape.class);

    Optional<BeamSigV4Metadata> meta = BeamSigV4Metadata.from(service);

    assertThat(meta).isPresent();
    assertThat(meta.get().signingName()).isEqualTo("sigv4test");
    assertThat(meta.get().unsignedPayload()).isFalse();
  }

  @Test
  void erlangClientEmitsSigV4SigningModule() {
    MockManifest manifest = runErlangClient();

    assertThat(manifest.getFileString("sigv4test_service_sigv4.erl")).isEmpty();
    assertThat(manifest.getFileString("sigv4test_service_presigner.erl")).isEmpty();

    String client = manifest.expectFileString("sigv4test_service_client.erl");
    assertThat(client).contains("aws_sigv4:sign(Config, ping, Req)");
  }

  @Test
  void elixirClientEmitsSigV4SigningModule() {
    MockManifest manifest = runElixirClient();

    assertThat(manifest.getFileString("sigv4test_service_sigv4.ex")).isEmpty();
    assertThat(manifest.getFileString("sigv4test_service_presigner.ex")).isEmpty();
    assertThat(manifest.getFileString("aws_sigv4.ex")).isPresent();

    String client = manifest.expectFileString("sigv4test_service_client.ex");
    assertThat(client).contains("AwsSigv4.sign(config, :ping, req)");
  }

  @Test
  void basicServiceOmitsSigV4Module() {
    URL resource = SigV4SigningTest.class.getResource("/model/basic.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.basic#BasicService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    assertThat(manifest.expectFileString("basic_service_client.erl"))
        .doesNotContain("aws_sigv4:sign(");
  }

  private static MockManifest runErlangClient() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static MockManifest runElixirClient() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }
}
