package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

/**
 * Applies the standard Smithy-Build directed-codegen model transforms for smithy-beam,
 * driven by {@link BeamSettings}. Keeps Erlang and Elixir generators aligned on
 * transform order.
 */
public final class BeamCodegenTransforms {

    private BeamCodegenTransforms() {}

    /**
     * Registers transforms on the given director in a fixed order:
     * default service codegen simplification (mixin flatten, service errors copied to operations),
     * dedicated operation input and output shapes,
     * optional relative deprecation filters from settings.
     *
     * <p>Call after {@code runner.model(...)}, {@code runner.service(...)}, and
     * {@code runner.settings(...)} (or {@code runner.settings(Class, Node)}) have been set.
     */
    public static <
                    W extends SymbolWriter<W, ? extends ImportContainer>,
                    I extends SmithyIntegration<BeamSettings, W, C>,
                    C extends CodegenContext<BeamSettings, W, I>>
            void applySharedCodegenTransforms(
                    CodegenDirector<W, I, C, BeamSettings> director,
                    BeamSettings settings) {
        director.performDefaultCodegenTransforms();
        director.createDedicatedInputsAndOutputs();
        String relativeDate = settings.relativeDate();
        if (relativeDate != null && !relativeDate.isBlank()) {
            director.removeShapesDeprecatedBeforeDate(relativeDate.trim());
        }
        String relativeVersion = settings.relativeVersion();
        if (relativeVersion != null && !relativeVersion.isBlank()) {
            director.removeShapesDeprecatedBeforeVersion(relativeVersion.trim());
        }
    }
}
