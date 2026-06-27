package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ErlangRuntimeHelpersIrTest {
  private static final String LABEL_SERVICE = "smithy.beam.demo.labels#LabelService";

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

  @Test
  void labelParsingFunctionsMatchGolden() throws IOException {
    List<ErlFunction> functions = ErlangRuntimeHelpersIr.labelParsingFunctions();
    assertThat(functions).hasSize(4);
    for (ErlFunction fn : functions) {
      assertThat(fn.name()).isNotBlank();
      assertThat(fn.clauses()).isNotEmpty();
    }
    String combined =
        functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/runtime_helpers_label_parsing.expected.erl"));
  }

  @Test
  void resolveBaseUrlAsStringMatchesGolden() throws IOException {
    assertThat(ErlangRuntimeHelpersIr.resolveBaseUrl().asString())
        .isEqualTo(readExpectedString("ir/runtime_helpers_resolve_base_url.expected.erl"));
  }

  @Test
  void labelBindingsModuleMatchesGolden() throws IOException {
    Model model = labelModel();
    ServiceShape service = model.expectShape(ShapeId.from(LABEL_SERVICE), ServiceShape.class);
    ErlModule module =
        ErlangRuntimeHelpersIr.runtimeHelpersModule(
            "runtime_helpers", service, model, false, true, false);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/runtime_helpers_label_module.expected.erl"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangRuntimeHelpersIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
