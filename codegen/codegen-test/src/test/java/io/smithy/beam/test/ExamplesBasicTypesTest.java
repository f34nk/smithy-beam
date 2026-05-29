package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExamplesBasicTypesTest {

  private static final Path EXAMPLES_BASIC_MODEL =
      Path.of("../../examples/model/basic.smithy").toAbsolutePath().normalize();

  private static Model loadExamplesBasicModel() {
    return Model.assembler()
        .addImport(EXAMPLES_BASIC_MODEL)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String generateTypesHeader() {
    Model model = loadExamplesBasicModel();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.basic#BasicService")
            .withMember("edition", "2026")
            .build();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());
    return manifest.expectFileString("basic_types.hrl");
  }

  @Test
  void examplesBasicModelEmitsErrorRecordWithKindField() {
    String content = generateTypesHeader();
    assertThat(content)
        .contains("%% Error shape: smithy.beam.demo.basic#BasicNotFound (client)")
        .contains("-record(basic_not_found, {")
        .contains("'__beam_error_kind' = client :: client | server")
        .contains("-type basic_not_found() :: #basic_not_found{}.");
  }
}
