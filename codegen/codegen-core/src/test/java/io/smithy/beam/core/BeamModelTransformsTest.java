package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamModelTransformsTest {

    private static Model model;
    private static ServiceShape service;

    @BeforeAll
    static void buildModel() {
        model = Model.assembler()
            .addUnparsedModel("test.smithy", String.join("\n",
                "$version: \"2\"",
                "namespace com.example",
                "",
                "service TestService {",
                "    version: \"2024-01-01\"",
                "    operations: [GetFoo]",
                "    errors: [ServiceFault]",
                "}",
                "",
                "operation GetFoo {",
                "    input: GetFooInput",
                "    output: GetFooOutput",
                "}",
                "",
                "structure GetFooInput {}",
                "structure GetFooOutput {}",
                "",
                "@error(\"server\")",
                "structure ServiceFault {",
                "    message: String",
                "}"
            ))
            .assemble()
            .unwrap();
        service = model.expectShape(ShapeId.from("com.example#TestService"), ServiceShape.class);
    }

    @Test
    void removeOutOfClosureReturnsNonNullModel() {
        Model result = BeamModelTransforms.removeOutOfClosure(model, service);
        assertThat(result).isNotNull();
    }

    @Test
    void removeOutOfClosureRetainsServiceShape() {
        Model result = BeamModelTransforms.removeOutOfClosure(model, service);
        assertThat(result.getShape(ShapeId.from("com.example#TestService"))).isPresent();
    }

    @Test
    void removeOutOfClosureRetainsOperationShapes() {
        Model result = BeamModelTransforms.removeOutOfClosure(model, service);
        assertThat(result.getShape(ShapeId.from("com.example#GetFoo"))).isPresent();
    }

    @Test
    void copyServiceErrorsToOperationsReturnsNonNullModel() {
        Model result = BeamModelTransforms.copyServiceErrorsToOperations(model, service);
        assertThat(result).isNotNull();
    }

    @Test
    void copyServiceErrorsToOperationsPropagatesServiceErrors() {
        Model result = BeamModelTransforms.copyServiceErrorsToOperations(model, service);
        ShapeId opId = ShapeId.from("com.example#GetFoo");
        assertThat(result.expectShape(opId).asOperationShape())
            .isPresent()
            .hasValueSatisfying(op ->
                assertThat(op.getErrors()).contains(ShapeId.from("com.example#ServiceFault")));
    }

    @Test
    void flattenMixinsReturnsNonNullModel() {
        Model result = BeamModelTransforms.flattenMixins(model);
        assertThat(result).isNotNull();
    }

    @Test
    void flattenMixinsPreservesNonMixinShapes() {
        Model result = BeamModelTransforms.flattenMixins(model);
        assertThat(result.getShape(ShapeId.from("com.example#GetFooInput"))).isPresent();
    }

    @Test
    void addDefaultErrorMembersReturnsNonNullModel() {
        Model result = BeamModelTransforms.addDefaultErrorMembers(model);
        assertThat(result).isNotNull();
    }

    @Test
    void addDefaultErrorMembersPreservesShapeCount() {
        Model result = BeamModelTransforms.addDefaultErrorMembers(model);
        assertThat(result.toSet()).hasSize(model.toSet().size());
    }
}
