package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirRetryIrTest {
  private static final ShapeId RETRY_SERVICE = ShapeId.from("smithy.beam.demo.retry#RetryService");

  @Test
  void withRetryFunctionsMatchGolden() throws IOException {
    String combined =
        ElixirRetryIr.withRetryFunctions().stream()
            .map(ExFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/retry_with_retry.expected.ex"));
    for (ExFunction fn : ElixirRetryIr.withRetryFunctions()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void retryModuleMatchesGolden() throws IOException {
    Model model = retryModel();
    ServiceShape service = model.expectShape(RETRY_SERVICE, ServiceShape.class);
    ExModule module = ElixirRetryIr.retryModule(testContext(model, service), service, model, sp(model, service));
    assertThat(module.asString()).isEqualTo(readExpectedString("ir/retry_module.expected.ex"));
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

  private static ElixirContext testContext(Model model, ServiceShape service) {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider symbolProvider = sp(model, service);
    MockManifest manifest = new MockManifest();
    return new ElixirContext(
        model,
        settings,
        symbolProvider,
        manifest,
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("retry")),
        java.util.List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "retry",
        "retry.ex");
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
