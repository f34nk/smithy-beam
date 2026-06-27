package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangHttpDispatchEmitterTest {

  private static final String SERVICE = "smithy.beam.demo.http#HttpService";

  private static Model httpModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.http

                use aws.protocols#restJson1

                string Name

                @restJson1
                service HttpService {
                    version: "2026"
                    operations: [GetName]
                }

                @readonly
                @http(method: "GET", uri: "/names/{name}", code: 200)
                operation GetName {
                    input: GetNameInput
                    output: GetNameOutput
                }

                structure GetNameInput {
                    @required
                    @httpLabel
                    name: Name
                }

                structure GetNameOutput {
                    name: Name
                }
                """;
    return Model.assembler()
        .addUnparsedModel("http.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String generateHttpModule() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(httpModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest.expectFileString("runtime_http.erl");
  }

  @Test
  void splitBaseUrlIsNotNestedUnderDispatchSigned() {
    String http = generateHttpModule();
    int dispatchEnd = http.indexOf("end.");
    assertThat(dispatchEnd).isGreaterThan(0);

    int splitBaseUrl = http.indexOf("split_base_url(<<>>) ->", dispatchEnd);
    assertThat(splitBaseUrl).isGreaterThan(dispatchEnd);
    assertThat(http.charAt(splitBaseUrl)).isEqualTo('s');
    assertThat(http.substring(splitBaseUrl - 1, splitBaseUrl)).isEqualTo("\n");

    int mime = http.indexOf("mime(Headers) ->", splitBaseUrl);
    assertThat(mime).isGreaterThan(splitBaseUrl);
    assertThat(http.charAt(mime)).isEqualTo('m');
  }
}
