package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.RuntimeResourceCopier;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Built-in integration that copies required Erlang runtime modules into the
 * generated output directory after all other integrations have run.
 *
 * <p>This integration is unconditionally active. It inspects every
 * {@link software.amazon.smithy.codegen.core.SymbolDependency} accumulated by
 * the writer delegator during code generation, extracts the ones that carry a
 * {@code "resourcePath"} property, and uses
 * {@link RuntimeResourceCopier} to copy the corresponding classpath resources
 * into the file manifest.
 *
 * <p>The {@code "resourcePath"} property is set by {@link ErlangDependency} on
 * each of its {@code SymbolDependency} instances. Protocol and auth integrations
 * register the relevant {@code ErlangDependency} constants via
 * {@code writer.addDependency(...)}, so only the runtime modules actually
 * referenced by a generated service are copied — nothing unused is emitted.
 *
 * <p>Priority is set to {@link Byte#MAX_VALUE} so this integration runs last,
 * after all code-emitting integrations have had a chance to register their
 * runtime dependencies.
 */
public final class ErlangRuntimeIntegration implements ErlangIntegration {

    @Override
    public byte priority() {
        return Byte.MAX_VALUE;
    }

    @Override
    public void customize(ErlangContext context) {
        Set<String> resourcePaths = context.writerDelegator()
                .getDependencies()
                .stream()
                .map(dep -> dep.getProperty("resourcePath", String.class))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        if (resourcePaths.isEmpty()) {
            return;
        }

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                resourcePaths,
                context.fileManifest());
    }
}
