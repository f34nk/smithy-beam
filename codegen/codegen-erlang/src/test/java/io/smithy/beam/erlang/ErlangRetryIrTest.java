package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangRetryIrTest {
  private static final ShapeId RETRY_SERVICE = ShapeId.from("smithy.beam.demo.retry#RetryService");

  @Test
  void withRetryFunctionsMatchGolden() throws IOException {
    String combined = IrGoldenAssertions.renderFunctions(ErlangRetryIr.withRetryFunctions());
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/retry_with_retry.expected.erl"));
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
