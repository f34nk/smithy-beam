package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirRestJsonIrTest {
  @Test
  void decodeGetNameRequestMatchesGolden() throws IOException {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    Function fn =
        ElixirRestJsonOperationIr.buildDecodeRequest(
                model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule)
            .get(0);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(ElixirRenderer.renderFunction(fn))
        .isEqualTo(readExpectedString("ir/rest_json_decode_get_name_request.expected.ex"));
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
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    Function fn =
        ElixirRestJsonOperationIr.buildDecodeResponse(
                model, service, op, httpIndex, sp, typesMod, runtimeMod)
            .get(0);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(ElixirRenderer.renderFunction(fn))
        .isEqualTo(readExpectedString("ir/rest_json_decode_get_name_response.expected.ex"));
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
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    Function fn =
        ElixirRestJsonOperationIr.buildEncodeRequest(
                model, service, op, httpIndex, sp, typesMod, runtimeMod, false, eventStreamModule)
            .get(0);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(ElixirRenderer.renderFunction(fn))
        .isEqualTo(readExpectedString("ir/rest_json_encode_get_name_request.expected.ex"));
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
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    Function fn =
        ElixirRestJsonOperationIr.buildEncodeResponse(
                model, op, httpIndex, sp, typesMod, runtimeMod)
            .get(0);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(ElixirRenderer.renderFunction(fn))
        .isEqualTo(readExpectedString("ir/rest_json_encode_get_name_response.expected.ex"));
  }

  @Test
  void clientCodecFunctionsAreStructural() {
    Model model = httpModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    for (Function fn :
        ElixirRestJsonIr.clientCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            eventStreamModule,
            false)) {
      ElixirIrTestSupport.assertStructural(fn);
    }
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

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirRestJsonIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
