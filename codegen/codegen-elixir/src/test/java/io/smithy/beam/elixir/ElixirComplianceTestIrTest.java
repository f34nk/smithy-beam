package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

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
    ExModule module = ElixirComplianceTestIr.complianceTestsModule(testContext(), service);
    IrGoldenAssertions.assertLinesAndAsString(
        module, "ir/compliance_service_compliance_tests.expected.ex");
    String text = module.asString();
    assertThat(text).contains("use ExUnit.Case, async: true");
    assertThat(text).contains("test \"GetItemRequest\"");
    assertThat(text).contains("defp assert_headers");
    for (ExFunction fn : module.functions()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void stringBackedEnumLiteralEmitsWireString() {
    String idl =
        """
                $version: "2"
                namespace com.large

                service LargeService {
                    operations: [LargeOp]
                }

                operation LargeOp {
                    input: LargeInput
                    output: LargeOutput
                }

                structure LargeInput {
                    status: LargeStatus
                }

                structure LargeOutput {}

                enum LargeStatus {
                    ALPHA
                    BETA
                    GAMMA
                }
                """;
    Model largeModel =
        Model.assembler().addUnparsedModel("large.smithy", idl).assemble().unwrap();
    ServiceShape largeService =
        largeModel.expectShape(ShapeId.from("com.large#LargeService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.elixirEnumStringThreshold(0);
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, largeService.getId().getNamespace(), largeService);
    ElixirSymbolProvider largeProvider =
        new ElixirSymbolProvider(
            settings,
            largeModel,
            largeService,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.TYPES);
    StructureShape input =
        largeModel.expectShape(ShapeId.from("com.large#LargeInput"), StructureShape.class);
    MemberShape statusMember = input.getMember("status").orElseThrow();
    assertThat(
            ElixirComplianceLiteralIr.memberValue(
                largeModel,
                statusMember,
                StringNode.from("ALPHA"),
                largeProvider,
                shape -> "Types." + largeProvider.toSymbol(shape).getName())
                .asString())
        .isEqualTo("\"ALPHA\"");
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
        "ComplianceServiceComplianceTests",
        "test/compliance_service_compliance_tests.ex");
  }
}
