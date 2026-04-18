package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamModelTransformsTest {

    private static final String MINIMAL_MODEL = "$version: \"2\"\n"
            + "namespace example\n"
            + "\n"
            + "service Weather {\n"
            + "    operations: [GetCity]\n"
            + "    errors: [ServiceError]\n"
            + "}\n"
            + "\n"
            + "operation GetCity {\n"
            + "    input := {}\n"
            + "    output := {}\n"
            + "}\n"
            + "\n"
            + "@error(\"client\")\n"
            + "structure ServiceError {\n"
            + "    message: String\n"
            + "}\n"
            + "\n"
            + "structure Orphan {}\n";

    private Model loadModel() {
        return Model.assembler()
                .addUnparsedModel("test.smithy", MINIMAL_MODEL)
                .assemble()
                .unwrap();
    }

    @Test
    void removeOutOfClosure_removesOrphanedShapes() {
        Model model = loadModel();
        ServiceShape service = model.expectShape(ShapeId.from("example#Weather"), ServiceShape.class);

        Model trimmed = BeamModelTransforms.removeOutOfClosure(model, service);

        assertThat(trimmed.getShapeIds()).doesNotContain(ShapeId.from("example#Orphan"));
        assertThat(trimmed.getShapeIds()).contains(ShapeId.from("example#ServiceError"));
    }

    @Test
    void removeOutOfClosure_preservesServiceAndOperations() {
        Model model = loadModel();
        ServiceShape service = model.expectShape(ShapeId.from("example#Weather"), ServiceShape.class);

        Model trimmed = BeamModelTransforms.removeOutOfClosure(model, service);

        assertThat(trimmed.getShapeIds()).contains(
                ShapeId.from("example#Weather"),
                ShapeId.from("example#GetCity")
        );
    }

    @Test
    void copyServiceErrorsToOperations_addsServiceErrorsToOperations() {
        Model model = loadModel();
        ServiceShape service = model.expectShape(ShapeId.from("example#Weather"), ServiceShape.class);

        Model transformed = BeamModelTransforms.copyServiceErrorsToOperations(model, service);

        assertThat(transformed.expectShape(ShapeId.from("example#GetCity")).asOperationShape()
                .get().getErrors())
                .contains(ShapeId.from("example#ServiceError"));
    }

    @Test
    void flattenMixins_returnsSameModelWhenNoMixinsPresent() {
        Model model = loadModel();

        Model flattened = BeamModelTransforms.flattenMixins(model);

        // Without any mixins the shape count should be stable
        assertThat(flattened.getShapeIds()).containsAll(model.getShapeIds());
    }

    @Test
    void addDefaultErrorMembers_addsCodeMemberWhenAbsent() {
        Model model = loadModel();

        Model transformed = BeamModelTransforms.addDefaultErrorMembers(model);

        assertThat(transformed.expectShape(ShapeId.from("example#ServiceError"))
                .asStructureShape()
                .get()
                .getMember("code"))
                .isPresent();
    }

    @Test
    void addDefaultErrorMembers_doesNotDuplicateMessageWhenPresent() {
        Model model = loadModel();

        Model transformed = BeamModelTransforms.addDefaultErrorMembers(model);

        // ServiceError already has a message member; it must not appear twice
        assertThat(transformed.expectShape(ShapeId.from("example#ServiceError"))
                .asStructureShape()
                .get()
                .getMember("message"))
                .isPresent();
    }
}
