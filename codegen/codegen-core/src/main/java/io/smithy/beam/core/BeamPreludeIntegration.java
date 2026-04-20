package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Base integration that every smithy-beam language integration should extend.
 *
 * <p>Applies the three standard model pre-processing transforms in
 * {@link #preprocessModel} so language-specific integrations inherit
 * them without duplicating the wiring.
 *
 * @param <S> the concrete settings type
 * @param <W> the concrete writer type
 * @param <C> the concrete codegen-context type
 */
public abstract class BeamPreludeIntegration<
        S extends BeamSettings,
        W extends SymbolWriter<W, ?>,
        C extends CodegenContext<S, W, ?>>
    implements SmithyIntegration<S, W, C> {

    @Override
    public Model preprocessModel(Model model, S settings) {
        ServiceShape svc = model.expectShape(settings.getService(), ServiceShape.class);
        model = BeamModelTransforms.removeOutOfClosure(model, svc);
        model = BeamModelTransforms.copyServiceErrorsToOperations(model, svc);
        model = BeamModelTransforms.flattenMixins(model);
        if (settings.isRemoveUnreferencedShapes()) {
            model = BeamModelTransforms.removeUnreferencedShapes(model, svc);
        }
        if (settings.isApplyAuthSchemes()) {
            model = BeamModelTransforms.applyAuthSchemes(model, svc);
        }
        return model;
    }
}
