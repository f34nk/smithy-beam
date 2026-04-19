package io.smithy.beam.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import software.amazon.smithy.build.FileManifest;

/**
 * Copies hand-written runtime source files from the plugin JAR classpath into
 * the generated {@link FileManifest}.
 *
 * <p>Only the files whose paths were referenced via {@code SymbolDependency}
 * during code generation are copied; unused runtime modules are never emitted.
 */
public final class RuntimeResourceCopier {

    private RuntimeResourceCopier() {}

    /**
     * Copies each resource path from the given {@link ClassLoader} into the
     * {@link FileManifest}, preserving the sub-path structure under
     * {@code stripPrefix} and placing it under {@code outputPrefix}.
     *
     * <p>For example, with {@code stripPrefix = "META-INF/smithy-beam/runtime/erlang/"}
     * and {@code outputPrefix = "runtime/"}, the classpath resource
     * {@code META-INF/smithy-beam/runtime/erlang/client/smithy_http_client.erl}
     * is written to {@code runtime/client/smithy_http_client.erl} in the
     * manifest. Two resources that share a basename but live in different
     * sub-directories (e.g. {@code client/foo.erl} and {@code server/foo.erl})
     * therefore land in different output paths and never overwrite each other.
     *
     * <p>If a resource path does not start with {@code stripPrefix}, the full
     * classpath path is used as the relative output path (no stripping is done).
     *
     * @param loader        class loader that owns the JAR containing the resources
     * @param resourcePaths classpath-relative paths of the files to copy
     * @param manifest      destination file manifest
     * @param stripPrefix   classpath path prefix to strip before computing the
     *                      output path; must end with {@code "/"} if non-empty
     * @param outputPrefix  prefix to prepend to the output path in the manifest;
     *                      must end with {@code "/"} if non-empty
     * @throws BeamCodegenException if a resource is missing or cannot be read
     */
    public static void copy(ClassLoader loader,
                            Collection<String> resourcePaths,
                            FileManifest manifest,
                            String stripPrefix,
                            String outputPrefix) {
        for (String resourcePath : resourcePaths) {
            URL url = loader.getResource(resourcePath);
            if (url == null) {
                throw new BeamCodegenException(
                    "Runtime resource not found on classpath: " + resourcePath);
            }
            String relative = resourcePath.startsWith(stripPrefix)
                    ? resourcePath.substring(stripPrefix.length())
                    : resourcePath;
            String outputPath = outputPrefix + relative;
            try (InputStream in = url.openStream()) {
                manifest.writeFile(outputPath, in);
            } catch (IOException e) {
                throw new BeamCodegenException(
                    "Failed to copy runtime resource: " + resourcePath, e);
            }
        }
    }
}
