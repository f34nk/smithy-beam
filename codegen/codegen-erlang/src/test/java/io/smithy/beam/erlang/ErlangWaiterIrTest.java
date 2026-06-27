package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ErlangWaiterIrTest {
  private static final ShapeId SERVICE = ShapeId.from("smithy.beam.test.waiters#WaitableService");

  private static Model model;
  private static ServiceShape service;
  private static BeamWaiterIndex index;
  private static ErlangSymbolProvider provider;
  private static String clientMod;

  @BeforeAll
  static void setUp() {
    model = waiterModel();
    service = model.expectShape(SERVICE, ServiceShape.class);
    index = BeamWaiterIndex.of(model, service);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    clientMod = layout.clientModuleName();
    provider =
        new ErlangSymbolProvider(
            settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
  }

  @Test
  void waitBucketExistsFunctionMatchesExpectedShape() {
    BeamWaiterIndex.WaiterBinding binding =
        index.bindings().stream()
            .filter(b -> b.name().equals("BucketExists"))
            .findFirst()
            .orElseThrow();
    ErlFunction fn = ErlangWaiterIr.waiterFunction(index, binding, clientMod, provider);
    String text = fn.asString();

    assertThat(fn.name()).isEqualTo("wait_bucket_exists");
    assertThat(fn.arity()).isEqualTo(3);
    assertThat(text)
        .contains("Waits using the BucketExists waiter on smithy.beam.test.waiters#HeadBucket.");
    assertThat(text).contains("wait_bucket_exists(Client, Input, Opts) ->");
    assertThat(text).contains("state => success");
    assertThat(text).contains("matcher => success, expected => true");
    assertThat(text).contains("matcher => errorType, expected => #not_found{}");
    assertThat(text).contains("min_delay_ms => 4000");
    assertThat(text).contains("max_delay_ms => 300000");
    assertThat(text).contains(clientMod + ":head_bucket(Client, Input)");
    assertThat(text).contains("wait_until(");
  }

  @Test
  void waitTableExistsFunctionUsesPathMatcher() {
    BeamWaiterIndex.WaiterBinding binding =
        index.bindings().stream()
            .filter(b -> b.name().equals("TableExists"))
            .findFirst()
            .orElseThrow();
    ErlFunction fn = ErlangWaiterIr.waiterFunction(index, binding, clientMod, provider);
    String text = fn.asString();

    assertThat(fn.name()).isEqualTo("wait_table_exists");
    assertThat(text).contains("matcher => output");
    assertThat(text).contains("path => [table, table_status]");
    assertThat(text).contains("comparator => stringEquals");
    assertThat(text).contains("expected => <<\"ACTIVE\">>");
    assertThat(text).contains(clientMod + ":describe_table(Client, Input)");
  }

  @Test
  void waitUntilHelpersIncludePollingLoop() {
    List<ErlFunction> helpers = ErlangWaiterIr.waitUntilHelperFunctions(model, service, provider);
    String combined =
        helpers.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));

    assertThat(combined).contains("wait_until(Fun, Acceptors, Opts) ->");
    assertThat(combined).contains("wait_until(Fun, Acceptors, Attempts, Delay, MaxDelay) ->");
    assertThat(combined).contains("timer:sleep(Delay)");
    assertThat(combined).contains("matches_acceptor(");
    assertThat(combined).contains("error_types_match(Expected, Got)");
    assertThat(combined).contains("path_string_equals(Path, Expected, Output)");
    assertThat(combined).contains("record_fields(table_description)");
    assertThat(combined).contains("string_equals(V, Expected)");
    assertThat(combined).contains("string:uppercase(atom_to_binary(V, utf8))");
    assertThat(combined).doesNotContain("atom_to_binary:atom_to_binary");
  }

  @Test
  void waitersModuleIncludesExportsAndWaiters() {
    ErlModule module =
        ErlangWaiterIr.waitersModule(
            "waitable_service_waiters",
            "waitable_service_types.hrl",
            clientMod,
            index,
            provider,
            model,
            service);
    String text = module.asString();

    assertThat(text).contains("%% Generated waiters for smithy.beam.test.waiters#WaitableService.");
    assertThat(text).contains("-module(waitable_service_waiters).");
    assertThat(text).contains("-include(\"waitable_service_types.hrl\").");
    assertThat(text).contains("wait_bucket_exists/3");
    assertThat(text).contains("wait_table_exists/3");
    assertThat(text).contains("wait_until(Fun, Acceptors, Opts) ->");
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
}
