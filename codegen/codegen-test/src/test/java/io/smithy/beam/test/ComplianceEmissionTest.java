package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ComplianceEmissionTest {

  private static final String COMPLIANCE_SERVICE = "smithy.beam.test.compliance#ComplianceService";
  private static final String BASIC_SERVICE = "smithy.beam.demo.basic#BasicService";

  private Model complianceModel() {
    return Model.assembler()
        .addImport(getClass().getResource("/model/compliance_tests_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private Model basicModel() {
    return Model.assembler()
        .addImport(getClass().getResource("/model/basic.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private ObjectNode serviceSettings() {
    return ObjectNode.builder()
        .withMember("service", COMPLIANCE_SERVICE)
        .withMember("edition", "2026")
        .build();
  }

  @Test
  void erlangComplianceTestsEmitFromTraits() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(serviceSettings())
                .build());

    String tests = manifest.getFileString("test/compliance_service_compliance_test.erl").orElse("");
    assertThat(tests).contains("-module(compliance_service_compliance_test).");
    assertThat(tests).contains("get_item_request_client_test");
    assertThat(tests).contains("encode_get_item_request");
    assertThat(tests).contains("get_item_response_client_test");
    assertThat(tests).contains("decode_get_item_response");
    assertThat(tests).contains("headers_to_proplist");
    assertThat(tests).contains("assert_headers");
    assertThat(tests).contains("assert_query_params");
    assertThat(tests).contains("assert_forbid_headers");
    assertThat(tests).contains("assert_require_headers");
    assertThat(tests).contains("Request#http_request.host");
    assertThat(tests).contains("prefix.example.com");
    assertThat(tests).doesNotContain("query_params_to_map");
  }

  @Test
  void erlangServerComplianceTestsEmitJsonBodyAssert() {
    MockManifest manifest = new MockManifest();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(serviceSettings())
                .build());

    String tests = manifest.getFileString("test/compliance_service_compliance_test.erl").orElse("");
    assertThat(tests).contains("get_item_response_encode_server_test");
    assertThat(tests).contains("assert_json_body");
    assertThat(tests).doesNotContain("get_item_request_client_test");
  }

  @Test
  void elixirComplianceTestsEmitFromTraits() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(serviceSettings())
                .build());

    String tests = manifest.getFileString("test/compliance_service_compliance_test.ex").orElse("");
    assertThat(tests).contains("defmodule ComplianceServiceComplianceTest");
    assertThat(tests).contains("test \"GetItemRequest client\"");
    assertThat(tests).contains("encode_get_item_request");
    assertThat(tests).contains("test \"GetItemResponse client\"");
    assertThat(tests).contains("decode_get_item_response");
    assertThat(tests).contains("defp headers_to_list");
    assertThat(tests).contains("defp assert_headers");
    assertThat(tests).contains("defp assert_query_params");
    assertThat(tests).contains("defp assert_forbid_headers");
    assertThat(tests).contains("defp assert_require_headers");
    assertThat(tests).contains("request.host");
    assertThat(tests).contains("prefix.example.com");
    assertThat(tests).doesNotContain("defp query_params_to_map");
  }

  @Test
  void elixirServerComplianceTestsEmitJsonBodyAssert() {
    MockManifest manifest = new MockManifest();
    new ElixirServerPlugin()
        .execute(
            PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(serviceSettings())
                .build());

    String tests = manifest.getFileString("test/compliance_service_compliance_test.ex").orElse("");
    assertThat(tests).contains("test \"GetItemResponseEncode server\"");
    assertThat(tests).contains("defp assert_json_body");
    assertThat(tests).doesNotContain("test \"GetItemRequest client\"");
  }

  @Test
  void erlangComplianceTestsAbsentWithoutTraits() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(basicModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", BASIC_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    assertThat(manifest.getFileString("test/basic_service_compliance_test.erl")).isEmpty();
  }

  @Test
  void elixirComplianceTestsAbsentWithoutTraits() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(basicModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", BASIC_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    assertThat(manifest.getFileString("test/basic_service_compliance_test.ex")).isEmpty();
  }
}
