package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Function;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ErlangRestXmlIrTest {
  @Test
  void restXmlHelpersMatchGolden() throws IOException {
    for (Function fn : ErlangRestXmlIr.xmlDecodeHelpers()) {
      assertStructural(fn);
    }
    for (Function fn : ErlangRestXmlIr.xmlEncodeHelpers()) {
      assertStructural(fn);
    }
    String combined =
        IrGoldenAssertions.renderFunctions(ErlangRestXmlIr.xmlDecodeHelpers())
            + "\n"
            + IrGoldenAssertions.renderFunctions(ErlangRestXmlIr.xmlEncodeHelpers());
    assertThat(IrGoldenAssertions.normalizeTrailingNewline(combined))
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/rest_xml_helpers.expected.erl"));
  }

  @Test
  void encodeRequestIsStructural() {
    assertStructural(sampleEncodeRequest());
  }

  @Test
  void decodeGetNameRequestIsStructural() {
    assertStructural(decodeGetNameRequest());
  }

  @Test
  void encodeGetNameResponseIsStructural() {
    assertStructural(encodeGetNameResponse());
  }

  @Test
  void decodeGetNameRequestMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        decodeGetNameRequest(), "ir/rest_xml_decode_get_name_request.expected.erl");
  }

  @Test
  void decodeGetNameResponseMatchesGolden() throws IOException {
    String combined =
        IrGoldenAssertions.renderFunctions(
            ErlangRestXmlOperationIr.buildDecodeResponse(
                sampleModel(), op(), HttpBindingIndex.of(sampleModel()), sp()));
    assertThat(IrGoldenAssertions.normalizeTrailingNewline(combined))
        .isEqualTo(
            IrGoldenAssertions.readExpectedString(
                "ir/rest_xml_decode_get_name_response.expected.erl"));
  }

  @Test
  void encodeGetNameRequestMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        sampleEncodeRequest(), "ir/rest_xml_encode_get_name_request.expected.erl");
  }

  @Test
  void encodeGetNameResponseMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        encodeGetNameResponse(), "ir/rest_xml_encode_get_name_response.expected.erl");
  }

  @Test
  void capturedCodecBodiesDoNotDuplicateClauseTerminators() {
    Model model = sampleModel();
    ServiceShape service = service();
    OperationShape op = op();
    ErlangSymbolProvider sp = sp();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);

    String encodeRequest =
        ErlangRenderer.renderFunction(
            ErlangRestXmlIr.encodeRequest(model, service, op, httpIndex, sp, false));
    String decodeRequest =
        ErlangRenderer.renderFunction(
            ErlangRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp));
    String decodeResponse =
        IrGoldenAssertions.renderFunctions(
            ErlangRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp));
    String encodeResponse =
        ErlangRenderer.renderFunction(
            ErlangRestXmlOperationIr.buildEncodeResponse(model, op, httpIndex, sp));

    for (String generated : List.of(encodeRequest, decodeRequest, decodeResponse, encodeResponse)) {
      assertThat(generated).doesNotContain("}..");
      assertThat(generated).doesNotContain("}};;");
      assertThat(generated).doesNotContain("};.");
    }
  }

  static Model sampleModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.http

                use aws.protocols#restXml

                string Name

                @restXml
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

  private static Function sampleEncodeRequest() {
    Model model = sampleModel();
    return ErlangRestXmlIr.encodeRequest(
        model, service(), op(), HttpBindingIndex.of(model), sp(), false);
  }

  private static Function decodeGetNameRequest() {
    Model model = sampleModel();
    return ErlangRestXmlOperationIr.buildDecodeRequest(
        model, op(), HttpBindingIndex.of(model), sp());
  }

  private static Function encodeGetNameResponse() {
    Model model = sampleModel();
    return ErlangRestXmlOperationIr.buildEncodeResponse(
        model, op(), HttpBindingIndex.of(model), sp());
  }

  private static ServiceShape service() {
    return sampleModel()
        .expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
  }

  private static OperationShape op() {
    return sampleModel()
        .expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
  }

  private static ErlangSymbolProvider sp() {
    return sampleSymbolProvider(sampleModel(), service());
  }

  static ErlangSymbolProvider sampleSymbolProvider(Model model, ServiceShape service) {
    return new ErlangSymbolProvider(
        new io.smithy.beam.core.BeamSettings(),
        model,
        service,
        "http_types.hrl",
        io.smithy.beam.core.BeamCodegenKind.CLIENT);
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
