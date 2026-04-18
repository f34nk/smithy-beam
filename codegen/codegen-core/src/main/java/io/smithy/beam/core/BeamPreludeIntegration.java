package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Abstract integration that applies the three shared model pre-processing
 * transforms to every smithy-beam language backend.
 *
 * <p>Language-core modules extend this class to inherit the shared transforms
 * without coupling to the concrete writer type.
 *
 * @param <S> the settings type
 * @param <W> the writer type
 * @param <C> the codegen context type
 */
public abstract class BeamPreludeIntegration<
        S extends BeamSettings,
        W extends SymbolWriter<W, ?>,
        C extends CodegenContext<S, W, ?>>
        implements SmithyIntegration<S, W, C> {

    @Override
    public Model preprocessModel(Model model, S settings) {
        ServiceShape service = model.expectShape(settings.getService(), ServiceShape.class);
        model = BeamModelTransforms.removeOutOfClosure(model, service);
        model = BeamModelTransforms.copyServiceErrorsToOperations(model, service);
        model = BeamModelTransforms.flattenMixins(model);
        return model;
    }
}
