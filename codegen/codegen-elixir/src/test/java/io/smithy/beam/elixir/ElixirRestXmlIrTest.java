package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirRestXmlIrTest {
  @Test
  void decodeGetNameRequestMatchesGolden() throws IOException {
    ExFunction fn = decodeGetNameRequest();
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/rest_xml_decode_get_name_request.expected.ex"));
  }

  @Test
  void decodeGetNameResponseMatchesGolden() throws IOException {
    ExFunction fn = decodeGetNameResponse();
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/rest_xml_decode_get_name_response.expected.ex"));
  }

  @Test
  void encodeGetNameRequestMatchesGolden() throws IOException {
    ExFunction fn = encodeGetNameRequest();
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/rest_xml_encode_get_name_request.expected.ex"));
  }

  @Test
  void encodeGetNameResponseMatchesGolden() throws IOException {
    ExFunction fn = encodeGetNameResponse();
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/rest_xml_encode_get_name_response.expected.ex"));
  }

  private static ExFunction decodeGetNameRequest() {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    ElixirSymbolProvider sp = symbolProvider(model);
    return ElixirRestXmlOperationIr.buildDecodeRequest(
        model, op, HttpBindingIndex.of(model), sp, typesMod(model));
  }

  private static ExFunction decodeGetNameResponse() {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    ElixirSymbolProvider sp = symbolProvider(model);
    return ElixirRestXmlOperationIr.buildDecodeResponse(
        model, op, HttpBindingIndex.of(model), sp, typesMod(model));
  }

  private static ExFunction encodeGetNameRequest() {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    ElixirSymbolProvider sp = symbolProvider(model);
    return ElixirRestXmlOperationIr.buildEncodeRequest(
        model,
        service,
        op,
        HttpBindingIndex.of(model),
        sp,
        typesMod(model),
        runtimeMod(model),
        false);
  }

  private static ExFunction encodeGetNameResponse() {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    ElixirSymbolProvider sp = symbolProvider(model);
    return ElixirRestXmlOperationIr.buildEncodeResponse(
        model, op, HttpBindingIndex.of(model), sp, typesMod(model), runtimeMod(model));
  }

  private static Model httpModel() {
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

  private static ElixirSymbolProvider symbolProvider(Model model) {
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
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

  private static String typesMod(Model model) {
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    return ElixirSymbolProvider.toModuleName(layout.typesModuleName());
  }

  private static String runtimeMod(Model model) {
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    return ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirRestXmlIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
