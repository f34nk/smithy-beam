package io.smithy.beam.core;

import java.util.LinkedHashSet;
import java.util.Set;
import software.amazon.smithy.codegen.core.CodegenContext;
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
 * Applies the standard Smithy-Build directed-codegen model transforms for smithy-beam, driven by
 * {@link BeamSettings}. Keeps Erlang and Elixir generators aligned on transform order.
 *
 * <p>Transforms run on the assembled {@link Model} before {@link CodegenDirector#run()}. The
 * director receives the pruned service-closure model via {@code runner.model(prunedModel)} and does
 * not register deferred model transforms.
 */
public final class BeamCodegenTransforms {

  private BeamCodegenTransforms() {}

  /**
   * Applies shared model transforms in a fixed order, then sets the director model to the service
   * closure: default service codegen simplification (mixin flatten, service errors copied to
   * operations), dedicated operation input and output shapes, optional relative deprecation filters
   * from settings, removal of shapes outside the service closure.
   *
   * <p>Call after {@code runner.service(...)} and {@code runner.settings(...)} (or {@code
   * runner.settings(Class, Node)}) have been set. Pass the assembled model; this method sets {@code
   * runner.model(prunedModel)}.
   */
  public static <
          W extends SymbolWriter<W, ? extends ImportContainer>,
          I extends SmithyIntegration<BeamSettings, W, C>,
          C extends CodegenContext<BeamSettings, W, I>>
      void applySharedCodegenTransforms(
          CodegenDirector<W, I, C, BeamSettings> director, BeamSettings settings, Model model) {
    ShapeId serviceId = settings.resolveService(model);
    ModelTransformer transformer = ModelTransformer.create();
    Model transformed =
        CodegenDirector.simplifyModelForServiceCodegen(model, serviceId, transformer);
    transformed = transformer.createDedicatedInputAndOutput(transformed, "Input", "Output");
    String relativeDate = settings.relativeDate();
    if (relativeDate != null && !relativeDate.isBlank()) {
      transformed = transformer.filterDeprecatedRelativeDate(transformed, relativeDate.trim());
    }
    String relativeVersion = settings.relativeVersion();
    if (relativeVersion != null && !relativeVersion.isBlank()) {
      transformed =
          transformer.filterDeprecatedRelativeVersion(transformed, relativeVersion.trim());
    }
    ServiceShape service = transformed.expectShape(serviceId, ServiceShape.class);
    director.model(pruneModelToServiceClosure(transformed, service));
  }

  /**
   * Removes shapes that are not in the closure of the given service and sets the result on the
   * director before {@link CodegenDirector#run()}.
   */
  public static void pruneToServiceClosure(
      CodegenDirector<?, ?, ?, BeamSettings> director, Model model, ServiceShape service) {
    director.model(pruneModelToServiceClosure(model, service));
  }

  static Model pruneModelToServiceClosure(Model model, ServiceShape service) {
    Set<ShapeId> keepIds = closureAndTraitDefinitionIds(model, service);
    Model.Builder builder = Model.builder();
    for (ShapeId shapeId : model.getShapeIds()) {
      if (keepIds.contains(shapeId)) {
        builder.addShape(model.expectShape(shapeId));
      }
    }
    return builder.build();
  }

  /**
   * Shape ids to retain: the service closure plus trait definition shapes attached to any shape in
   * that closure (for example protocol traits on the service).
   */
  static Set<ShapeId> closureAndTraitDefinitionIds(Model model, ServiceShape service) {
    Walker walker = new Walker(model);
    Set<ShapeId> keepIds = new LinkedHashSet<>();
    for (Shape shape : walker.walkShapes(service)) {
      keepIds.add(shape.getId());
      shape.getAllTraits().keySet().forEach(keepIds::add);
    }
    keepIds.addAll(BeamWaiterIndex.referencedErrorShapeIds(model, service));
    return keepIds;
  }
}
