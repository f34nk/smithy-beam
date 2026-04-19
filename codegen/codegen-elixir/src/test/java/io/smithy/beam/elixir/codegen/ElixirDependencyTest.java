package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ElixirDependencyTest {

    private static final String RUNTIME_PATH_PREFIX = "META-INF/smithy-beam/runtime/elixir/";

    @Test
    void allConstantsHaveResourcePathWithCorrectPrefix() {
        for (ElixirDependency dep : ElixirDependency.values()) {
            assertThat(dep.getResourcePath())
                    .as("resourcePath for %s", dep)
                    .startsWith(RUNTIME_PATH_PREFIX);
        }
    }

    @Test
    void allConstantsHaveExFileExtension() {
        for (ElixirDependency dep : ElixirDependency.values()) {
            assertThat(dep.getResourcePath())
                    .as("resourcePath for %s should end with .ex", dep)
                    .endsWith(".ex");
        }
    }

    @Test
    void everyConstantsResourceExistsOnClasspath() {
        for (ElixirDependency dep : ElixirDependency.values()) {
            try (InputStream in = ElixirDependency.class.getClassLoader().getResourceAsStream(dep.getResourcePath())) {
                assertThat(in)
                        .as("classpath resource for %s at %s", dep, dep.getResourcePath())
                        .isNotNull();
            } catch (IOException e) {
                throw new AssertionError("failed to read classpath resource " + dep.getResourcePath(), e);
            }
        }
    }

    @Test
    void clientDependenciesHaveClientSubPath() {
        assertThat(ElixirDependency.SMITHY_HTTP_CLIENT.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_http_client.ex");
        assertThat(ElixirDependency.SMITHY_JSON.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_json.ex");
        assertThat(ElixirDependency.SMITHY_XML.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_xml.ex");
        assertThat(ElixirDependency.SMITHY_SIGV4.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_sigv4.ex");
        assertThat(ElixirDependency.SMITHY_QUERY.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_query.ex");
        assertThat(ElixirDependency.SMITHY_S3.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_s3.ex");
        assertThat(ElixirDependency.SMITHY_CLIENT.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_client.ex");
        assertThat(ElixirDependency.SMITHY_CREDENTIALS.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_credentials.ex");
        assertThat(ElixirDependency.SMITHY_RETRY.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_retry.ex");
    }

    @Test
    void serverDependenciesHaveServerSubPath() {
        assertThat(ElixirDependency.SMITHY_ROUTER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_router.ex");
        assertThat(ElixirDependency.SMITHY_HANDLER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_handler.ex");
        assertThat(ElixirDependency.SMITHY_SERVER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_server.ex");
        assertThat(ElixirDependency.SMITHY_VALIDATOR.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_validator.ex");
        assertThat(ElixirDependency.SMITHY_ERROR_MAP.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_error_map.ex");
    }

    @Test
    void eachConstantHasExactlyOneSymbolDependency() {
        for (ElixirDependency dep : ElixirDependency.values()) {
            assertThat(dep.getDependencies())
                    .as("getDependencies() for %s", dep)
                    .hasSize(1);
        }
    }

    @Test
    void symbolDependencyHasResourcePathProperty() {
        for (ElixirDependency dep : ElixirDependency.values()) {
            assertThat(dep.getDependencies().get(0).getProperty("resourcePath", String.class))
                    .as("resourcePath property on SymbolDependency for %s", dep)
                    .isPresent()
                    .hasValue(dep.getResourcePath());
        }
    }

    @Test
    void symbolDependencyPackageNameIsSmithyBeamRuntimeElixir() {
        for (ElixirDependency dep : ElixirDependency.values()) {
            assertThat(dep.getDependencies().get(0).getPackageName())
                    .as("packageName for %s", dep)
                    .isEqualTo("smithy-beam-runtime-elixir");
        }
    }

    @Test
    void enumHasFourteenConstants() {
        assertThat(ElixirDependency.values()).hasSize(14);
    }
}
