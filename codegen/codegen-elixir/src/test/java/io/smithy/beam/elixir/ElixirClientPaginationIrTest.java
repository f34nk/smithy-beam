package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.LocalCallExpr;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirClientPaginationIrTest {
  private static final String PAGINATED_SERVICE = "smithy.beam.test.paginated#PaginatedService";

  @Test
  void paginatedOperationFunctionsMatchGolden() throws IOException {
    Model model = paginatedModel();
    OperationShape op =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#ListWidgets"), OperationShape.class);
    ServiceShape service = model.expectShape(ShapeId.from(PAGINATED_SERVICE), ServiceShape.class);
    ElixirContext ctx = testContext(model, PAGINATED_SERVICE);
    BeamElixirLayout layout = layout(model, PAGINATED_SERVICE);
    SymbolProvider sp = ctx.symbolProvider();
    PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(model, service, op);
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String itemType =
        ElixirTopDown.structureSpecType(
            typesMod, BeamClientPaginationSupport.itemsElementSymbol(model, sp, pi).orElseThrow());
    String successReturnType = "[" + itemType + "]";

    List<Function> functions =
        ElixirClientPaginationIr.paginatedOperationFunctions(
            ctx, service, op, layout, false, "RetryMod", successReturnType, null);

    assertThat(functions).hasSize(2);
    assertThat(functions.get(0).private_()).isFalse();
    assertThat(functions.get(1).private_()).isTrue();
    for (Function fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    assertThat(renderFunctions(functions))
        .isEqualTo(readExpectedString("ir/client_pagination_list_widgets.expected.ex"));

    LocalCallExpr arity2Call = (LocalCallExpr) functions.get(0).body();
    assertThat(arity2Call.function()).isEqualTo("list_widgets");
    assertThat(arity2Call.args().get(2)).isInstanceOf(ListExpr.class);

    String arity3Body = ElixirRenderer.renderFunction(functions.get(1));
    assertThat(arity3Body).contains("++");
  }

  private static String renderFunctions(List<Function> functions) {
    return functions.stream()
        .map(ElixirRenderer::renderFunction)
        .collect(Collectors.joining("\n\n"));
  }

  private static Model paginatedModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.paginated

                use aws.protocols#restJson1
                use smithy.api#httpQuery

                @restJson1
                service PaginatedService {
                    version: "2026"
                    operations: [ListWidgets]
                }

                @readonly
                @http(method: "GET", uri: "/widgets")
                @paginated(
                    inputToken: "next_token"
                    outputToken: "next_token"
                    pageSize: "max_results"
                    items: "widgets"
                )
                operation ListWidgets {
                    input: ListWidgetsInput
                    output: ListWidgetsOutput
                }

                structure ListWidgetsInput {
                    @httpQuery("next_token")
                    next_token: String

                    @httpQuery("max_results")
                    max_results: Integer
                }

                structure ListWidgetsOutput {
                    next_token: String
                    widgets: WidgetList
                }

                list WidgetList {
                    member: Widget
                }

                structure Widget {
                    id: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("paginated.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static ElixirContext testContext(Model model, String serviceId) {
    ServiceShape service = model.expectShape(ShapeId.from(serviceId), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings,
                model,
                service,
                layout.clientModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
                BeamCodegenKind.CLIENT));
    MockManifest manifest = new MockManifest();
    Optional<ShapeId> resolved = BeamProtocolResolver.resolve(model, service, settings);
    return new ElixirContext(
        model,
        settings,
        sp,
        manifest,
        new WriterDelegator<>(manifest, sp, ElixirWriter.factory("client")),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        resolved.map(id -> BeamProtocolCodegenFactory.create(model, id, List.of())).orElse(null),
        resolved.orElse(null),
        ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
        layout.clientModuleFile());
  }

  private static BeamElixirLayout layout(Model model, String serviceId) {
    ServiceShape service = model.expectShape(ShapeId.from(serviceId), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    return new BeamElixirLayout(settings, service.getId().getNamespace(), service);
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirClientPaginationIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
