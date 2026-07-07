package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.Module;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangHttpDispatchIrTest {
  private static final String HELPERS_MOD = "runtime_helpers";
  private static final String ENDPOINTS_MOD = "endpoints";

  @Test
  void httpDispatchModuleAsStringMatchesGolden() throws IOException {
    Model model = httpDispatchModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    Module module =
        ErlangHttpDispatchIr.httpDispatchModule(
            "runtime_http",
            "runtime_types.hrl",
            service,
            false,
            false,
            HELPERS_MOD,
            ENDPOINTS_MOD);
    IrGoldenAssertions.assertGolden(module, "ir/http_dispatch_module.expected.erl");
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

  @Test
  void splitBaseUrlAsStringMatchesGolden() throws IOException {
    Function fn = ErlangHttpDispatchIr.splitBaseUrl();
    IrGoldenAssertions.assertGolden(fn, "ir/http_dispatch_split_base_url.expected.erl");
  }

  @Test
  void mimeAsStringMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(ErlangHttpDispatchIr.mime(), "ir/http_dispatch_mime.expected.erl");
  }

  @Test
  void dispatchArity2AsStringMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangHttpDispatchIr.dispatchArity2(), "ir/http_dispatch_dispatch_arity2.expected.erl");
  }

  @Test
  void dispatchArity3AsStringMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangHttpDispatchIr.dispatchArity3(), "ir/http_dispatch_dispatch_arity3.expected.erl");
  }

  @Test
  void dispatchSignedBasicAsStringMatchesGolden() throws IOException {
    Function fn =
        ErlangHttpDispatchIr.dispatchSigned(false, false, HELPERS_MOD, ENDPOINTS_MOD);
    IrGoldenAssertions.assertGolden(fn, "ir/http_dispatch_dispatch_signed_basic.expected.erl");
  }

  @Test
  void dispatchSignedSigv4AsStringMatchesGolden() throws IOException {
    Function fn =
        ErlangHttpDispatchIr.dispatchSigned(true, false, HELPERS_MOD, ENDPOINTS_MOD);
    IrGoldenAssertions.assertGolden(fn, "ir/http_dispatch_dispatch_signed_sigv4.expected.erl");
  }

  @Test
  void dispatchSignedEndpointRulesAsStringMatchesGolden() throws IOException {
    Function fn =
        ErlangHttpDispatchIr.dispatchSigned(false, true, HELPERS_MOD, ENDPOINTS_MOD);
    IrGoldenAssertions.assertGolden(
        fn, "ir/http_dispatch_dispatch_signed_endpoint_rules.expected.erl");
  }

  @Test
  void dispatchSignedSigv4EndpointRulesAsStringMatchesGolden() throws IOException {
    Function fn =
        ErlangHttpDispatchIr.dispatchSigned(true, true, HELPERS_MOD, ENDPOINTS_MOD);
    IrGoldenAssertions.assertGolden(
        fn, "ir/http_dispatch_dispatch_signed_sigv4_endpoint_rules.expected.erl");
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangHttpDispatchIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
