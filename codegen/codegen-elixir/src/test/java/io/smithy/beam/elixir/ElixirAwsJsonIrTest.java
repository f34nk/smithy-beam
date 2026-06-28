package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirAwsJsonIrTest {
  private static Model model;
  private static ServiceShape service;
  private static OperationShape getUserOp;
  private static HttpBindingIndex httpIndex;
  private static ElixirSymbolProvider sp;
  private static String runtimeMod;
  private static String typesMod;
  private static String eventStreamModule;

  @BeforeAll
  static void setup() {
    model = loadFixtureModel();
    service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.awsjson11#Json11Service"), ServiceShape.class);
    getUserOp =
        model.expectShape(ShapeId.from("smithy.beam.test.awsjson11#GetUser"), OperationShape.class);
    httpIndex = HttpBindingIndex.of(model);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
  }

  @Test
  void buildClientCodecModuleHasExpectedStructure() {
    ExModule module =
        ElixirAwsJsonIr.buildClientCodecModule(
            clientContext(), service, BeamProtocolIds.AWS_JSON_1_1);
    assertThat(module.moduleName()).isNotBlank();
    assertThat(module.functions()).isNotEmpty();
    for (ExFunction fn : module.functions()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void encodeGetUserRequestMatchesGolden() throws IOException {
    ExFunction fn =
        ElixirAwsJsonOperationIr.buildEncodeRequest(
            model,
            getUserOp,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            "Json11Service",
            "application/x-amz-json-1.1",
            eventStreamModule);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_json_encode_get_user_request.expected.ex"));
  }

  @Test
  void decodeGetUserResponseMatchesGolden() throws IOException {
    ExFunction fn =
        ElixirAwsJsonOperationIr.buildDecodeResponse(
            model, getUserOp, httpIndex, sp, typesMod, runtimeMod, eventStreamModule);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_json_decode_get_user_response.expected.ex"));
  }

  @Test
  void decodeGetUserRequestMatchesGolden() throws IOException {
    ExFunction fn =
        ElixirAwsJsonOperationIr.buildDecodeRequest(
            model, getUserOp, httpIndex, sp, typesMod, runtimeMod, eventStreamModule);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_json_decode_get_user_request.expected.ex"));
  }

  @Test
  void encodeGetUserResponseMatchesGolden() throws IOException {
    ExFunction fn =
        ElixirAwsJsonOperationIr.buildEncodeResponse(
            model,
            getUserOp,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            "application/x-amz-json-1.1",
            eventStreamModule);
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_json_encode_get_user_response.expected.ex"));
  }

  private static ElixirContext clientContext() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider symbolProvider =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings,
                model,
                service,
                layout.typesModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
                BeamCodegenKind.CLIENT));
    MockManifest manifest = new MockManifest();
    Optional<ShapeId> resolved = BeamProtocolResolver.resolve(model, service, settings);
    return new ElixirContext(
        model,
        settings,
        symbolProvider,
        manifest,
        new WriterDelegator<>(
            manifest, symbolProvider, ElixirWriter.factory(layout.clientModuleName())),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        resolved.map(id -> BeamProtocolCodegenFactory.create(model, id, List.of())).orElse(null),
        resolved.orElse(null),
        layout.clientModuleName(),
        layout.clientModuleFile());
  }

  private static Model loadFixtureModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.awsjson11

                use aws.protocols#awsJson1_1
                use aws.api#service

                @awsJson1_1
                @service(sdkId: "Json11", endpointPrefix: "json11")
                service Json11Service {
                    version: "2026"
                    operations: [GetUser]
                }

                operation GetUser {
                    input: GetUserInput
                    output: GetUserOutput
                }

                structure GetUserInput {
                    userName: String
                }

                structure GetUserOutput {
                    userName: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("aws_json_1_1_fixture.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirAwsJsonIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
