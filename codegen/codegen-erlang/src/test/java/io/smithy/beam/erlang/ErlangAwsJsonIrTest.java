package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangAwsJsonIrTest {
  private static Model model;
  private static ServiceShape service;
  private static OperationShape getUserOp;
  private static HttpBindingIndex httpIndex;
  private static ErlangSymbolProvider provider;

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
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    provider =
        new ErlangSymbolProvider(
            settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
  }

  @Test
  void buildClientCodecModuleHasExpectedStructure() {
    Module module =
        ErlangAwsJsonIr.buildClientCodecModule(
            clientContext(), service, BeamProtocolIds.AWS_JSON_1_1);
    assertThat(module.name()).isNotBlank();
    assertThat(module.functions()).isNotEmpty();
    for (Function fn : module.functions()) {
      assertStructural(fn);
    }
  }

  @Test
  void encodeGetUserRequestMatchesGolden() throws IOException {
    Function fn =
        ErlangAwsJsonIr.encodeRequest(
            model,
            getUserOp,
            httpIndex,
            provider,
            "Json11Service",
            "application/x-amz-json-1.1",
            "event_stream");
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/aws_json_encode_get_user_request.expected.erl");
  }

  @Test
  void decodeGetUserResponseMatchesGolden() throws IOException {
    Function fn =
        ErlangAwsJsonIr.decodeResponse(model, getUserOp, httpIndex, provider, "event_stream");
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/aws_json_decode_get_user_response.expected.erl");
  }

  @Test
  void errorDispatchGetUserMatchesGolden() throws IOException {
    Function fn = ErlangAwsJsonIr.errorDispatch(model, getUserOp, provider);
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/aws_json_error_dispatch_get_user.expected.erl");
  }

  @Test
  void decodeGetUserRequestMatchesGolden() throws IOException {
    Function fn =
        ErlangAwsJsonIr.decodeRequest(model, getUserOp, httpIndex, provider, "event_stream");
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/aws_json_decode_get_user_request.expected.erl");
  }

  @Test
  void encodeGetUserResponseMatchesGolden() throws IOException {
    Function fn =
        ErlangAwsJsonIr.encodeResponse(
            model, getUserOp, httpIndex, provider, "application/x-amz-json-1.1", "event_stream");
    assertStructural(fn);
    IrGoldenAssertions.assertGolden(fn, "ir/aws_json_encode_get_user_response.expected.erl");
  }

  @Test
  void sharedCodecHelpersMatchesGolden() throws IOException {
    List<Function> functions = ErlangAwsJsonIr.sharedCodecHelpers(model, service, provider);
    assertThat(helpersAsString(functions))
        .isEqualTo(readExpectedString("ir/aws_json_shared_codec_helpers.expected.erl"));
    for (Function fn : functions) {
      assertStructural(fn);
    }
  }

  private static Model loadFixtureModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.awsjson11

                use aws.protocols#awsJson1_1
                use aws.api#service

                @service(sdkId: "Json11")
                @awsJson1_1
                service Json11Service {
                    version: "2026"
                    operations: [GetUser]
                }

                operation GetUser {
                    input: GetUserInput
                    output: GetUserOutput
                }

                structure GetUserInput {
                    userId: String
                }

                structure GetUserOutput {
                    userId: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("awsjson11.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static ErlangContext clientContext() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
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

  private static String helpersAsString(List<Function> functions) {
    return functions.stream().map(ErlangRenderer::renderFunction).collect(Collectors.joining("\n\n"));
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangAwsJsonIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
