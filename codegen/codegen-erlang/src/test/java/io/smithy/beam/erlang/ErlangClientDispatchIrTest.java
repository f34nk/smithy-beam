package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlMatch;
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

class ErlangClientDispatchIrTest {
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

  @Test
  void restJsonOperationBodyMatchesGolden() throws IOException {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<ErlExpr> body =
        ErlangClientDispatchIr.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            false,
            "retry_mod",
            false,
            ErlangClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    assertStructural(body);
    assertThat(renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_get_name.expected.erl"));
  }

  @Test
  void restJsonOperationBodyWithRetryMatchesGolden() throws IOException {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<ErlExpr> body =
        ErlangClientDispatchIr.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            true,
            "retry_mod",
            false,
            ErlangClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    assertThat(body.get(0)).isInstanceOf(ErlMatch.class);
    assertThat(body.get(body.size() - 1)).isInstanceOf(ErlCall.class);
    assertThat(((ErlCall) body.get(body.size() - 1)).function()).isEqualTo("with_retry");
    assertThat(renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_get_name_retry.expected.erl"));
  }

  @Test
  void restJsonOperationBodyWithSigV4MatchesGolden() throws IOException {
    Model model = sigv4HttpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<ErlExpr> body =
        ErlangClientDispatchIr.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            false,
            "retry_mod",
            false,
            ErlangClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    assertStructural(body);
    assertThat(renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_get_name_sigv4.expected.erl"));
  }

  @Test
  void paginatedPageBodyMatchesGolden() throws IOException {
    Model model = paginatedModel();
    OperationShape op =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#ListWidgets"), OperationShape.class);
    List<ErlExpr> body =
        ErlangClientDispatchIr.operationBodyExprs(
            testContext(model, PAGINATED_SERVICE),
            op,
            layout(model, PAGINATED_SERVICE),
            false,
            "retry_mod",
            true,
            ErlangClientDispatchOperationIr.DispatchBodyMode.PAGINATED_PAGE);
    assertStructural(body);
    assertThat(renderBody(body))
        .isEqualTo(readExpectedString("ir/client_dispatch_list_widgets_page.expected.erl"));
  }

  private static void assertStructural(List<ErlExpr> body) {
    assertThat(body).isNotEmpty();
    assertThat(body.get(0)).isInstanceOf(ErlMatch.class);
    assertThat(body.get(body.size() - 1)).isInstanceOf(ErlExpr.class);
    ErlExpr dispatch = body.get(body.size() - 1);
    assertThat(dispatch).isInstanceOf(ErlCase.class);
  }

  private static String renderBody(List<ErlExpr> body) {
    ErlangWriter writer = new ErlangWriter("test.erl");
    ErlangClientDispatchIr.writeExprs(writer, body);
    return writer.toString().strip();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangClientDispatchIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
