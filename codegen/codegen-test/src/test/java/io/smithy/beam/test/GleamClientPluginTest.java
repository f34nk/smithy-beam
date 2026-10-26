package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.gleam.GleamClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamClientPluginTest {

  private static final String TYPES_FILE = "basic_service_types.gleam";
  private static final String CLIENT_FILE = "basic_service_client.gleam";

  private static Model loadModel() {
    URL resource = GleamClientPluginTest.class.getResource("/model/basic.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static PluginContext buildContext(Model model, FileManifest manifest) {
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.basic#BasicService")
            .withMember("edition", "2026")
            .build();
    return PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
  }

  @Test
  void emitsTypesAndClientStubWithOperationNames() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();

    new GleamClientPlugin().execute(buildContext(model, manifest));

    assertThat(manifest.expectFileString(TYPES_FILE)).contains("pub type BasicString = String");

    String client = manifest.expectFileString(CLIENT_FILE);
    assertThat(client)
        .contains("import basic_service_types")
        .contains("pub type ClientConfig {")
        .contains("pub type ClientError {")
        .contains("  NotImplemented")
        .contains("pub fn get_type_closure(")
        .contains("pub fn list_basic_items(")
        .contains("Error(NotImplemented)")
        .contains("Result(basic_service_types.GetTypeClosureOutput, ClientError)")
        .contains("Result(basic_service_types.ListBasicItemsOutput, ClientError)");
  }

  @Test
  void stubOnlyServiceWithoutProtocolTraitEmitsClient() {
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
                    input: PingInput
                    output: PingOutput
                }

                structure PingInput {}
                structure PingOutput {}
                """)
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "com.example#StubService")
            .withMember("edition", "2026")
            .build();
    new GleamClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

    assertThat(manifest.expectFileString("stub_service_types.gleam")).contains("pub type PingInput");
    assertThat(manifest.expectFileString("stub_service_client.gleam"))
        .contains("pub fn ping(")
        .contains("Error(NotImplemented)");
  }
}
