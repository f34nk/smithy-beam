package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.SymbolDependency;

class ErlangDependencyTest {

    @Test
    void allConstants_haveSingleDependencyAndCorrectPrefix() {
        for (ErlangDependency dep : ErlangDependency.values()) {
            assertThat(dep.getDependencies()).hasSize(1);
            assertThat(dep.getResourcePath())
                    .startsWith("META-INF/smithy-beam/runtime/erlang/");
            assertThat(dep.getResourcePath()).endsWith(".erl");

            SymbolDependency dependency = dep.getDependencies().get(0);
            assertThat(dependency.getProperty("resourcePath", String.class))
                    .hasValue(dep.getResourcePath());
            assertThat(dependency.getPackageName()).isEqualTo("smithy-beam-runtime-erlang");
        }
    }

    @Test
    void smithyHttpClient_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_HTTP_CLIENT.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/client/smithy_http_client.erl");
    }

    @Test
    void smithyJson_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_JSON.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/client/smithy_json.erl");
    }

    @Test
    void smithyXml_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_XML.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/client/smithy_xml.erl");
    }

    @Test
    void smithySigV4_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_SIGV4.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/client/smithy_sigv4.erl");
    }

    @Test
    void smithyQuery_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_QUERY.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/client/smithy_query.erl");
    }

    @Test
    void smithyS3_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_S3.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/client/smithy_s3.erl");
    }

    @Test
    void smithyRouter_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_ROUTER.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/server/smithy_router.erl");
    }

    @Test
    void smithyHandler_hasExpectedResourcePath() {
        assertThat(ErlangDependency.SMITHY_HANDLER.getResourcePath())
                .isEqualTo("META-INF/smithy-beam/runtime/erlang/server/smithy_handler.erl");
    }
}
