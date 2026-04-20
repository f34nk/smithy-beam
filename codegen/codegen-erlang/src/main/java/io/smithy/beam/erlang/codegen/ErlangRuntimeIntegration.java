package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.Mode;
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
 *
 * <p>Runtime files are written directly alongside the generated sources inside
 * {@code outputDir} — no separate {@code runtime/} sub-tree is created.
 */
public final class ErlangRuntimeIntegration implements ErlangIntegration {

    private static final String JAR_ROOT = "META-INF/smithy-beam/runtime/erlang/";

    @Override
    public void customize(ErlangContext ctx) {
        Set<String> paths = ctx.writerDelegator()
                .getDependencies().stream()
                .map(dep -> dep.getProperty("resourcePath", String.class))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        String outputDir = ctx.settings().getOutputDir();
        String outputPrefix = outputDir.endsWith("/") ? outputDir : outputDir + "/";
        String modeSubDir = ctx.settings().mode() == Mode.SERVER ? "server/" : "client/";

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                paths,
                ctx.fileManifest(),
                JAR_ROOT + modeSubDir,
                outputPrefix);
    }
}
