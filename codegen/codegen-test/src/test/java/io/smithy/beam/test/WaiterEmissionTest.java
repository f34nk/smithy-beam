package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class WaiterEmissionTest {

  private static final String SERVICE = "smithy.beam.test.waiters#WaitableService";

  private Model waiterModel() {
    return Model.assembler()
        .addImport(getClass().getResource("/model/waiter_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  @Test
  void erlangWaiterModulePollsWithAcceptors() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(waiterModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String waiters = manifest.getFileString("waitable_service_waiters.erl").orElse("");
    assertThat(waiters).contains("-module(waitable_service_waiters).");
    assertThat(waiters).contains("-include(\"waitable_service_types.hrl\")");
    assertThat(waiters).contains("wait_bucket_exists/3");
    assertThat(waiters).contains("wait_bucket_exists(Client, Input, Opts) ->");
    assertThat(waiters).contains("waitable_service_client:head_bucket(Client, Input)");
    assertThat(waiters).contains("state => success");
    assertThat(waiters).contains("matcher => success, expected => true");
    assertThat(waiters).contains("matcher => errorType, expected => #not_found{}");
    assertThat(waiters).contains("error_types_match(Expected, Got)");
    assertThat(waiters).contains("timer:sleep(Delay)");
  }

  @Test
  void erlangWaiterPathMatcherUsesSnakeCaseSegments() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(waiterModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String waiters = manifest.getFileString("waitable_service_waiters.erl").orElse("");
    assertThat(waiters).contains("path => [table, table_status]");
    assertThat(waiters).contains("record_fields(table_description)");
    assertThat(waiters).contains("record_field(");
    assertThat(waiters).contains("string_equals(");
  }

  @Test
  void elixirWaiterModulePollsWithAcceptors() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(waiterModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String waiters = manifest.getFileString("waitable_service_waiters.ex").orElse("");
    assertThat(waiters).contains("defmodule WaitableServiceWaiters");
    assertThat(waiters).contains("def wait_bucket_exists");
    assertThat(waiters).contains("WaitableServiceClient.head_bucket(client, input)");
    assertThat(waiters).contains("state: :success");
    assertThat(waiters).contains("matcher: :success");
    assertThat(waiters).contains("expected: true");
    assertThat(waiters).contains("matcher: :errorType");
    assertThat(waiters).contains("expected: %WaitableServiceTypes.NotFound{}");
    assertThat(waiters).contains("error_types_match?(expected, got)");
    assertThat(waiters).contains("Process.sleep(delay)");
  }
}
