package io.smithy.beam.core.output;

import software.amazon.smithy.build.FileManifest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

/**
 * Writes generated files and copies runtime resources.
 *
 * <p>Two modes:
 * <ul>
 *   <li><strong>Manifest mode</strong> (for tests) — delegates to a Smithy {@link FileManifest}.
 *       Create with {@link #FileOutput(FileManifest, String)}.</li>
 *   <li><strong>Filesystem mode</strong> (for production plugins) — writes directly to disk relative
 *       to the current working directory.  Create with {@link #forPlugin(String)}.</li>
 * </ul>
 *
 * <p>Runtime resources copied by {@link #copyRuntime} are always written under {@code outputDir}
 * using only the file's base name (directory segments in the resource path are stripped).
 */
public final class FileOutput {

    private static final String RUNTIME_PREFIX = "META-INF/smithy-beam/runtime/";

    // ── manifest mode ──────────────────────────────────────────────────────────
    private final FileManifest manifest;

    // ── filesystem mode ────────────────────────────────────────────────────────
    /** Absolute project root resolved at construction time. */
    private final Path projectRoot;
    /** {@code outputDir} from plugin settings, e.g. {@code "src/generated"}. */
    private final String outputDir;

    // ── constructors ───────────────────────────────────────────────────────────

    /**
     * Manifest-based mode.
     *
     * @param fileExtension unused; kept for API compatibility
     */
    public FileOutput(FileManifest manifest, @SuppressWarnings("unused") String fileExtension) {
        this.manifest   = Objects.requireNonNull(manifest, "manifest");
        this.projectRoot = null;
        this.outputDir   = null;
    }

    private FileOutput(Path projectRoot, String outputDir) {
        this.manifest    = null;
        this.projectRoot = Objects.requireNonNull(projectRoot);
        this.outputDir   = Objects.requireNonNull(outputDir);
    }

    /**
     * Factory for use inside Smithy build plugins.
     *
     * <p>Files are written to {@code <cwd>/<outputDir>/<path>}, making the generated
     * output land directly in the project source tree rather than the Smithy build cache.
     *
     * @param outputDir value of {@code outputDir} from plugin settings (e.g. {@code "src/generated"})
     */
    public static FileOutput forPlugin(String outputDir) {
        return new FileOutput(Paths.get("").toAbsolutePath(), outputDir);
    }

    // ── public API ─────────────────────────────────────────────────────────────

    /**
     * Writes {@code content} to {@code path}.
     *
     * <p>In manifest mode the path is passed as-is to {@link FileManifest#writeFile}.
     * In filesystem mode the path is resolved against the project root.
     */
    public void write(String path, String content) {
        if (manifest != null) {
            manifest.writeFile(path, content);
        } else {
            writeToDisk(projectRoot.resolve(path), content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Writes {@code content} only if {@code dir/filename} is not already present.
     */
    public void writeIfAbsent(String dir, String filename, String content) {
        String path = normalizePath(dir, filename);
        if (manifest != null) {
            if (!manifest.hasFile(path)) {
                write(path, content);
            }
        } else {
            Path target = projectRoot.resolve(path);
            if (!Files.exists(target)) {
                writeToDisk(target, content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Copies a bundled runtime resource into the output.
     *
     * <p>The resource is located at
     * {@code META-INF/smithy-beam/runtime/{languageId}/{resourcePath}} inside the plugin JAR.
     * Only the file's <em>base name</em> is used for the output path (directory segments in
     * {@code resourcePath} are stripped), so e.g. {@code "client/smithy_retry.erl"} is written
     * as {@code smithy_retry.erl} directly under {@code outputDir}.
     *
     * <p>Idempotent: skips the write if an identical file already exists.
     *
     * @param languageId   e.g. {@code "erlang"}
     * @param resourcePath path under the language folder, e.g. {@code "client/smithy_sigv4.erl"}
     */
    public void copyRuntime(String languageId, String resourcePath, ClassLoader resourceLoader) {
        String classpath = RUNTIME_PREFIX + languageId + "/" + resourcePath;
        String filename  = Paths.get(resourcePath).getFileName().toString();

        try (InputStream in = resourceLoader.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("Missing runtime resource: " + classpath);
            }
            byte[] bytes = in.readAllBytes();

            if (manifest != null) {
                // Manifest mode: write at just the filename (no client/ prefix).
                if (manifest.hasFile(filename)) {
                    Path existing = manifest.resolvePath(manifest.getBaseDir().resolve(filename));
                    if (Files.exists(existing) && Arrays.equals(Files.readAllBytes(existing), bytes)) {
                        return;
                    }
                }
                manifest.writeFile(filename, new ByteArrayInputStream(bytes));
            } else {
                // Filesystem mode: write to outputDir/filename.
                Path target = projectRoot.resolve(outputDir).resolve(filename);
                if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                    writeToDisk(target, bytes);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Convenience overload using the system class loader. */
    public void copyRuntime(String languageId, String resourcePath) {
        copyRuntime(languageId, resourcePath, ClassLoader.getSystemClassLoader());
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private static void writeToDisk(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalizePath(String dir, String filename) {
        String d = dir.endsWith("/") ? dir : dir + "/";
        return d + filename;
    }
}
