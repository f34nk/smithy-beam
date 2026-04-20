package io.smithy.beam.elixir.codegen;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.client.ElixirClientSettings;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Test-only helper for building a minimal {@link ElixirContext} suitable for
 * exercising codecs and transports in isolation.
 *
 * <p>Lives in the {@code io.smithy.beam.elixir.codegen} package so the
 * {@code codec/} and {@code http/} sub-packages can both import it. The
 * unused {@link ElixirContext} record components ({@code modelTransformer},
 * {@code fileManifest}, {@code writerDelegator}) are intentionally
 * {@code null} — codecs and transports do not consult them.
 */
public final class CodegenTestSupport {

    private CodegenTestSupport() {
    }

    /**
     * A model + ready-to-use {@link ElixirContext} bound to the named service.
     */
    public record Fixture(Model model, ElixirContext ctx, ServiceShape service) {

        /** Returns the operation shape with the given absolute Smithy ID. */
        public OperationShape operation(String absoluteId) {
            return model.expectShape(ShapeId.from(absoluteId), OperationShape.class);
        }
    }

    /**
     * Assembles a Smithy model from a single inline source string and
     * returns a {@link Fixture} ready for codec/transport testing.
     */
    public static Fixture fixture(String smithyModel, String serviceId, String namespace) {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", smithyModel)
                .assemble()
                .unwrap();

        ElixirClientSettings settings = new ElixirClientSettings();
        settings.setService(ShapeId.from(serviceId));
        settings.setNamespace(namespace);
        settings.setEdition("2025");

        ServiceShape service = model.expectShape(ShapeId.from(serviceId), ServiceShape.class);
        SymbolProvider symbols = new ElixirSymbolProvider(model, settings, Mode.CLIENT);

        ElixirContext ctx = new ElixirContext(
                model,
                null,
                settings,
                symbols,
                null,
                null,
                List.of(),
                service);

        return new Fixture(model, ctx, service);
    }

    /** Returns a fresh writer for the given filename. */
    public static ElixirWriter writer(String filename) {
        return new ElixirWriter(filename);
    }
}
