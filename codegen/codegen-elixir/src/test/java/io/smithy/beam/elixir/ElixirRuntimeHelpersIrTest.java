package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirRuntimeHelpersIrTest {
  private static final String LABEL_SERVICE = "smithy.beam.demo.labels#LabelService";

  @Test
  void labelParsingFunctionsMatchGolden() throws IOException {
    List<ExFunction> functions = ElixirRuntimeHelpersIr.labelParsingFunctions();
    assertThat(functions).hasSize(4);
    for (ExFunction fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String combined =
        functions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n"));
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/runtime_helpers_label_parsing.expected.ex"));
  }

  @Test
  void resolveBaseUrlAsStringMatchesGolden() throws IOException {
    assertThat(ElixirRuntimeHelpersIr.resolveBaseUrl().asString())
        .isEqualTo(readExpectedString("ir/runtime_helpers_resolve_base_url.expected.ex"));
  }

  @Test
  void labelBindingsModuleMatchesGolden() throws IOException {
    Model model = labelModel();
    ServiceShape service = model.expectShape(ShapeId.from(LABEL_SERVICE), ServiceShape.class);
    ExModule module =
        ElixirRuntimeHelpersIr.runtimeHelpersModule(testContext(model, service), service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/runtime_helpers_label_module.expected.ex"));
  }

  private static ElixirContext testContext(Model model, ServiceShape service) {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    MockManifest manifest = new MockManifest();
    return new ElixirContext(
        model,
        settings,
        null,
        manifest,
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("runtime_helpers")),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "runtime_helpers",
        "runtime_helpers.ex");
  }

  private static Model labelModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.labels

                use aws.protocols#restJson1

                string ItemId

                @restJson1
                service LabelService {
                    version: "2026"
                    operations: [GetItem]
                }

                @readonly
                @http(method: "GET", uri: "/items/{id}", code: 200)
                operation GetItem {
                    input: GetItemInput
                    output: GetItemOutput
                }

                structure GetItemInput {
                    @required
                    @httpLabel
                    id: ItemId
                }

                structure GetItemOutput {
                    id: ItemId
                }
                """;
    return Model.assembler()
        .addUnparsedModel("labels.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirRuntimeHelpersIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
