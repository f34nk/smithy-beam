package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExNestedModule;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

class ElixirIrGoldenIntegrationTest {
  private static final String SERVICE_ID = "smithy.beam.demo.http#HttpService";
  private static final String GET_NAME_OUTPUT_ID = "smithy.beam.demo.http#GetNameOutput";

  static Model model;
  static ServiceShape service;
  static StructureShape getNameOutput;
  static BeamSettings settings;

  @BeforeAll
  static void setup() {
    URL resource = ElixirIrGoldenIntegrationTest.class.getResource("/model/ir_golden_http.smithy");
    assertThat(resource).isNotNull();
    model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    service = model.expectShape(ShapeId.from(SERVICE_ID), ServiceShape.class);
    getNameOutput = model.expectShape(ShapeId.from(GET_NAME_OUTPUT_ID), StructureShape.class);
    settings = new BeamSettings();
    settings.edition("2026");
  }

  static ElixirContext clientContext() {
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings,
                model,
                service,
                layout.clientModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
                BeamCodegenKind.CLIENT));
    MockManifest manifest = new MockManifest();
    Optional<ShapeId> resolved = BeamProtocolResolver.resolve(model, service, settings);
    return new ElixirContext(
        model,
        settings,
        sp,
        manifest,
        new WriterDelegator<>(manifest, sp, ElixirWriter.factory("test")),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        resolved.map(id -> BeamProtocolCodegenFactory.create(model, id, List.of())).orElse(null),
        resolved.orElse(null),
        ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
        layout.clientModuleFile());
  }

  @Test
  void structureNestedModuleFromSmithyMatchesGolden() throws IOException {
    ElixirContext ctx = clientContext();
    SymbolProvider sp = ctx.symbolProvider();
    List<MemberShape> members =
        StreamSupport.stream(getNameOutput.members().spliterator(), false).toList();
    ExNestedModule nested =
        ElixirDirectedCodegen.buildStructureNestedModule(
            getNameOutput,
            sp.toSymbol(getNameOutput),
            ctx,
            sp,
            NullableIndex.of(model),
            members);
    IrGoldenAssertions.assertLinesAndAsString(
        nested, "ir/golden/get_name_output_structure.expected.ex");
  }

  @Test
  void clientCodecModuleFromSmithyMatchesGolden() throws IOException {
    var module = ElixirRestJsonIr.buildClientCodecModule(clientContext(), service);
    IrGoldenAssertions.assertLinesAndAsString(
        module, "ir/golden/http_service_rest_json_1_client_codec.expected.ex");
    for (var fn : module.functions()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }
}
