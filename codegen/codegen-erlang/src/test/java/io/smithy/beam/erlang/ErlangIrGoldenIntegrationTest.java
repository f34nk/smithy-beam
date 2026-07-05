package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.beam.ir.erlang.Header;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangIrGoldenIntegrationTest {
  private static final String SERVICE_ID = "smithy.beam.demo.http#HttpService";
  private static final String GET_NAME_OUTPUT_ID = "smithy.beam.demo.http#GetNameOutput";

  private static Model model;
  private static ServiceShape service;
  private static StructureShape getNameOutput;
  private static BeamSettings settings;

  @BeforeAll
  static void setup() {
    URL resource = ErlangIrGoldenIntegrationTest.class.getResource("/model/ir_golden_http.smithy");
    assertThat(resource).isNotNull();
    model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    service = model.expectShape(ShapeId.from(SERVICE_ID), ServiceShape.class);
    getNameOutput = model.expectShape(ShapeId.from(GET_NAME_OUTPUT_ID), StructureShape.class);
    settings = new BeamSettings();
    settings.edition("2026");
  }

  private static ErlangContext clientContext() {
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ErlangSymbolProvider(
                settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT));
    MockManifest manifest = new MockManifest();
    Optional<ShapeId> resolved = BeamProtocolResolver.resolve(model, service, settings);
    return new ErlangContext(
        model,
        settings,
        sp,
        manifest,
        new WriterDelegator<>(manifest, sp, ErlangWriter.factory()),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        resolved.map(id -> BeamProtocolCodegenFactory.create(model, id, List.of())).orElse(null),
        resolved.orElse(null),
        layout.clientModuleName(),
        layout.clientModuleFile());
  }

  @Test
  void structureTypeHeaderFromSmithyMatchesGolden() throws IOException {
    Header header =
        ErlangTypeDirectedCodegen.buildStructureTypeHeader(model, service, getNameOutput, settings);
    IrGoldenAssertions.assertGolden(header, "ir/get_name_output_structure.expected.hrl");
  }

  @Test
  void clientCodecModuleFromSmithyMatchesGolden() throws IOException {
    Module module = ErlangRestJsonIr.buildClientCodecModule(clientContext(), service);
    IrGoldenAssertions.assertGolden(
        module, "ir/http_service_rest_json_1_client_codec.expected.erl");
    for (var fn : module.functions()) {
      assertThat(fn.name()).isNotBlank();
      assertThat(fn.clauses()).isNotEmpty();
    }
  }
}
