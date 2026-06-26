package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangAwsJsonIrTest {
    private static Model model;
    private static ServiceShape service;
    private static OperationShape getUserOp;
    private static HttpBindingIndex httpIndex;
    private static ErlangSymbolProvider provider;

    @BeforeAll
    static void setup() {
        model = loadFixtureModel();
        service = model.expectShape(ShapeId.from("smithy.beam.test.awsjson11#Json11Service"), ServiceShape.class);
        getUserOp = model.expectShape(ShapeId.from("smithy.beam.test.awsjson11#GetUser"), OperationShape.class);
        httpIndex = HttpBindingIndex.of(model);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        BeamErlangLayout layout = new BeamErlangLayout(settings, service.getId().getNamespace(), service);
        provider = new ErlangSymbolProvider(settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
    }

    @Test
    void encodeGetUserRequestMatchesGolden() throws IOException {
        ErlFunction fn = ErlangAwsJsonIr.encodeRequest(
                model, getUserOp, httpIndex, provider,
                "Json11Service", "application/x-amz-json-1.1", "event_stream");
        assertStructural(fn);
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/aws_json_encode_get_user_request.expected.erl"));
    }

    @Test
    void decodeGetUserResponseMatchesGolden() throws IOException {
        ErlFunction fn = ErlangAwsJsonIr.decodeResponse(
                model, getUserOp, httpIndex, provider, "event_stream");
        assertStructural(fn);
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/aws_json_decode_get_user_response.expected.erl"));
    }

    @Test
    void errorDispatchGetUserMatchesGolden() throws IOException {
        ErlFunction fn = ErlangAwsJsonIr.errorDispatch(model, getUserOp, provider);
        assertStructural(fn);
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/aws_json_error_dispatch_get_user.expected.erl"));
    }

    @Test
    void decodeGetUserRequestMatchesGolden() throws IOException {
        ErlFunction fn = ErlangAwsJsonIr.decodeRequest(
                model, getUserOp, httpIndex, provider, "event_stream");
        assertStructural(fn);
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/aws_json_decode_get_user_request.expected.erl"));
    }

    @Test
    void encodeGetUserResponseMatchesGolden() throws IOException {
        ErlFunction fn = ErlangAwsJsonIr.encodeResponse(
                model, getUserOp, httpIndex, provider,
                "application/x-amz-json-1.1", "event_stream");
        assertStructural(fn);
        assertThat(fn.asString()).isEqualTo(readExpectedString("ir/aws_json_encode_get_user_response.expected.erl"));
    }

    @Test
    void sharedCodecHelpersMatchesGolden() throws IOException {
        List<ErlFunction> functions = ErlangAwsJsonIr.sharedCodecHelpers(model, service, provider);
        assertThat(helpersAsString(functions)).isEqualTo(readExpectedString("ir/aws_json_shared_codec_helpers.expected.erl"));
        for (ErlFunction fn : functions) {
            assertStructural(fn);
        }
    }

    private static Model loadFixtureModel() {
        String idl = """
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

    private static String helpersAsString(List<ErlFunction> functions) {
        return functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
    }

    private static void assertStructural(ErlFunction fn) {
        assertThat(fn.name()).isNotBlank();
        assertThat(fn.clauses()).isNotEmpty();
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangAwsJsonIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
