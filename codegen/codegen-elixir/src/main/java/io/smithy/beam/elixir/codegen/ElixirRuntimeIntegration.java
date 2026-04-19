package io.smithy.beam.elixir.codegen;

import io.smithy.beam.core.RuntimeResourceCopier;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copies only the Elixir runtime source files that were actually referenced
 * (via {@link ElixirDependency} symbols) during code generation into the
 * {@code FileManifest}.
 *
 * <p>Unconditionally active for every generation pass. Hooks into
 * {@link #customize(ElixirContext)} which runs after all integrations have
 * finished shape generation.
 */
public final class ElixirRuntimeIntegration implements ElixirIntegration {

    @Override
    public void customize(ElixirContext ctx) {
        Set<String> paths = ctx.writerDelegator()
                .getDependencies().stream()
                .map(dep -> dep.getProperty("resourcePath", String.class))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                paths,
                ctx.fileManifest(),
                "META-INF/smithy-beam/runtime/elixir/",
                "runtime/");
    }
}
