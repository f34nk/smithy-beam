package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BeamCodegenTransformsTest {

    private static final ShapeId SERVICE_ID = ShapeId.from("example.com#MyService");
    private static final ShapeId ORPHAN_ID = ShapeId.from("example.com#Orphan");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void applySharedCodegenTransforms_invokesDirectorStepsIncludingRelativeFilters() {
        CodegenDirector runner = runnerWithService();
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        settings.service(SERVICE_ID);
        settings.relativeDate(" 2026-06-01 ");
        settings.relativeVersion(" 1.2.3 ");

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        InOrder order = inOrder(runner);
        order.verify(runner).performDefaultCodegenTransforms();
        order.verify(runner).createDedicatedInputsAndOutputs();
        order.verify(runner).removeShapesDeprecatedBeforeDate("2026-06-01");
        order.verify(runner).removeShapesDeprecatedBeforeVersion("1.2.3");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void applySharedCodegenTransforms_skipsDeprecationFilters_whenRelativeFieldsBlankOrWhitespace() {
        CodegenDirector runner = runnerWithService();
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        settings.service(SERVICE_ID);
        settings.relativeDate("   ");
        settings.relativeVersion("\t");

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        verify(runner).performDefaultCodegenTransforms();
        verify(runner).createDedicatedInputsAndOutputs();
        verify(runner, never()).removeShapesDeprecatedBeforeDate(anyString());
        verify(runner, never()).removeShapesDeprecatedBeforeVersion(anyString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void applySharedCodegenTransforms_skipsDeprecationFilters_whenRelativeFieldsUnset() {
        CodegenDirector runner = runnerWithService();
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        settings.service(SERVICE_ID);

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        verify(runner).performDefaultCodegenTransforms();
        verify(runner).createDedicatedInputsAndOutputs();
        verify(runner, never()).removeShapesDeprecatedBeforeDate(anyString());
        verify(runner, never()).removeShapesDeprecatedBeforeVersion(anyString());
    }

    @Test
    void pruneModelToServiceClosure_removesShapesOutsideServiceClosure() {
        Model model = modelWithOrphanShape();
        ServiceShape service = model.expectShape(SERVICE_ID, ServiceShape.class);

        Model pruned = BeamCodegenTransforms.pruneModelToServiceClosure(model, service);

        assertThat(pruned.getShape(ORPHAN_ID)).isEmpty();
        assertThat(pruned.getShape(SERVICE_ID)).isPresent();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void applySharedCodegenTransforms_prunesShapesOutsideServiceClosure() {
        Model model = modelWithOrphanShape();
        CodegenDirector runner = new CodegenDirector<>();
        runner.model(model);
        runner.service(SERVICE_ID);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        settings.service(SERVICE_ID);

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        Model transformed = BeamCodegenTransforms.applyDirectorTransforms(runner);
        assertThat(transformed.getShape(ORPHAN_ID)).isEmpty();
        assertThat(transformed.getShape(SERVICE_ID)).isPresent();
    }

    @SuppressWarnings("rawtypes")
    private static CodegenDirector runnerWithService() {
        CodegenDirector runner = Mockito.spy(new CodegenDirector<>());
        runner.model(modelWithOrphanShape());
        runner.service(SERVICE_ID);
        return runner;
    }

    private static Model modelWithOrphanShape() {
        ServiceShape service = ServiceShape.builder()
                .id(SERVICE_ID)
                .version("1")
                .addOperation(ShapeId.from("example.com#Ping"))
                .build();
        OperationShape ping = OperationShape.builder()
                .id(ShapeId.from("example.com#Ping"))
                .build();
        StructureShape orphan = StructureShape.builder().id(ORPHAN_ID).build();
        return Model.assembler()
                .addShape(service)
                .addShape(ping)
                .addShape(orphan)
                .assemble()
                .unwrap();
    }
}
