package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.core.BeamWaiterIndex;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirWaiterIrTest {
  private static final ShapeId SERVICE = ShapeId.from("smithy.beam.test.waiters#WaitableService");

  private static Model model;
  private static ServiceShape service;
  private static BeamWaiterIndex index;
  private static ElixirSymbolProvider provider;
  private static String clientMod;
  private static String typesMod;

  @BeforeAll
  static void setUp() {
    model = waiterModel();
    service = model.expectShape(SERVICE, ServiceShape.class);
    index = BeamWaiterIndex.of(model, service);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    clientMod = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    provider =
        new ElixirSymbolProvider(
            settings, model, service, layout.typesModuleFile(), typesMod, BeamCodegenKind.CLIENT);
  }

  @Test
  void waitBucketExistsFunctionMatchesExpectedShape() {
    BeamWaiterIndex.WaiterBinding binding =
        index.bindings().stream()
            .filter(b -> b.name().equals("BucketExists"))
            .findFirst()
            .orElseThrow();
    Function fn = ElixirWaiterIr.waiterFunction(index, binding, clientMod, typesMod, provider);
    String text = ElixirRenderer.renderFunction(fn);

    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.name()).isEqualTo("wait_bucket_exists");
    assertThat(text)
        .contains("Waits using the BucketExists waiter on smithy.beam.test.waiters#HeadBucket.");
    assertThat(text).contains("def wait_bucket_exists(client, input, opts) do");
    assertThat(text).contains("state: :success");
    assertThat(text).contains("matcher: :success");
    assertThat(text).contains("expected: true");
    assertThat(text).contains("matcher: :errorType");
    assertThat(text).contains("%" + typesMod + ".NotFound{}");
    assertThat(text).contains("min_delay_ms, 4000");
    assertThat(text).contains("max_delay_ms, 300000");
    assertThat(text).contains(clientMod + ".head_bucket(client, input)");
    assertThat(text).contains("wait_until(");
  }

  @Test
  void waitTableExistsFunctionUsesPathMatcher() {
    BeamWaiterIndex.WaiterBinding binding =
        index.bindings().stream()
            .filter(b -> b.name().equals("TableExists"))
            .findFirst()
            .orElseThrow();
    Function fn = ElixirWaiterIr.waiterFunction(index, binding, clientMod, typesMod, provider);
    String text = ElixirRenderer.renderFunction(fn);

    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.name()).isEqualTo("wait_table_exists");
    assertThat(text).contains("matcher: :output");
    assertThat(text).contains("path: [:table, :table_status]");
    assertThat(text).contains("comparator: :stringEquals");
    assertThat(text).contains("expected: \"ACTIVE\"");
    assertThat(text).contains(clientMod + ".describe_table(client, input)");
  }

  @Test
  void waitUntilHelpersIncludePollingLoop() {
    List<Function> helpers = ElixirWaiterIr.waitUntilHelperFunctions();
    String combined =
        helpers.stream().map(ElixirRenderer::renderFunction).collect(Collectors.joining("\n\n"));

    for (Function fn : helpers) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    assertThat(combined).contains("defp wait_until(step, acceptors, opts) do");
    assertThat(combined).contains("wait_until(step, acceptors, attempts");
    assertThat(combined).contains("Process.sleep(delay)");
    assertThat(combined).contains("matches_acceptor?");
    assertThat(combined).contains("error_types_match?");
    assertThat(combined).contains("path_string_equals?");
    assertThat(combined).contains("Map.from_struct(value)");
    assertThat(combined).contains("string_equals?");
    assertThat(combined).contains("String.upcase(Atom.to_string(left))");
  }

  @Test
  void waitersModuleMatchesGolden() throws IOException {
    Module module = ElixirWaiterIr.waitersModule(testContext(), service, index, provider, model);
    assertThat(ElixirRenderer.render(module))
        .isEqualTo(readExpectedString("ir/waiters_module.expected.ex"));
  }

  private static ElixirContext testContext() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    MockManifest manifest = new MockManifest();
    return new ElixirContext(
        model,
        settings,
        provider,
        manifest,
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("waiters")),
        java.util.List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "waiters",
        "waiters.ex");
  }

  private static Model waiterModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.waiters

                use aws.protocols#restJson1
                use smithy.waiters#waitable

                @restJson1
                service WaitableService {
                    version: "2026"
                    operations: [HeadBucket, DescribeTable]
                }

                @waitable(
                    BucketExists: {
                        documentation: "Wait until a bucket exists"
                        minDelay: 4
                        maxDelay: 300
                        acceptors: [
                            {
                                state: "success"
                                matcher: {
                                    success: true
                                }
                            }
                            {
                                state: "retry"
                                matcher: {
                                    errorType: "NotFound"
                                }
                            }
                        ]
                    }
                )
                @readonly
                @http(method: "HEAD", uri: "/{Bucket}")
                operation HeadBucket {
                    input: HeadBucketInput
                    output: HeadBucketOutput
                    errors: [NotFound]
                }

                structure HeadBucketInput {
                    @httpLabel
                    @required
                    Bucket: String
                }

                structure HeadBucketOutput {}

                @waitable(
                    TableExists: {
                        acceptors: [
                            {
                                state: "success"
                                matcher: {
                                    output: {
                                        path: "Table.TableStatus"
                                        expected: "ACTIVE"
                                        comparator: "stringEquals"
                                    }
                                }
                            }
                        ]
                    }
                )
                @readonly
                @http(method: "GET", uri: "/tables/{TableName}")
                operation DescribeTable {
                    input: DescribeTableInput
                    output: DescribeTableOutput
                }

                structure DescribeTableInput {
                    @httpLabel
                    @required
                    TableName: String
                }

                structure DescribeTableOutput {
                    Table: TableDescription
                }

                structure TableDescription {
                    TableStatus: String
                }

                @error("client")
                structure NotFound {
                    message: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("waiter_fixture.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirWaiterIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
