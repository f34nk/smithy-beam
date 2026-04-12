package io.smithy.beam.core.output;

import software.amazon.smithy.build.FileManifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Writes generated files and copies runtime resources via a Smithy {@link FileManifest}.
 */
public final class FileOutput {

    private static final String RUNTIME_PREFIX = "META-INF/smithy-beam/runtime/";

    private final FileManifest manifest;

    /**
     * @param fileExtension reserved for future path defaults (e.g. from {@code LanguageWriter#fileExtension()})
     */
    public FileOutput(FileManifest manifest, @SuppressWarnings("unused") String fileExtension) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
    }

    public void write(String filename, String content) {
        manifest.writeFile(filename, content);
    }

    /**
     * Writes under {@code dir} only if the target file is not already present in the manifest.
     */
    public void writeIfAbsent(String dir, String filename, String content) {
        String path = normalizePath(dir, filename);
        if (manifest.hasFile(path)) {
            return;
        }
        write(path, content);
    }

    /**
     * Copies a resource bundled in a codegen JAR at {@code META-INF/smithy-beam/runtime/{languageId}/{resourcePath}}
     * into the manifest output. If the file already exists with identical content, does nothing.
     *
     * @param languageId e.g. {@code "erlang"}
     * @param resourcePath path under the language folder, e.g. {@code "client/aws_sigv4.erl"}
     */
    public void copyRuntime(String languageId, String resourcePath, ClassLoader resourceLoader) {
        String classpath = RUNTIME_PREFIX + languageId + "/" + resourcePath;
        try (InputStream in = resourceLoader.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("Missing runtime resource: " + classpath);
            }
            byte[] bytes = in.readAllBytes();
            String relative = resourcePath;
            if (manifest.hasFile(relative)) {
                Path existing = manifest.resolvePath(manifest.getBaseDir().resolve(relative));
                if (Files.exists(existing)) {
                    byte[] current = Files.readAllBytes(existing);
                    if (Arrays.equals(current, bytes)) {
                        return;
                    }
                }
            }
            manifest.writeFile(relative, new java.io.ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uses {@link ClassLoader#getSystemResourceAsStream(String)} (per build plugin convention). */
    public void copyRuntime(String languageId, String resourcePath) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        copyRuntime(languageId, resourcePath, cl);
    }

    private static String normalizePath(String dir, String filename) {
        String d = dir.endsWith("/") ? dir : dir + "/";
        return d + filename;
    }
}
