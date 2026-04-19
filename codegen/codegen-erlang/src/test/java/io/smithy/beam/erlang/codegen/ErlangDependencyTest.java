package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    void serverDependenciesHaveServerSubPath() {
        assertThat(ErlangDependency.SMITHY_ROUTER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_router.erl");
        assertThat(ErlangDependency.SMITHY_HANDLER.getResourcePath())
                .isEqualTo(RUNTIME_PATH_PREFIX + "server/smithy_handler.erl");
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
    void enumHasEightConstants() {
        assertThat(ErlangDependency.values()).hasSize(8);
    }
}
