package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ErlangDependencyTest {

    private static final String RUNTIME_PATH_PREFIX = "META-INF/smithy-beam/runtime/erlang/";

    @Test
    void allConstantsHaveResourcePathWithCorrectPrefix() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            assertThat(dep.getResourcePath())
                    .as("resourcePath for %s", dep)
                    .startsWith(RUNTIME_PATH_PREFIX);
        }
    }

    @Test
    void allConstantsHaveErlFileExtension() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            assertThat(dep.getResourcePath())
                    .as("resourcePath for %s should end with .erl", dep)
                    .endsWith(".erl");
        }
    }

    @Test
    void everyConstantsResourceExistsOnClasspath() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            try (InputStream in = ErlangDependency.class.getClassLoader().getResourceAsStream(dep.getResourcePath())) {
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
        assertThat(ErlangDependency.SMITHY_HTTP_CLIENT.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_http_client.erl");
        assertThat(ErlangDependency.SMITHY_JSON.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_json.erl");
        assertThat(ErlangDependency.SMITHY_XML.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_xml.erl");
        assertThat(ErlangDependency.SMITHY_SIGV4.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_sigv4.erl");
        assertThat(ErlangDependency.SMITHY_QUERY.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_query.erl");
        assertThat(ErlangDependency.SMITHY_S3.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_s3.erl");
        assertThat(ErlangDependency.SMITHY_ENDPOINTS.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_endpoints.erl");
        assertThat(ErlangDependency.SMITHY_CREDENTIALS.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_credentials.erl");
        assertThat(ErlangDependency.SMITHY_RETRY.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_retry.erl");
        assertThat(ErlangDependency.SMITHY_CONFIG.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "client/smithy_config.erl");
    }

    @Test
    void serverDependenciesHaveServerSubPath() {
        assertThat(ErlangDependency.SMITHY_ROUTER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_router.erl");
        assertThat(ErlangDependency.SMITHY_HANDLER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_handler.erl");
        assertThat(ErlangDependency.SMITHY_SERVER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_server.erl");
        assertThat(ErlangDependency.SMITHY_VALIDATOR.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_validator.erl");
        assertThat(ErlangDependency.SMITHY_ERROR_MAP.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_error_map.erl");
    }

    @Test
    void eachConstantHasExactlyOneSymbolDependency() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            assertThat(dep.getDependencies())
                    .as("getDependencies() for %s", dep)
                    .hasSize(1);
        }
    }

    @Test
    void symbolDependencyHasResourcePathProperty() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            assertThat(dep.getDependencies().get(0).getProperty("resourcePath", String.class))
                    .as("resourcePath property on SymbolDependency for %s", dep)
                    .isPresent()
                    .hasValue(dep.getResourcePath());
        }
    }

    @Test
    void symbolDependencyPackageNameIsSmithyBeamRuntimeErlang() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            assertThat(dep.getDependencies().get(0).getPackageName())
                    .as("packageName for %s", dep)
                    .isEqualTo("smithy-beam-runtime-erlang");
        }
    }

    @Test
    void enumHasFifteenConstants() {
        assertThat(ErlangDependency.values()).hasSize(15);
    }
}
