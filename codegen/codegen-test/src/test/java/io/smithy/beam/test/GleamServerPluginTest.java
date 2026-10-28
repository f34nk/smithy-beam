package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.gleam.GleamServerPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamServerPluginTest {

  private static Model loadModel() {
    URL resource = GleamServerPluginTest.class.getResource("/model/basic.smithy");
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
  void emitsTypesBehaviourImplServerAndRouterWithOperationNames() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();

    new GleamServerPlugin().execute(buildContext(model, manifest));

    assertThat(manifest.expectFileString("basic_service_types.gleam"))
        .contains("pub type BasicString = String");

    String behaviour = manifest.expectFileString("basic_service_behaviour.gleam");
    assertThat(behaviour)
        .contains("pub type HandleGetTypeClosure =")
        .contains("pub type HandleListBasicItems =")
        .contains("fn(basic_service_types.GetTypeClosureInput) -> Result(");

    String impl = manifest.expectFileString("basic_service_impl.gleam");
    assertThat(impl)
        .contains("pub fn handle_get_type_closure(")
        .contains("pub fn handle_list_basic_items(")
        .contains("todo");

    String server = manifest.expectFileString("basic_service_server.gleam");
    assertThat(server).contains("pub fn start(").contains("ServerConfig");

    String router = manifest.expectFileString("basic_service_router.gleam");
    assertThat(router)
        .contains("pub fn route(")
        .contains("pub type Handlers")
        .contains("parse_labels(")
        .contains("Response(BitArray)")
        .doesNotContain("panic")
        .doesNotContain("let assert")
        .doesNotContain("Error(\"not_implemented\")");

    assertThat(manifest.getFileString("basic_service_rest_json_1.gleam")).isPresent();
  }
}
