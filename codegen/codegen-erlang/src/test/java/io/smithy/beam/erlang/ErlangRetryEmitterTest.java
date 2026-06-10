package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangRetryEmitterTest {

    private static final String SERVICE = "smithy.beam.demo.retry#RetryService";

    private static Model retryModel() {
        String idl = """
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
        return Model.assembler()
                .addUnparsedModel("retry.smithy", idl)
                .assemble()
                .unwrap();
    }

    private static String generateRetryModule() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(retryModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest.expectFileString("retry_service_retry.erl");
    }

    @Test
    void withRetryOuterCaseEndAlignsWithCaseFun() {
        String retry = generateRetryModule();
        int outerCase = retry.indexOf("case Fun() of");
        assertThat(outerCase).isGreaterThanOrEqualTo(0);

        String tail = retry.substring(outerCase);
        assertThat(tail).contains("Err\n            end\n    end.");

        int retryableSpec = retry.indexOf("-spec retryable(term())");
        assertThat(retryableSpec).isGreaterThan(0);
        assertThat(retry.substring(retryableSpec, retryableSpec + 20)).startsWith("-spec retryable");
    }
}
