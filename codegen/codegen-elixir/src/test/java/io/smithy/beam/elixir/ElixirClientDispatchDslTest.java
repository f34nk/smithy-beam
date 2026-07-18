package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.RemoteCallExpr;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirClientDispatchDslTest {
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
  @Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
  void restJsonOperationBodyMatchesGolden() throws IOException {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<Expression> body =
        ElixirClientDispatchDsl.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            false,
            "RetryMod",
            false,
            ElixirClientDispatchOperationDsl.DispatchBodyMode.SINGLE_PAGE);
    assertStructural(body);
    assertThat(ElixirClientDispatchDsl.renderBody(body))
        .isEqualTo(readExpectedString("dsl/client_dispatch_get_name.expected.ex"));
  }

  @Test
  @Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
  void restJsonOperationBodyWithRetryMatchesGolden() throws IOException {
    Model model = httpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<Expression> body =
        ElixirClientDispatchDsl.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            true,
            "HttpServiceClient",
            false,
            ElixirClientDispatchOperationDsl.DispatchBodyMode.SINGLE_PAGE);
    assertThat(body.get(0)).isInstanceOf(MatchExpr.class);
    assertThat(body.get(body.size() - 1)).isInstanceOf(RemoteCallExpr.class);
    assertThat(((RemoteCallExpr) body.get(body.size() - 1)).function()).isEqualTo("with_retry");
    assertThat(ElixirClientDispatchDsl.renderBody(body))
        .isEqualTo(readExpectedString("dsl/client_dispatch_get_name_retry.expected.ex"));
  }

  @Test
  void restJsonOperationBodyWithSigV4MatchesGolden() throws IOException {
    Model model = sigv4HttpModel();
    OperationShape op =
        model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
    List<Expression> body =
        ElixirClientDispatchDsl.operationBodyExprs(
            testContext(model, HTTP_SERVICE),
            op,
            layout(model, HTTP_SERVICE),
            false,
            "RetryMod",
            false,
            ElixirClientDispatchOperationDsl.DispatchBodyMode.SINGLE_PAGE);
    assertStructural(body);
    String rendered = ElixirClientDispatchDsl.renderBody(body);
    assertThat(rendered).contains("sign_request(config, :get_name, req)");
    assertThat(rendered)
        .isEqualTo(readExpectedString("dsl/client_dispatch_get_name_sigv4.expected.ex"));
  }

  @Test
  void signRequestHelperFetchesAmbientCredentialsBeforeSign() {
    Function helper = ElixirClientDispatchOperationDsl.signRequestFunction();
    String rendered = ElixirRenderer.renderFunction(helper);
    assertThat(helper.private_()).isTrue();
    assertThat(rendered).contains("defp sign_request(config, op, req)");
    assertThat(rendered).contains(":aws_credentials.get_credentials()");
    assertThat(rendered).contains("Map.put(config, :credentials, creds)");
    assertThat(rendered).contains("AwsSigv4.sign(config, op, req)");
    assertThat(rendered).doesNotContain("%{config | credentials:");
  }

  @Test
  @Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
  void paginatedPageBodyMatchesGolden() throws IOException {
    Model model = paginatedModel();
    OperationShape op =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#ListWidgets"), OperationShape.class);
    List<Expression> body =
        ElixirClientDispatchDsl.operationBodyExprs(
            testContext(model, PAGINATED_SERVICE),
            op,
            layout(model, PAGINATED_SERVICE),
            false,
            "RetryMod",
            true,
            ElixirClientDispatchOperationDsl.DispatchBodyMode.PAGINATED_PAGE);
    assertStructural(body);
    assertThat(ElixirClientDispatchDsl.renderBody(body))
        .isEqualTo(readExpectedString("dsl/client_dispatch_list_widgets_page.expected.ex"));
  }

  private static void assertStructural(List<Expression> body) {
    assertThat(body).isNotEmpty();
    assertThat(body.get(0)).isInstanceOf(MatchExpr.class);
    assertThat(body.get(body.size() - 1)).isInstanceOf(CaseExpr.class);
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirClientDispatchDslTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
