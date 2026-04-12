package io.smithy.beam.core.model;

import io.smithy.beam.core.ir.ModuleTypeSpec;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSpecBuilderTest {

    @Test
    void buildsModuleTypeSpecFromReachableShapes() {
        String smithy =
                """
                $version: "2"
                namespace test.example

                service S {
                    version: "1"
                    operations: [Op]
                }

                operation Op {
                    input: In
                    output: Out
                }

                structure In {
                    foo: String
                }

                structure Out {
                    bar: Integer
                }
                """;

        Model model =
                Model.assembler().addUnparsedModel("m.smithy", smithy).disableValidation().assemble().unwrap();

        ServiceShape service = model.expectShape(ShapeId.from("test.example#S"), ServiceShape.class);
        Set<Shape> reachable = ShapeIndex.reachable(service, model);
        ModuleTypeSpec spec = TypeSpecBuilder.build(service, model, reachable);

        assertThat(spec.serviceName()).isEqualTo("S");
        assertThat(spec.structures())
                .hasSize(2)
                .extracting(s -> s.name())
                .containsExactlyInAnyOrder("In", "Out");
        assertThat(spec.enums()).isEmpty();
        assertThat(spec.unions()).isEmpty();
        assertThat(spec.errors()).isEmpty();
        assertThat(spec.hasServiceErrors()).isFalse();
    }
}
