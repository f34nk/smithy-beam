package io.smithy.beam.erlang.codegen;

import java.util.List;
import software.amazon.smithy.codegen.core.SymbolDependency;
import software.amazon.smithy.codegen.core.SymbolDependencyContainer;

/**
 * Erlang runtime module dependencies bundled in the JAR under
 * {@code META-INF/smithy-beam/runtime/erlang/}.
 *
 * <p>{@link ErlangRuntimeIntegration} copies only the files that were actually
 * referenced (via {@link SymbolDependency}) during code generation.
 */
public enum ErlangDependency implements SymbolDependencyContainer {

    SMITHY_HTTP_CLIENT("client/smithy_http_client.erl"),
    SMITHY_JSON("client/smithy_json.erl"),
    SMITHY_XML("client/smithy_xml.erl"),
    SMITHY_SIGV4("client/smithy_sigv4.erl"),
    SMITHY_QUERY("client/smithy_query.erl"),
    SMITHY_S3("client/smithy_s3.erl"),
    SMITHY_ENDPOINTS("client/smithy_endpoints.erl"),
    SMITHY_CREDENTIALS("client/smithy_credentials.erl"),
    SMITHY_RETRY("client/smithy_retry.erl"),
    SMITHY_CONFIG("client/smithy_config.erl"),
    SMITHY_PAGINATION("client/smithy_pagination.erl"),
    SMITHY_EVENT_STREAM("client/smithy_event_stream.erl"),
    SMITHY_ROUTER("server/smithy_router.erl"),
    SMITHY_HANDLER("server/smithy_handler.erl"),
    SMITHY_SERVER("server/smithy_server.erl"),
    SMITHY_VALIDATOR("server/smithy_validator.erl"),
    SMITHY_ERROR_MAP("server/smithy_error_map.erl");

    private final String resourcePath;
    private final SymbolDependency dependency;

    ErlangDependency(String relativePath) {
        this.resourcePath = "META-INF/smithy-beam/runtime/erlang/" + relativePath;
        this.dependency = SymbolDependency.builder()
                .packageName("smithy-beam-runtime-erlang")
                .version("0.1.0")
                .putProperty("resourcePath", this.resourcePath)
                .build();
    }

    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public List<SymbolDependency> getDependencies() {
        return List.of(dependency);
    }
}
