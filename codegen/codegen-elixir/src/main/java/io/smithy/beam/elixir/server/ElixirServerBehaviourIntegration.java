package io.smithy.beam.elixir.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.sections.ModuleAttributesSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Injects the {@code @behaviour SmithyHandler} attribute into server modules.
 *
 * <p>Guards on {@link Mode#SERVER} so it is safe to register in the shared
 * {@code ElixirIntegration} SPI file — when the client plugin runs the mode will
 * be {@link Mode#CLIENT} and this integration becomes a no-op.
 *
 * <p>Uses the {@link #interceptors} hook so the behaviour attribute is written
 * into the {@link ModuleAttributesSection} injection point created by
 * {@link ElixirServerCodegen#generateService}, before any other content in the
 * generated server module.
 */
public final class ElixirServerBehaviourIntegration implements ElixirIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (ctx.settings().mode() != Mode.SERVER) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(ModuleAttributesSection.class, (writer, section) -> {
                    writer.addDependency(ElixirDependency.SMITHY_HANDLER);
                    writer.write("@behaviour SmithyHandler");
                }));
    }
}
