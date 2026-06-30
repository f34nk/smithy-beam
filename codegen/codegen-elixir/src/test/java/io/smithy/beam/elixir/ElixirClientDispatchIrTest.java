package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExMatch;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirClientDispatchIrTest {
  private static final String HTTP_SERVICE = "smithy.beam.demo.http#HttpService";
  private static final String PAGINATED_SERVICE = "smithy.beam.test.paginated#PaginatedService";

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

  private static Model sigv4HttpModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.http

                use aws.protocols#restJson1
                use aws.auth#sigv4

                string Name

                @restJson1
                @sigv4(name: "demo")
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
        .addUnparsedModel("http_sigv4.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
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

  @Test
  void restJsonOperationBodyMatchesGolden() throws IOException {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<ExExpr> body =
        ElixirClientDispatchIr.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            false,
            "RetryMod",
            false,
            ElixirClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    assertStructural(body);
    assertThat(ElixirClientDispatchIr.renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_get_name.expected.ex"));
  }

  @Test
  void restJsonOperationBodyWithRetryMatchesGolden() throws IOException {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<ExExpr> body =
        ElixirClientDispatchIr.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            true,
            "RetryMod",
            false,
            ElixirClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    assertThat(body.get(0)).isInstanceOf(ExMatch.class);
    assertThat(body.get(body.size() - 1)).isInstanceOf(ExCall.class);
    assertThat(((ExCall) body.get(body.size() - 1)).function()).isEqualTo("with_retry");
    assertThat(ElixirClientDispatchIr.renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_get_name_retry.expected.ex"));
  }

  @Test
  void restJsonOperationBodyWithSigV4MatchesGolden() throws IOException {
    Model model = sigv4HttpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<ExExpr> body =
        ElixirClientDispatchIr.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            false,
            "RetryMod",
            false,
            ElixirClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    assertStructural(body);
    assertThat(ElixirClientDispatchIr.renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_get_name_sigv4.expected.ex"));
  }

  @Test
  void paginatedPageBodyMatchesGolden() throws IOException {
    Model model = paginatedModel();
    OperationShape op =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#ListWidgets"), OperationShape.class);
    List<ExExpr> body =
        ElixirClientDispatchIr.operationBodyExprs(
            testContext(model, PAGINATED_SERVICE),
            op,
            layout(model, PAGINATED_SERVICE),
            false,
            "RetryMod",
            true,
            ElixirClientDispatchOperationIr.DispatchBodyMode.PAGINATED_PAGE);
    assertStructural(body);
    assertThat(ElixirClientDispatchIr.renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_list_widgets_page.expected.ex"));
  }

  private static void assertStructural(List<ExExpr> body) {
    assertThat(body).isNotEmpty();
    assertThat(body.get(0)).isInstanceOf(ExMatch.class);
    assertThat(body.get(body.size() - 1)).isInstanceOf(ExExpr.class);
    ExExpr dispatch = body.get(body.size() - 1);
    assertThat(dispatch).isInstanceOf(ExCase.class);
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirClientDispatchIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
