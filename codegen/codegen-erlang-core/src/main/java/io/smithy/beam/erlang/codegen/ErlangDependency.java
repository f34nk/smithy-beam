package io.smithy.beam.erlang.codegen;

import java.util.List;
import software.amazon.smithy.codegen.core.SymbolDependency;
import software.amazon.smithy.codegen.core.SymbolDependencyContainer;

/**
 * Classpath-bundled Erlang runtime files that generated code depends on.
 *
 * <p>Each constant maps a well-known runtime module (e.g.
 * {@code smithy_json.erl}) to a {@link SymbolDependency} whose
 * {@code "resourcePath"} property identifies the classpath resource path
 * under {@code META-INF/smithy-beam/runtime/erlang/}.
 *
 * <p>Protocol and auth integrations add the appropriate constants to operation
 * symbols; {@code ErlangRuntimeIntegration} later collects the unique set of
 * resource paths from the writer delegator's dependency graph and copies them
 * into the file manifest via {@link io.smithy.beam.core.RuntimeResourceCopier}.
 */
public enum ErlangDependency implements SymbolDependencyContainer {

    SMITHY_HTTP_CLIENT("client/smithy_http_client.erl"),
    SMITHY_JSON("client/smithy_json.erl"),
    SMITHY_XML("client/smithy_xml.erl"),
    SMITHY_SIGV4("client/smithy_sigv4.erl"),
    SMITHY_QUERY("client/smithy_query.erl"),
    SMITHY_S3("client/smithy_s3.erl"),
    SMITHY_ROUTER("server/smithy_router.erl"),
    SMITHY_HANDLER("server/smithy_handler.erl");

    private static final String RESOURCE_PREFIX = "META-INF/smithy-beam/runtime/erlang/";

    private final String resourcePath;
    private final SymbolDependency dependency;

    ErlangDependency(String relativePath) {
        this.resourcePath = RESOURCE_PREFIX + relativePath;
        this.dependency = SymbolDependency.builder()
                .packageName("smithy-beam-runtime-erlang")
                .version("0.1.0")
                .putProperty("resourcePath", this.resourcePath)
                .build();
    }

    /**
     * Returns the full classpath resource path for this runtime module,
     * e.g. {@code "META-INF/smithy-beam/runtime/erlang/client/smithy_json.erl"}.
     */
    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public List<SymbolDependency> getDependencies() {
        return List.of(dependency);
    }
}
