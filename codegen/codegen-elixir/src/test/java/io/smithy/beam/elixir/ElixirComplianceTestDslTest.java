package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirComplianceTestIrTest {
  private static final ShapeId SERVICE =
      ShapeId.from("smithy.beam.test.compliance#ComplianceService");
  private static final ShapeId REST_JSON = ShapeId.from("aws.protocols#restJson1");

  private static Model model;
  private static ServiceShape service;
  private static ElixirSymbolProvider provider;

  @BeforeAll
  static void setUp() {
    model =
        Model.assembler()
            .addImport(
                ElixirComplianceTestIrTest.class.getResource(
                    "/model/compliance_tests_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    service = model.expectShape(SERVICE, ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    provider =
        new ElixirSymbolProvider(
            settings, model, service, layout.typesModuleFile(), typesMod, BeamCodegenKind.CLIENT);
  }

  @Test
  void complianceTestsModuleMatchesGolden() throws IOException {
    Module module = ElixirComplianceTestDsl.complianceTestsModule(testContext(), service);
    assertThat(ElixirRenderer.render(module))
        .isEqualTo(
            DslGoldenAssertions.readExpectedString(
                "dsl/compliance_service_compliance_test.expected.ex"));
    String text = ElixirRenderer.render(module);
    assertThat(text).contains("use ExUnit.Case, async: true");
    assertThat(text).contains("test \"GetItemRequest\"");
    assertThat(text).contains("defp assert_headers");
    for (Function fn : module.functions()) {
      ElixirDslTestSupport.assertStructural(fn);
    }
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
        new WriterDelegator<>(manifest, provider, ElixirWriter.factory("compliance")),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        REST_JSON,
        "ComplianceServiceComplianceTest",
        "test/compliance_service_compliance_test.ex");
  }
}
