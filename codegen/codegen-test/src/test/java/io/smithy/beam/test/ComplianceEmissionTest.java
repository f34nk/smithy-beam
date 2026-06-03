package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceEmissionTest {

    private static final String SERVICE = "smithy.beam.test.compliance#ComplianceService";

    private Model complianceModel() {
        return Model.assembler()
                .addImport(getClass().getResource("/model/compliance_tests_fixture.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangComplianceTestsRequireOptInSetting() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        assertThat(manifest.getFileString("test/compliance_service_compliance_tests.erl")).isEmpty();
    }

    @Test
    void erlangComplianceTestsEmitWhenEnabled() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .withMember("emitComplianceTests", true)
                        .build())
                .build());

        String tests = manifest.getFileString("test/compliance_service_compliance_tests.erl").orElse("");
        assertThat(tests).contains("-module(compliance_service_compliance_tests).");
        assertThat(tests).contains("get_item_request_test_");
        assertThat(tests).contains("encode_get_item_request");
        assertThat(tests).contains("get_item_response_test_");
        assertThat(tests).contains("decode_get_item_response");
    }

    @Test
    void elixirComplianceTestsEmitWhenEnabled() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(complianceModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .withMember("emitComplianceTests", true)
                        .build())
                .build());

        String tests = manifest.getFileString("test/compliance_service_compliance_tests.ex").orElse("");
        assertThat(tests).contains("defmodule ComplianceServiceComplianceTests");
        assertThat(tests).contains("test \"GetItemRequest\"");
        assertThat(tests).contains("encode_get_item_request");
        assertThat(tests).contains("test \"GetItemResponse\"");
        assertThat(tests).contains("decode_get_item_response");
    }
}
