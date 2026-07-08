package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangRetryEmitterTest {

  private static final String SERVICE = "smithy.beam.demo.retry#RetryService";

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

  private static MockManifest generateClient() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(retryModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  @Test
  void retryPredicatesAppearInClientNotSeparateModule() {
    MockManifest manifest = generateClient();
    assertThat(manifest.getFileString("retry_service_retry.erl")).isEmpty();
    String client = manifest.expectFileString("retry_service_client.erl");
    assertThat(client).contains("-spec should_retry(term())");
    assertThat(client).contains("should_retry({error, #retryable_error{}}) -> true;");
    assertThat(client).doesNotContain("-module(retry_service_retry).");
  }
}
