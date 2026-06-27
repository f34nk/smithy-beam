package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangHostLabelIrTest {
    @Test
    void hostLabelHelpersAsStringMatchesGolden() throws IOException {
        Model model = hostLabelModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.test.hostlabel#HostLabelService"), ServiceShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        ErlangSymbolProvider sp = new ErlangSymbolProvider(
                settings, model, service, "host_label_types.hrl", BeamCodegenKind.CLIENT);
        List<ErlFunction> functions = ErlangHostLabelIr.buildHostFunctions(model, service, sp);
        assertThat(functions).hasSize(2);
        String combined = functions.get(0).asString() + "\n\n" + functions.get(1).asString();
        assertThat(combined).isEqualTo(readExpectedString("ir/host_label_helpers.expected.erl"));
    }

    private static Model hostLabelModel() {
        String idl = """
                $version: "2"
                namespace smithy.beam.test.hostlabel

                use aws.protocols#restJson1

                @restJson1
                service HostLabelService {
                    version: "2026"
                    operations: [GetTenantData]
                }

                @endpoint(hostPrefix: "{tenant}.")
                @http(method: "GET", uri: "/data/{tenant}")
                operation GetTenantData {
                    input: GetTenantDataInput
                    output: GetTenantDataOutput
                }

                structure GetTenantDataInput {
                    @required
                    @hostLabel
                    @httpLabel
                    tenant: String
                }

                structure GetTenantDataOutput {
                    value: String
                }
                """;
        return Model.assembler()
                .addUnparsedModel("test.smithy", idl)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangHostLabelIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
