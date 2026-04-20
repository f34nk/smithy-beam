package io.smithy.beam.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.codegen.core.WriterDelegator;

/**
 * "Skip if exists" file emission helper for files that the user is expected
 * to hand-edit after they are first generated (most notably the
 * {@code *_server_impl} stubs produced by the BEAM server codegens).
 *
 * <p>Smithy's {@link FileManifest} always overwrites; instead of forking
 * {@code FileManifest}, this helper interposes a pre-emit existence check:
 *
 * <ol>
 *   <li>If the file is already enqueued in the {@link FileManifest} (e.g.
 *       another integration produced it), the consumer is not run.</li>
 *   <li>If the file already exists on disk under
 *       {@link BeamSettings#getProjectRoot()} (or the JVM working directory
 *       when {@code projectRoot} is unset), the consumer is not run.</li>
 *   <li>Otherwise, the writer is opened via
 *       {@link WriterDelegator#useFileWriter(String, Consumer)} and the
 *       consumer is invoked.</li>
 * </ol>
 *
 * <p>The check against the consumer's filesystem is what gives the
 * "WILL NOT BE OVERWRITTEN" guarantee: the staging directory used by
 * {@code SmithyBuild} is always empty at the start of a build, so a
 * naive {@code FileManifest}-only check would never see a previous
 * generation's output.
 */
public final class ImplFileGuard {

    private ImplFileGuard() {}

    /**
     * Emits the file if neither the manifest nor the consumer's project
     * root already contains it.
     *
     * @param settings        Settings carrying the optional {@code projectRoot}.
     * @param manifest        File manifest to check for already-enqueued files.
     * @param delegator       Writer delegator used to enqueue the new writer.
     * @param relativePath    Path of the file relative to the manifest base
     *                        (and relative to {@code projectRoot}).
     * @param writerConsumer  Body invoked with a fresh writer when the file
     *                        is to be generated.
     * @return {@code true} if the writer was opened and the consumer ran,
     *         {@code false} if the file was skipped because it already exists.
     */
    public static <W extends SymbolWriter<W, ? extends ImportContainer>> boolean useFileWriterIfAbsent(
            BeamSettings settings,
            FileManifest manifest,
            WriterDelegator<W> delegator,
            String relativePath,
            Consumer<W> writerConsumer) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(delegator, "delegator");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(writerConsumer, "writerConsumer");

        if (manifest.hasFile(relativePath)) {
            return false;
        }
        Path target = resolveTarget(settings, relativePath);
        if (Files.exists(target)) {
            return false;
        }
        delegator.useFileWriter(relativePath, writerConsumer);
        return true;
    }

    /**
     * Resolves the absolute path that would receive the file in the
     * consumer's project tree. Exposed for tests.
     */
    public static Path resolveTarget(BeamSettings settings, String relativePath) {
        Path projectRoot = settings.getProjectRoot() == null
                ? Path.of("").toAbsolutePath()
                : Path.of(settings.getProjectRoot());
        return projectRoot.resolve(relativePath);
    }
}
