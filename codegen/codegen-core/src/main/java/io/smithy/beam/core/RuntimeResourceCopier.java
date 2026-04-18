package io.smithy.beam.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import software.amazon.smithy.build.FileManifest;

/**
 * Copies a set of classpath resources into a {@link FileManifest}.
 *
 * <p>Used by {@code <Lang>RuntimeIntegration} inside
 * {@code customizeAfterIntegrations} to write bundled runtime Erlang/Elixir
 * source files into the generated output directory.
 */
public final class RuntimeResourceCopier {

    private RuntimeResourceCopier() {}

    /**
     * Copies all resources listed in {@code resourcePaths} from {@code loader}
     * into {@code manifest}.
     *
     * @param loader        ClassLoader to resolve resources from
     * @param resourcePaths paths relative to the classpath root, e.g.
     *                      {@code "META-INF/smithy-beam/runtime/erlang/client/smithy_http_client.erl"}
     * @param manifest      destination FileManifest
     * @throws BeamCodegenException if a resource is not found or cannot be read
     */
    public static void copy(ClassLoader loader,
                            Collection<String> resourcePaths,
                            FileManifest manifest) {
        for (String resourcePath : resourcePaths) {
            try (InputStream in = loader.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new BeamCodegenException(
                            "Runtime resource not found on classpath: " + resourcePath);
                }
                String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                manifest.writeFile(filename, in);
            } catch (IOException e) {
                throw new BeamCodegenException(
                        "Failed to copy runtime resource: " + resourcePath, e);
            }
        }
    }
}
