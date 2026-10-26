package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.gleam.GleamTypesPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamTypesPluginTest {

  private static final String TYPES_FILE = "basic_service_types.gleam";

  private static Model loadModel() {
    URL resource = GleamTypesPluginTest.class.getResource("/model/basic.smithy");
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
  void generatesExpectedTypesForBasicServiceClosure() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();

    new GleamTypesPlugin().execute(buildContext(model, manifest));

    String content = manifest.expectFileString(TYPES_FILE);

    assertThat(content)
        .contains("//// Type definitions for the basic_service_types model.")
        .contains("import gleam/time/timestamp")
        .contains("import gleam/dynamic")
        .contains("import gleam/dict.{type Dict}")
        .contains("import gleam/option.{type Option}")
        .contains("pub type BasicString = String")
        .contains("pub type BasicInteger = Int")
        .contains("pub type BasicLong = Int")
        .contains("pub type BasicFloat = Float")
        .contains("pub type BasicBoolean = Bool")
        .contains("pub type BasicBlob = BitArray")
        .contains("pub type BasicByte = Int")
        .contains("pub type BasicShort = Int")
        .contains("pub type BasicDouble = Float")
        .contains("pub type BasicBigInteger = Int")
        .contains("pub type BasicTimestamp = timestamp.Timestamp")
        .contains("pub type BasicDocument = dynamic.Dynamic")
        .contains("pub type BasicList = List(BasicString)")
        .contains("pub type BasicMap = Dict(BasicString, BasicString)")
        .contains("pub type BasicStatus {")
        .contains("  Active")
        .contains("  Inactive")
        .contains("  Pending")
        .contains("pub type BasicPriority {")
        .contains("  Low")
        .contains("  Medium")
        .contains("  High")
        .contains("pub type BasicUnion {")
        .contains("  Text(BasicString)")
        .contains("  Number(BasicInteger)")
        .contains("  Flag(BasicBoolean)")
        .contains("pub type BasicItem {")
        .contains("  BasicItem(name: BasicString, count: Option(BasicInteger))")
        .contains("pub type BasicNotFound {");
  }
}
