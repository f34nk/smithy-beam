package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class DeprecatedShapeFilterTest {

  private static final String SERVICE =
      "smithy.beam.demo.relative_deprecation#RelativeDeprecationService";
  private static final String TYPES_FILE = "relative_deprecation_service_types.hrl";

  private static Model loadModel() {
    URL resource =
        DeprecatedShapeFilterTest.class.getResource("/model/relative_deprecation.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static void executeClientPlugin(Model model, MockManifest manifest, ObjectNode settings) {
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
  }

  @Test
  void relativeDateRemovesDeprecatedShapesFromClientGeneration() {
    Model model = loadModel();

    MockManifest baseline = new MockManifest();
    executeClientPlugin(
        model,
        baseline,
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build());
    assertThat(baseline.expectFileString(TYPES_FILE))
        .contains("-type legacy_string() :: binary().");

    MockManifest filtered = new MockManifest();
    executeClientPlugin(
        model,
        filtered,
        ObjectNode.builder()
            .withMember("service", SERVICE)
            .withMember("edition", "2026")
            .withMember("relativeDate", "2026-01-01")
            .build());
    assertThat(filtered.expectFileString(TYPES_FILE))
        .doesNotContain("-type legacy_string() :: binary().");
  }

  @Test
  void relativeVersionRemovesDeprecatedShapesFromClientGeneration() {
    Model model = loadModel();

    MockManifest baseline = new MockManifest();
    executeClientPlugin(
        model,
        baseline,
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build());
    assertThat(baseline.expectFileString(TYPES_FILE))
        .contains("-type legacy_version_string() :: binary().");

    MockManifest filtered = new MockManifest();
    executeClientPlugin(
        model,
        filtered,
        ObjectNode.builder()
            .withMember("service", SERVICE)
            .withMember("edition", "2026")
            .withMember("relativeVersion", "1.0.0")
            .build());
    assertThat(filtered.expectFileString(TYPES_FILE))
        .doesNotContain("-type legacy_version_string() :: binary().");
  }
}
