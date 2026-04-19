package io.smithy.beam.elixir.codegen;

import java.util.List;
import software.amazon.smithy.codegen.core.SymbolDependency;
import software.amazon.smithy.codegen.core.SymbolDependencyContainer;

/**
 * Elixir runtime module dependencies bundled in the JAR under
 * {@code META-INF/smithy-beam/runtime/elixir/}.
 *
 * <p>{@link ElixirRuntimeIntegration} copies only the files that were actually
 * referenced (via {@link SymbolDependency}) during code generation.
 */
public enum ElixirDependency implements SymbolDependencyContainer {

    SMITHY_HTTP_CLIENT("client/smithy_http_client.ex"),
    SMITHY_JSON("client/smithy_json.ex"),
    SMITHY_XML("client/smithy_xml.ex"),
    SMITHY_SIGV4("client/smithy_sigv4.ex"),
    SMITHY_QUERY("client/smithy_query.ex"),
    SMITHY_S3("client/smithy_s3.ex"),
    SMITHY_CLIENT("client/smithy_client.ex"),
    SMITHY_CREDENTIALS("client/smithy_credentials.ex"),
    SMITHY_RETRY("client/smithy_retry.ex"),
    SMITHY_ROUTER("server/smithy_router.ex"),
    SMITHY_HANDLER("server/smithy_handler.ex"),
    SMITHY_SERVER("server/smithy_server.ex"),
    SMITHY_VALIDATOR("server/smithy_validator.ex"),
    SMITHY_ERROR_MAP("server/smithy_error_map.ex");

    private final String resourcePath;
    private final SymbolDependency dependency;

    ElixirDependency(String relativePath) {
        this.resourcePath = "META-INF/smithy-beam/runtime/elixir/" + relativePath;
        this.dependency = SymbolDependency.builder()
                .packageName("smithy-beam-runtime-elixir")
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
