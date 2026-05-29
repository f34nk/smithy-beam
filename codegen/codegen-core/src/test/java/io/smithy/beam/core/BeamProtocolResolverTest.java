package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DynamicTrait;
import software.amazon.smithy.model.traits.ProtocolDefinitionTrait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeamProtocolResolverTest {

    private static StructureShape protocolTraitDefinition(ShapeId traitId) {
        return StructureShape.builder()
                .id(traitId)
                .addTrait(
                        ProtocolDefinitionTrait.builder()
                                .addTrait(ShapeId.from("smithy.api#http"))
                                .build())
                .build();
    }

    private static Model modelWith(ServiceShape service, StructureShape... protocolDefinitions) {
        Model.Builder builder = Model.builder().addShape(service);
        for (StructureShape def : protocolDefinitions) {
            builder.addShape(def);
        }
        return builder.build();
    }

    @Test
    void resolve_returnsExplicitProtocol_whenSetAndAttachedToService() {
        ShapeId protoId = ShapeId.from("com.example#ProtoOne");
        ServiceShape service =
                ServiceShape.builder()
                        .id(ShapeId.from("com.example#Svc"))
                        .version("1")
                        .addTrait(new DynamicTrait(protoId, Node.objectNode()))
                        .build();
        Model model = modelWith(service, protocolTraitDefinition(protoId));

        BeamSettings settings = new BeamSettings();
        settings.protocol(protoId);

        assertThat(BeamProtocolResolver.resolve(model, service, settings)).isEqualTo(protoId);
    }

    @Test
    void resolve_returnsSingleAttachedProtocol_whenExplicitProtocolUnset() {
        ShapeId protoId = ShapeId.from("com.example#ProtoOne");
        ServiceShape service =
                ServiceShape.builder()
                        .id(ShapeId.from("com.example#Svc"))
                        .version("1")
                        .addTrait(new DynamicTrait(protoId, Node.objectNode()))
                        .build();
        Model model = modelWith(service, protocolTraitDefinition(protoId));

        assertThat(BeamProtocolResolver.resolve(model, service, new BeamSettings())).isEqualTo(protoId);
    }

    @Test
    void resolve_throws_whenExplicitProtocolNotOnService() {
        ShapeId attached = ShapeId.from("com.example#ProtoOne");
        ShapeId requested = ShapeId.from("com.example#ProtoOther");
        ServiceShape service =
                ServiceShape.builder()
                        .id(ShapeId.from("com.example#Svc"))
                        .version("1")
                        .addTrait(new DynamicTrait(attached, Node.objectNode()))
                        .build();
        Model model = modelWith(service, protocolTraitDefinition(attached));

        BeamSettings settings = new BeamSettings();
        settings.protocol(requested);

        assertThatThrownBy(() -> BeamProtocolResolver.resolve(model, service, settings))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("not attached");
    }

    @Test
    void resolve_throws_whenNoProtocolTraitsAndExplicitUnset() {
        ServiceShape service =
                ServiceShape.builder()
                        .id(ShapeId.from("com.example#Svc"))
                        .version("1")
                        .build();
        Model model = Model.builder().addShape(service).build();

        assertThatThrownBy(() -> BeamProtocolResolver.resolve(model, service, new BeamSettings()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("No protocol trait found");
    }

    @Test
    void resolve_throws_whenMultipleProtocolTraitsAndExplicitUnset() {
        ShapeId first = ShapeId.from("com.example#ProtoOne");
        ShapeId second = ShapeId.from("com.example#ProtoTwo");
        ServiceShape service =
                ServiceShape.builder()
                        .id(ShapeId.from("com.example#Svc"))
                        .version("1")
                        .addTrait(new DynamicTrait(first, Node.objectNode()))
                        .addTrait(new DynamicTrait(second, Node.objectNode()))
                        .build();
        Model model =
                modelWith(service, protocolTraitDefinition(first), protocolTraitDefinition(second));

        assertThatThrownBy(() -> BeamProtocolResolver.resolve(model, service, new BeamSettings()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("Multiple protocol traits found");
    }

    @Test
    void closureAuditCollectsAllUnsupportedShapesInOneException() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace test

                        use smithy.api#default
                        use smithy.api#streaming

                        service Svc {
                            version: "2026"
                            operations: [Op]
                        }

                        @readonly
                        operation Op {
                            output: OpOutput
                        }

                        structure OpOutput {
                            @default("")
                            data: StreamBlob
                        }

                        @streaming
                        blob StreamBlob
                        """)
                .assemble()
                .unwrap();

        ServiceShape service = model.getServiceShapes().iterator().next();
        ShapeId protocol = ShapeId.from("aws.protocols#restJson1");

        CodegenException ex = assertThrows(CodegenException.class, () ->
                BeamProtocolResolver.assertClosureSupported(model, service, protocol));

        assertThat(ex.getMessage()).contains("streaming blob");
        assertThat(ex.getMessage()).contains("StreamBlob");
    }
}
