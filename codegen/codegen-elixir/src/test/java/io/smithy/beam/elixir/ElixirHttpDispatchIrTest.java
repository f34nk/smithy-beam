package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirHttpDispatchIrTest {
  private static final String ENDPOINTS_MOD = "Endpoints";
  private static final String CREDENTIALS_MOD = "Credentials";

  @Test
  void httpDispatchModuleAsStringMatchesGolden() throws IOException {
    Model model = httpDispatchModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    ExModule module = ElixirHttpDispatchIr.httpDispatchModule(testContext(model, service), service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/http_dispatch_module.expected.ex"));
  }

  @Test
  void splitBaseUrlAsStringMatchesGolden() throws IOException {
    assertThat(ElixirHostLabelIr.splitBaseUrl().asString())
        .isEqualTo(readExpectedString("ir/http_dispatch_split_base_url.expected.ex"));
  }

  @Test
  void dispatchArity2AsStringMatchesGolden() throws IOException {
    assertThat(ElixirHttpDispatchIr.dispatchArity2().asString())
        .isEqualTo(readExpectedString("ir/http_dispatch_dispatch_arity2.expected.ex"));
  }

  @Test
  void dispatchArity3AsStringMatchesGolden() throws IOException {
    assertThat(ElixirHttpDispatchIr.dispatchArity3().asString())
        .isEqualTo(readExpectedString("ir/http_dispatch_dispatch_arity3.expected.ex"));
  }

  @Test
  void dispatchSignedBasicAsStringMatchesGolden() throws IOException {
    assertThat(
            ElixirHttpDispatchIr.dispatchSigned(
                    false, false, "config", ENDPOINTS_MOD, CREDENTIALS_MOD)
                .asString())
        .isEqualTo(readExpectedString("ir/http_dispatch_dispatch_signed_basic.expected.ex"));
  }

  @Test
  void dispatchSignedSigv4AsStringMatchesGolden() throws IOException {
    assertThat(
            ElixirHttpDispatchIr.dispatchSigned(
                    true, false, "config1", ENDPOINTS_MOD, CREDENTIALS_MOD)
                .asString())
        .isEqualTo(readExpectedString("ir/http_dispatch_dispatch_signed_sigv4.expected.ex"));
  }

  @Test
  void dispatchSignedEndpointRulesAsStringMatchesGolden() throws IOException {
    assertThat(
            ElixirHttpDispatchIr.dispatchSigned(
                    false, true, "config", ENDPOINTS_MOD, CREDENTIALS_MOD)
                .asString())
        .isEqualTo(
            readExpectedString("ir/http_dispatch_dispatch_signed_endpoint_rules.expected.ex"));
  }

  @Test
  void dispatchSignedSigv4EndpointRulesAsStringMatchesGolden() throws IOException {
    assertThat(
            ElixirHttpDispatchIr.dispatchSigned(
                    true, true, "config1", ENDPOINTS_MOD, CREDENTIALS_MOD)
                .asString())
        .isEqualTo(
            readExpectedString(
                "ir/http_dispatch_dispatch_signed_sigv4_endpoint_rules.expected.ex"));
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
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("runtime_http")),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "runtime_http",
        "runtime_http.ex");
  }

  private static Model httpDispatchModel() {
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

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirHttpDispatchIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
