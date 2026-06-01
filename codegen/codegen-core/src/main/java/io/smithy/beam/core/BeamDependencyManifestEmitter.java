package io.smithy.beam.core;

import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.codegen.core.SymbolDependency;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateListDirective;
import software.amazon.smithy.codegen.core.directed.GenerateMapDirective;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes {@code smithy/beam_dependencies.json} from symbol dependencies collected
 * during DirectedCodegen.
 */
public final class BeamDependencyManifestEmitter {

    static final String MANIFEST_PATH = "smithy/beam_dependencies.json";

    private BeamDependencyManifestEmitter() {}

    /**
     * Wraps a {@link DirectedCodegen} delegate so the created context can be read
     * after {@link software.amazon.smithy.codegen.core.directed.CodegenDirector#run()}.
     */
    public static <
                    W extends SymbolWriter<W, ?>,
                    C extends CodegenContext<BeamSettings, W, I>,
                    I extends SmithyIntegration<BeamSettings, W, C>>
            DirectedCodegen<C, BeamSettings, I> capturingContext(
                    DirectedCodegen<C, BeamSettings, I> delegate, AtomicReference<C> contextRef) {
        return new ContextCapturingDirectedCodegen<>(delegate, contextRef);
    }

    /**
     * Emits dependency metadata collected by the given writer delegator.
     */
    public static void emit(FileManifest fileManifest, WriterDelegator<?> writerDelegator, BeamSettings settings) {
        List<SymbolDependency> dependencies = writerDelegator.getDependencies();

        Map<String, SymbolDependency> uniqueByName = new LinkedHashMap<>();
        for (SymbolDependency dependency : dependencies) {
            uniqueByName.putIfAbsent(dependency.getPackageName(), dependency);
        }

        ObjectNode.Builder root = ObjectNode.builder();
        String packageVersion = settings.packageVersion();
        if (packageVersion != null && !packageVersion.isBlank()) {
            root.withMember("packageVersion", packageVersion);
        }

        ArrayNode.Builder dependencyNodes = ArrayNode.builder();
        for (SymbolDependency dependency : uniqueByName.values()) {
            dependencyNodes.withValue(
                    ObjectNode.builder()
                            .withMember("name", dependency.getPackageName())
                            .withMember("version", dependency.getVersion())
                            .build());
        }
        root.withMember("dependencies", dependencyNodes.build());

        fileManifest.writeJson(MANIFEST_PATH, root.build());
    }

    private static final class ContextCapturingDirectedCodegen<
                    W extends SymbolWriter<W, ?>,
                    C extends CodegenContext<BeamSettings, W, I>,
                    I extends SmithyIntegration<BeamSettings, W, C>>
            implements DirectedCodegen<C, BeamSettings, I> {

        private final DirectedCodegen<C, BeamSettings, I> delegate;
        private final AtomicReference<C> contextRef;

        ContextCapturingDirectedCodegen(
                DirectedCodegen<C, BeamSettings, I> delegate, AtomicReference<C> contextRef) {
            this.delegate = delegate;
            this.contextRef = contextRef;
        }

        @Override
        public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<BeamSettings> directive) {
            return delegate.createSymbolProvider(directive);
        }

        @Override
        public C createContext(CreateContextDirective<BeamSettings, I> directive) {
            C context = delegate.createContext(directive);
            contextRef.set(context);
            return context;
        }

        @Override
        public void generateService(GenerateServiceDirective<C, BeamSettings> directive) {
            delegate.generateService(directive);
        }

        @Override
        public void generateResource(GenerateResourceDirective<C, BeamSettings> directive) {
            delegate.generateResource(directive);
        }

        @Override
        public void generateOperation(GenerateOperationDirective<C, BeamSettings> directive) {
            delegate.generateOperation(directive);
        }

        @Override
        public void generateStructure(GenerateStructureDirective<C, BeamSettings> directive) {
            delegate.generateStructure(directive);
        }

        @Override
        public void generateError(GenerateErrorDirective<C, BeamSettings> directive) {
            delegate.generateError(directive);
        }

        @Override
        public void generateUnion(GenerateUnionDirective<C, BeamSettings> directive) {
            delegate.generateUnion(directive);
        }

        @Override
        public void generateList(GenerateListDirective<C, BeamSettings> directive) {
            delegate.generateList(directive);
        }

        @Override
        public void generateMap(GenerateMapDirective<C, BeamSettings> directive) {
            delegate.generateMap(directive);
        }

        @Override
        public void generateEnumShape(GenerateEnumDirective<C, BeamSettings> directive) {
            delegate.generateEnumShape(directive);
        }

        @Override
        public void generateIntEnumShape(GenerateIntEnumDirective<C, BeamSettings> directive) {
            delegate.generateIntEnumShape(directive);
        }

        @Override
        public void customizeBeforeShapeGeneration(CustomizeDirective<C, BeamSettings> directive) {
            delegate.customizeBeforeShapeGeneration(directive);
        }

        @Override
        public void customizeBeforeIntegrations(CustomizeDirective<C, BeamSettings> directive) {
            delegate.customizeBeforeIntegrations(directive);
        }

        @Override
        public void customizeAfterIntegrations(CustomizeDirective<C, BeamSettings> directive) {
            delegate.customizeAfterIntegrations(directive);
        }
    }
}
