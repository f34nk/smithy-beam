package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamAwsServiceMetadata;
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

class AwsServiceMetadataTest {

  private static final ShapeId FULL_SERVICE =
      ShapeId.from("smithy.beam.test.awsservice#AwsMetadataService");
  private static final ShapeId MINIMAL_SERVICE =
      ShapeId.from("smithy.beam.test.awsservice#MinimalAwsService");

  private static Model loadAwsModel() {
    URL resource = AwsServiceMetadataTest.class.getResource("/model/aws_service_metadata.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static Model loadBasicModel() {
    URL resource = AwsServiceMetadataTest.class.getResource("/model/basic.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void beamAwsServiceMetadataReadsTraitValues() {
    Model model = loadAwsModel();
    ServiceShape service = model.expectShape(FULL_SERVICE, ServiceShape.class);

    Optional<BeamAwsServiceMetadata> meta = BeamAwsServiceMetadata.from(service);

    assertThat(meta).isPresent();
    assertThat(meta.get().sdkId()).isEqualTo("TestSdk");
    assertThat(meta.get().endpointPrefix()).isEqualTo("testprefix");
    assertThat(meta.get().signingName()).isEqualTo("testsign");
  }

  @Test
  void beamAwsServiceMetadataUsesSmithyDefaultsForOmittedMembers() {
    Model model = loadAwsModel();
    ServiceShape service = model.expectShape(MINIMAL_SERVICE, ServiceShape.class);

    Optional<BeamAwsServiceMetadata> meta = BeamAwsServiceMetadata.from(service);

    assertThat(meta).isPresent();
    assertThat(meta.get().sdkId()).isEqualTo("OnlySdk");
    assertThat(meta.get().endpointPrefix()).isEqualTo("minimalawsservice");
    assertThat(meta.get().signingName()).isEqualTo("minimalawsservice");
  }

  @Test
  void erlangClientEmitsAwsMetadataHelpers() {
    MockManifest manifest = runErlangClient(FULL_SERVICE);
    String client = manifest.expectFileString("aws_metadata_service_client.erl");

    assertThat(client).contains("%%   sdkId: TestSdk");
    assertThat(client).contains("%%   endpointPrefix: testprefix");
    assertThat(client).contains("default_config() ->");
    assertThat(client).contains("endpoint_prefix => <<\"testprefix\">>");
    assertThat(client).contains("signing_name => <<\"testsign\">>");
    assertThat(client).doesNotContain("resolve_base_url");

    String helpers = manifest.expectFileString("runtime_helpers.erl");
    assertThat(helpers).contains("resolve_base_url(Config) ->");
    assertThat(helpers)
        .contains("<<\"https://\", Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>");
  }

  @Test
  void elixirClientEmitsAwsMetadataHelpers() {
    MockManifest manifest = runElixirClient(FULL_SERVICE);
    String client = manifest.expectFileString("aws_metadata_service_client.ex");

    assertThat(client).contains("#   sdkId: TestSdk");
    assertThat(client).contains("#   endpointPrefix: testprefix");
    assertThat(client).contains("def default_config do");
    assertThat(client).contains("endpoint_prefix: \"testprefix\"");
    assertThat(client).contains("signing_name: \"testsign\"");
    assertThat(client).doesNotContain("def resolve_base_url");

    String helpers = manifest.expectFileString("runtime_helpers.ex");
    assertThat(helpers).contains("def resolve_base_url(config) do");
    assertThat(helpers).contains("\"https://#{prefix}.#{region}.amazonaws.com\"");
  }

  @Test
  void erlangClientOmitsAwsMetadataWithoutServiceTrait() {
    MockManifest manifest = runErlangClient(ShapeId.from("smithy.beam.demo.basic#BasicService"));
    String client = manifest.expectFileString("basic_service_client.erl");

    assertThat(client).contains("-type client_config() :: #{binary() => term()}.");
    assertThat(client).doesNotContain("default_config()");
    assertThat(client).doesNotContain("resolve_base_url");
  }

  @Test
  void elixirClientOmitsAwsMetadataWithoutServiceTrait() {
    MockManifest manifest = runElixirClient(ShapeId.from("smithy.beam.demo.basic#BasicService"));
    String client = manifest.expectFileString("basic_service_client.ex");

    assertThat(client).contains("@type client_config :: map()");
    assertThat(client).doesNotContain("def default_config");
    assertThat(client).doesNotContain("def resolve_base_url");
  }

  private static MockManifest runErlangClient(ShapeId serviceId) {
    Model model =
        serviceId.getNamespace().equals("smithy.beam.demo.basic")
            ? loadBasicModel()
            : loadAwsModel();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", serviceId.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static MockManifest runElixirClient(ShapeId serviceId) {
    Model model =
        serviceId.getNamespace().equals("smithy.beam.demo.basic")
            ? loadBasicModel()
            : loadAwsModel();
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", serviceId.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }
}
