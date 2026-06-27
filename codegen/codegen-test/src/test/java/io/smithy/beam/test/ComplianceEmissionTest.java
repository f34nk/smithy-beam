package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
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

  @Test
  void erlangComplianceTestsEmitFromTraits() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", COMPLIANCE_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String tests =
        manifest.getFileString("test/compliance_service_compliance_tests.erl").orElse("");
    assertThat(tests).contains("-module(compliance_service_compliance_tests).");
    assertThat(tests).contains("get_item_request_test_");
    assertThat(tests).contains("encode_get_item_request");
    assertThat(tests).contains("get_item_response_test_");
    assertThat(tests).contains("decode_get_item_response");
  }

  @Test
  void elixirComplianceTestsEmitFromTraits() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", COMPLIANCE_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String tests = manifest.getFileString("test/compliance_service_compliance_tests.ex").orElse("");
    assertThat(tests).contains("defmodule ComplianceServiceComplianceTests");
    assertThat(tests).contains("test \"GetItemRequest\"");
    assertThat(tests).contains("encode_get_item_request");
    assertThat(tests).contains("test \"GetItemResponse\"");
    assertThat(tests).contains("decode_get_item_response");
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

    assertThat(manifest.getFileString("test/basic_service_compliance_tests.erl")).isEmpty();
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

    assertThat(manifest.getFileString("test/basic_service_compliance_tests.ex")).isEmpty();
  }
}
