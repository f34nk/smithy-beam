package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirRetryIrTest {
  private static final ShapeId RETRY_SERVICE = ShapeId.from("smithy.beam.demo.retry#RetryService");

  @Test
  void clientPredicateFunctionsMatchGolden() throws IOException {
    Model model = retryModel();
    ServiceShape service = model.expectShape(RETRY_SERVICE, ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp = sp(model, service);
    List<Function> functions = ElixirRetryIr.clientPredicateFunctions(model, service, sp, layout);
    assertThat(functions).hasSize(5);
    String combined =
        functions.stream().map(ElixirRenderer::renderFunction).collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/retry_client_predicates.expected.ex"));
    for (Function fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  private static ElixirSymbolProvider sp(Model model, ServiceShape service) {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    return new ElixirSymbolProvider(
        settings,
        model,
        service,
        layout.typesModuleFile(),
        ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
        BeamCodegenKind.CLIENT);
  }

  private static Model retryModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.retry

                use smithy.api#error
                use smithy.api#String
                use smithy.api#retryable

                @error("client")
                @retryable
                structure RetryableError {
                    message: String
                }

                structure Empty {}

                service RetryService {
                    version: "2026"
                    operations: [Call]
                }

                @readonly
                operation Call {
                    input: Empty
                    output: Empty
                    errors: [RetryableError]
                }
                """;
    return Model.assembler().addUnparsedModel("retry.smithy", idl).assemble().unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirRetryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
