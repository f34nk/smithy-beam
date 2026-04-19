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
     * {@link FileManifest}.
     *
     * @param loader        class loader that owns the JAR containing the resources
     * @param resourcePaths classpath-relative paths of the files to copy
     * @param manifest      destination file manifest
     * @throws BeamCodegenException if a resource is missing or cannot be read
     */
    public static void copy(ClassLoader loader,
                            Collection<String> resourcePaths,
                            FileManifest manifest) {
        for (String resourcePath : resourcePaths) {
            URL url = loader.getResource(resourcePath);
            if (url == null) {
                throw new BeamCodegenException(
                    "Runtime resource not found on classpath: " + resourcePath);
            }
            String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            try (InputStream in = url.openStream()) {
                manifest.writeFile(filename, in);
            } catch (IOException e) {
                throw new BeamCodegenException(
                    "Failed to copy runtime resource: " + resourcePath, e);
            }
        }
    }
}
