package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Function;
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
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangClientPaginationIrTest {
  private static final String PAGINATED_SERVICE = "smithy.beam.test.paginated#PaginatedService";

  @Test
  void paginatedOperationFunctionsMatchGolden() throws IOException {
    Model model = paginatedModel();
    OperationShape op =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#ListWidgets"), OperationShape.class);
    ServiceShape service = model.expectShape(ShapeId.from(PAGINATED_SERVICE), ServiceShape.class);
    ErlangContext ctx = testContext(model, PAGINATED_SERVICE);
    BeamErlangLayout layout = layout(model, PAGINATED_SERVICE);

    List<Function> functions =
        ErlangClientPaginationIr.paginatedOperationFunctions(
            ctx, service, op, layout, false, "retry_mod", "[widget()]", null);

    assertThat(functions).hasSize(2);
    assertThat(functions.get(0).arity()).isEqualTo(2);
    assertThat(functions.get(1).arity()).isEqualTo(3);
    assertThat(functions.get(0).clauses()).isNotEmpty();
    assertThat(functions.get(1).clauses()).isNotEmpty();
    assertThat(IrGoldenAssertions.renderFunctions(functions))
        .isEqualTo(readExpectedString("ir/client_pagination_list_widgets.expected.erl"));
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

  private static ErlangContext testContext(Model model, String serviceId) {
    ServiceShape service = model.expectShape(ShapeId.from(serviceId), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ErlangSymbolProvider(
                settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT));
    MockManifest manifest = new MockManifest();
    Optional<ShapeId> resolved = BeamProtocolResolver.resolve(model, service, settings);
    return new ErlangContext(
        model,
        settings,
        sp,
        manifest,
        new WriterDelegator<>(manifest, sp, ErlangWriter.factory()),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        resolved.map(id -> BeamProtocolCodegenFactory.create(model, id, List.of())).orElse(null),
        resolved.orElse(null),
        layout.clientModuleName(),
        layout.clientModuleFile());
  }

  private static BeamErlangLayout layout(Model model, String serviceId) {
    ServiceShape service = model.expectShape(ShapeId.from(serviceId), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    return new BeamErlangLayout(settings, service.getId().getNamespace(), service);
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangClientPaginationIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
