package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.MapEntry;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangRestJsonIrTest {
  private static StructureShape basicItem;
  private static ErlangSymbolProvider provider;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service ItemService {
                    operations: [ListItems]
                }

                operation ListItems {
                    input: ListItemsInput
                    output: ListItemsOutput
                }

                structure ListItemsInput {}

                structure ListItemsOutput {
                    items: BasicItemList
                }

                structure BasicItem {
                    name: String
                    count: Integer
                }

                list BasicItemList {
                    member: BasicItem
                }
                """;
    Model model = Model.assembler().addUnparsedModel("item.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#ItemService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    provider =
        new ErlangSymbolProvider(settings, model, service, "item_types.hrl", BeamCodegenKind.TYPES);
    basicItem = model.expectShape(ShapeId.from("com.example#BasicItem"), StructureShape.class);
  }

  private static Model model() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service ItemService {
                    operations: [ListItems]
                }

                operation ListItems {
                    input: ListItemsInput
                    output: ListItemsOutput
                }

                structure ListItemsInput {}

                structure ListItemsOutput {
                    items: BasicItemList
                }

                structure BasicItem {
                    name: String
                    count: Integer
                }

                list BasicItemList {
                    member: BasicItem
                }
                """;
    return Model.assembler().addUnparsedModel("item.smithy", idl).assemble().unwrap();
  }

  @Test
  void structureDecodeEncodeAsStringMatchesGolden() throws IOException {
    Model model = model();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    List<Function> functions =
        ErlangRestJsonIr.buildStructureDecodeEncode(model, httpIndex, basicItem, provider);
    assertThat(functions).hasSize(2);
    assertStructural(functions.get(0));
    assertStructural(functions.get(1));
    String combined =
        ErlangRenderer.renderFunction(functions.get(0))
            + "\n\n"
            + ErlangRenderer.renderFunction(functions.get(1));
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/structure_decode_encode_basic_item.expected.erl"));
  }

  @Test
  void encodeGetNameRequestMatchesGolden() throws IOException {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    io.smithy.beam.core.BeamErlangLayout layout =
        new io.smithy.beam.core.BeamErlangLayout(settings, service.getId().getNamespace(), service);
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    Function fn =
        ErlangRestJsonIr.encodeRequest(
            model, service, op, httpIndex, sp, false, layout.eventStreamModuleName());
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/rest_json_encode_get_name_request.expected.erl");
  }

  @Test
  void decodedBodyPreludeMatchesGolden() throws IOException {
    String combined =
        ErlangJsonCodecSupport.decodedBodyPrelude().stream()
            .map(ErlangRenderer::renderExpression)
            .collect(Collectors.joining("\n"));
    assertThat(combined).isEqualTo(IrGoldenAssertions.readExpectedString("ir/json_decoded_body_prelude.expected.erl"));
  }

  @Test
  void bodyMapEntriesForBasicItemMatchesGolden() throws IOException {
    Model model = model();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    List<MemberShape> members =
        List.of(
            model
                .expectShape(
                    software.amazon.smithy.model.shapes.ShapeId.from("com.example#BasicItem"),
                    StructureShape.class)
                .getMember("name")
                .orElseThrow(),
            model
                .expectShape(
                    software.amazon.smithy.model.shapes.ShapeId.from("com.example#BasicItem"),
                    StructureShape.class)
                .getMember("count")
                .orElseThrow());
    List<MapEntry> entries =
        ErlangJsonCodecSupport.bodyMapEntries(
            model, httpIndex, provider, members, HttpBinding.Location.DOCUMENT, "event_stream");
    String combined =
        entries.stream()
            .map(entry -> ErlangRenderer.renderExpression(entry.key()) + " => "
                + ErlangRenderer.renderExpression(entry.value()))
            .collect(Collectors.joining(",\n"));
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/json_body_map_entries_basic_item.expected.erl"));
  }

  @Test
  void encodeResponseBodyExprsMatchGolden() throws IOException {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, "http_types.hrl", BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    String combined =
        ErlangRestJsonOperationIr.buildEncodeResponseBodyExprs(model, op, httpIndex, sp).stream()
            .map(ErlangRenderer::renderExpression)
            .collect(Collectors.joining("\n"));
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/rest_json_encode_response_body.expected.erl"));
  }

  @Test
  void encodeGetNameResponseMatchesGolden() throws IOException {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, "http_types.hrl", BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    Function fn = ErlangRestJsonOperationIr.buildEncodeResponse(model, op, httpIndex, sp);
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/rest_json_encode_get_name_response.expected.erl");
  }

  @Test
  void encodeNotFoundErrorResponseMatchesGolden() throws IOException {
    Model model = errorModel();
    ShapeId errorId = ShapeId.from("smithy.beam.demo.http#NotFoundError");
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, "http_types.hrl", BeamCodegenKind.CLIENT);
    Function fn = ErlangRestJsonOperationIr.buildErrorResponseEncoder(model, errorId, sp);
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(
        fn, "ir/rest_json_encode_not_found_error_response.expected.erl");
  }

  @Test
  void decodeGetNameResponseMatchesGolden() throws IOException {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    io.smithy.beam.core.BeamErlangLayout layout =
        new io.smithy.beam.core.BeamErlangLayout(settings, service.getId().getNamespace(), service);
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    Function fn = ErlangRestJsonIr.decodeResponse(model, service, op, httpIndex, sp, layout);
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/rest_json_decode_get_name_response.expected.erl");
  }

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

  private static Model errorModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.http

                use aws.protocols#restJson1
                use smithy.api#httpError

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
                    errors: [NotFoundError]
                }

                structure GetNameInput {
                    @required
                    @httpLabel
                    name: Name
                }

                structure GetNameOutput {
                    name: Name
                }

                @httpError(404)
                @error("client")
                structure NotFoundError {
                    message: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("http.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  @Test
  void structureListDecodeEncodeAsStringMatchesGolden() throws IOException {
    List<Function> functions =
        ErlangRestJsonIr.buildStructureListDecodeEncodeFunctions(basicItem, provider);
    assertThat(functions).hasSize(2);
    assertStructural(functions.get(0));
    assertStructural(functions.get(1));
    String combined =
        ErlangRenderer.renderFunction(functions.get(0))
            + "\n\n"
            + ErlangRenderer.renderFunction(functions.get(1));
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/structure_list_decode_encode_item.expected.erl"));
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
