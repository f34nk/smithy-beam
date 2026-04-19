package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.RuntimeResourceCopier;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copies only the Erlang runtime source files that were actually referenced
 * (via {@link ErlangDependency} symbols) during code generation into the
 * {@code FileManifest}.
 *
 * <p>Unconditionally active for every generation pass. Hooks into
 * {@link #customize(ErlangContext)} which runs after all integrations have
 * finished shape generation.
 */
public final class ErlangRuntimeIntegration implements ErlangIntegration {

    @Override
    public void customize(ErlangContext ctx) {
        Set<String> paths = ctx.writerDelegator()
                .getDependencies().stream()
                .map(dep -> dep.getProperty("resourcePath", String.class))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                paths,
                ctx.fileManifest(),
                "META-INF/smithy-beam/runtime/erlang/",
                "runtime/");
    }
}
