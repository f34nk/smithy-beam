package io.smithy.beam.core;

import java.lang.reflect.Field;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Applies the standard Smithy-Build directed-codegen model transforms for smithy-beam,
 * driven by {@link BeamSettings}. Keeps Erlang and Elixir generators aligned on
 * transform order.
 *
 * <p>Service closure pruning registers a deferred model transform on {@link CodegenDirector}.
 * Smithy 1.54.0 does not expose a public API for that registration, so this class reads
 * the private {@code transforms} and {@code model} fields via reflection. Field names and
 * layout can change across Smithy releases; {@code BeamCodegenTransformsTest} pins the
 * expected names for the pinned Smithy version.
 *
 * <p>When Smithy exposes public transform registration (for example
 * {@code director.registerTransform(...)}), replace the reflection helpers below rather
 * than extending them.
 */
public final class BeamCodegenTransforms {

    /** Private {@link CodegenDirector} field name on Smithy 1.54.0. */
    static final String CODEGEN_DIRECTOR_TRANSFORMS_FIELD = "transforms";

    /** Private {@link CodegenDirector} field name on Smithy 1.54.0. */
    static final String CODEGEN_DIRECTOR_MODEL_FIELD = "model";

    private static final Field TRANSFORMS_FIELD;
    private static final Field MODEL_FIELD;

    static {
        try {
            TRANSFORMS_FIELD =
                    CodegenDirector.class.getDeclaredField(CODEGEN_DIRECTOR_TRANSFORMS_FIELD);
            TRANSFORMS_FIELD.setAccessible(true);
            MODEL_FIELD = CodegenDirector.class.getDeclaredField(CODEGEN_DIRECTOR_MODEL_FIELD);
            MODEL_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private BeamCodegenTransforms() {}

    /**
     * Registers transforms on the given director in a fixed order:
     * default service codegen simplification (mixin flatten, service errors copied to operations),
     * dedicated operation input and output shapes,
     * optional relative deprecation filters from settings,
     * removal of shapes outside the service closure.
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
        Model model = getDirectorModel(director);
        ServiceShape service =
                model.expectShape(settings.resolveService(model), ServiceShape.class);
        pruneToServiceClosure(director, service);
    }

    /**
     * Removes shapes that are not in the closure of the given service from the director model
     * during {@link CodegenDirector#run()}.
     *
     * <p>Uses reflection to append to {@link CodegenDirector}'s private transform list because
     * no public registration hook exists on Smithy 1.54.0.
     */
    public static void pruneToServiceClosure(
            CodegenDirector<?, ?, ?, BeamSettings> director, ServiceShape service) {
        ShapeId serviceId = service.getId();
        addDirectorTransform(
                director,
                (model, transformer) ->
                        pruneModelToServiceClosure(
                                model, model.expectShape(serviceId, ServiceShape.class)));
    }

    static Model pruneModelToServiceClosure(Model model, ServiceShape service) {
        Set<ShapeId> keepIds = closureAndTraitDefinitionIds(model, service);
        ModelTransformer transformer = ModelTransformer.create();
        return transformer.removeShapesIf(model, shape -> !keepIds.contains(shape.getId()));
    }

    /**
     * Shape ids to retain: the service closure plus trait definition shapes attached to
     * any shape in that closure (for example protocol traits on the service).
     */
    static Set<ShapeId> closureAndTraitDefinitionIds(Model model, ServiceShape service) {
        Walker walker = new Walker(model);
        Set<ShapeId> keepIds = new LinkedHashSet<>();
        for (Shape shape : walker.walkShapes(service)) {
            keepIds.add(shape.getId());
            shape.getAllTraits().keySet().forEach(keepIds::add);
        }
        return keepIds;
    }

    /** Appends a deferred transform via reflection; see class Javadoc for upgrade path. */
    @SuppressWarnings("unchecked")
    private static void addDirectorTransform(
            CodegenDirector<?, ?, ?, BeamSettings> director,
            BiFunction<Model, ModelTransformer, Model> transform) {
        try {
            List<BiFunction<Model, ModelTransformer, Model>> transforms =
                    (List<BiFunction<Model, ModelTransformer, Model>>) TRANSFORMS_FIELD.get(director);
            transforms.add(transform);
        } catch (IllegalAccessException e) {
            throw new CodegenException("Failed to register codegen model transform", e);
        }
    }

    /** Reads the director working model via reflection; see class Javadoc for upgrade path. */
    private static Model getDirectorModel(CodegenDirector<?, ?, ?, BeamSettings> director) {
        try {
            return (Model) MODEL_FIELD.get(director);
        } catch (IllegalAccessException e) {
            throw new CodegenException("Failed to read codegen model", e);
        }
    }

    @SuppressWarnings("unchecked")
    static Model applyDirectorTransforms(CodegenDirector<?, ?, ?, BeamSettings> director) {
        Model model = getDirectorModel(director);
        ModelTransformer transformer = ModelTransformer.create();
        try {
            List<BiFunction<Model, ModelTransformer, Model>> transforms =
                    (List<BiFunction<Model, ModelTransformer, Model>>) TRANSFORMS_FIELD.get(director);
            for (BiFunction<Model, ModelTransformer, Model> transform : transforms) {
                model = transform.apply(model, transformer);
            }
            return model;
        } catch (IllegalAccessException e) {
            throw new CodegenException("Failed to apply codegen model transforms", e);
        }
    }
}
