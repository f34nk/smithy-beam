package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangRetryIrTest {
  private static final ShapeId RETRY_SERVICE = ShapeId.from("smithy.beam.demo.retry#RetryService");

  @Test
  void withRetryFunctionsMatchGolden() throws IOException {
    String combined = IrGoldenAssertions.renderFunctions(ErlangRetryIr.withRetryFunctions());
    assertThat(combined).isEqualTo(IrGoldenAssertions.readExpectedString("ir/retry_with_retry.expected.erl"));
  }

  @Test
  void retryModuleMatchesGolden() throws IOException {
    Model model = retryModel();
    ServiceShape service = model.expectShape(RETRY_SERVICE, ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, layout.typesHeaderFile(), BeamCodegenKind.CLIENT);
    Module module =
        ErlangRetryIr.retryModule(
            "retry_service_retry", "retry_service_types.hrl", service, model, sp);
    IrGoldenAssertions.assertGolden(module, "ir/retry_module.expected.erl");
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
}
