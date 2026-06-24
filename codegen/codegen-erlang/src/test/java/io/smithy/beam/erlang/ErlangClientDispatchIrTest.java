package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangClientDispatchIrTest {
    private static final String SERVICE = "smithy.beam.demo.http#HttpService";

    private static Model httpModel() {
        String idl = """
                $version: "2"
                namespace smithy.beam.demo.http

                use aws.protocols#restJson1

                string Name

                @restJson1
                service HttpService {
                    version: "2026"
                    operations: [GetName]
                }

                @readonly
                @http(method: "GET", uri: "/names/{name}", code: 200)
                operation GetName {
                    input: GetNameInput
                    output: GetNameOutput
                }

                structure GetNameInput {
                    @required
                    @httpLabel
                    name: Name
                }

                structure GetNameOutput {
                    name: Name
                }
                """;
        return Model.assembler()
                .addUnparsedModel("http.smithy", idl)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static ErlangContext testContext(Model model) {
        ServiceShape service = model.expectShape(ShapeId.from(SERVICE), ServiceShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        BeamErlangLayout layout = new BeamErlangLayout(settings, service.getId().getNamespace(), service);
        SymbolProvider sp = SymbolProvider.cache(
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

    private static BeamErlangLayout layout(Model model) {
        ServiceShape service = model.expectShape(ShapeId.from(SERVICE), ServiceShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        return new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    }

    @Test
    void restJsonOperationBodyMatchesGolden() throws IOException {
        Model model = httpModel();
        OperationShape op = model.expectShape(ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
        ErlFunction body = ErlangClientDispatchIr.operationBody(
                testContext(model),
                op,
                layout(model),
                false,
                "retry_mod",
                false,
                ErlangClientDispatchEmitter.DispatchBodyMode.SINGLE_PAGE);
        assertThat(body.asString()).isEqualTo(readExpectedString("ir/client_dispatch_get_name.expected.erl"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangClientDispatchIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
