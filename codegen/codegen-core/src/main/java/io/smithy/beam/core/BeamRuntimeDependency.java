package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.SymbolDependency;
import software.amazon.smithy.codegen.core.SymbolDependencyContainer;

import java.util.Collections;
import java.util.List;

/**
 * Runtime library dependencies attached to generated Beam symbols.
 */
public enum BeamRuntimeDependency implements SymbolDependencyContainer {
    JSX("deps", "jsx", "3.1.0"),
    AWS_SIGV4("deps", "aws_sigv4", "1.0.0");

    public final SymbolDependency dependency;

    BeamRuntimeDependency(String type, String name, String version) {
        this.dependency =
                SymbolDependency.builder()
                        .dependencyType(type)
                        .packageName(name)
                        .version(version)
                        .build();
    }

    @Override
    public List<SymbolDependency> getDependencies() {
        return Collections.singletonList(dependency);
    }
}
