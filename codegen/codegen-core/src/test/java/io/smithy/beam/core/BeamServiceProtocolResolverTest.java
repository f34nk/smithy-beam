package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DynamicTrait;
import software.amazon.smithy.model.traits.ProtocolDefinitionTrait;

class BeamServiceProtocolResolverTest {

  private static StructureShape protocolTraitDefinition(ShapeId traitId) {
    return StructureShape.builder()
        .id(traitId)
        .addTrait(
            ProtocolDefinitionTrait.builder().addTrait(ShapeId.from("smithy.api#http")).build())
        .build();
  }

  private static Model loadMultiService() {
    URL resource = BeamServiceProtocolResolverTest.class.getResource("/model/multi_service.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void resolveServiceProtocol_returnsRestJson1_whenSingleTrait() {
    ShapeId restJson1 = ShapeId.from("aws.protocols#restJson1");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Svc"))
            .version("1")
            .addTrait(new DynamicTrait(restJson1, Node.objectNode()))
            .build();
    Model model =
        Model.builder().addShape(service).addShape(protocolTraitDefinition(restJson1)).build();

    Optional<ShapeId> resolved = BeamProtocolResolver.resolveServiceProtocol(model, service);

    assertThat(resolved).contains(restJson1);
  }

  @Test
  void resolveServiceProtocol_returnsEmpty_whenNoProtocolTraits() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                        $version: "2"
                        namespace test

                        service Test {
                            version: "1"
                        }
                        """)
            .assemble()
            .unwrap();

    ServiceShape service = model.getServiceShapes().iterator().next();

    assertThat(BeamProtocolResolver.resolveServiceProtocol(model, service)).isEmpty();
  }

  @Test
  void resolveServiceProtocol_throws_whenMultipleProtocolTraits() {
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
        Model.builder()
            .addShape(service)
            .addShape(protocolTraitDefinition(first))
            .addShape(protocolTraitDefinition(second))
            .build();

    assertThatThrownBy(() -> BeamProtocolResolver.resolveServiceProtocol(model, service))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("declares multiple protocol traits");
  }

  @Test
  void resolveServiceProtocol_returnsCustomProtocol_whenSingleProtocolDefinition() {
    Model model = loadMultiService();
    ServiceShape serviceA =
        model.expectShape(ShapeId.from("smithy.beam.demo.multi#ServiceA"), ServiceShape.class);

    Optional<ShapeId> resolved = BeamProtocolResolver.resolveServiceProtocol(model, serviceA);

    assertThat(resolved).contains(ShapeId.from("smithy.beam.demo.multi#TestProtocol"));
  }

  @Test
  void resolve_returnsExplicitProtocol_whenNoModelTrait() {
    ShapeId explicit = ShapeId.from("smithy.beam.test#ExplicitOnly");
    ServiceShape service =
        ServiceShape.builder().id(ShapeId.from("com.example#Svc")).version("1").build();
    Model model = Model.builder().addShape(service).build();
    BeamSettings settings = new BeamSettings();
    settings.protocol(explicit);

    assertThat(BeamProtocolResolver.resolve(model, service, settings)).contains(explicit);
  }

  @Test
  void resolve_returnsModelTrait_whenNoExplicitSetting() {
    ShapeId restJson1 = ShapeId.from("aws.protocols#restJson1");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Svc"))
            .version("1")
            .addTrait(new DynamicTrait(restJson1, Node.objectNode()))
            .build();
    Model model =
        Model.builder().addShape(service).addShape(protocolTraitDefinition(restJson1)).build();
    BeamSettings settings = new BeamSettings();

    assertThat(BeamProtocolResolver.resolve(model, service, settings)).contains(restJson1);
  }

  @Test
  void resolve_returnsExplicit_whenExplicitMatchesSoleModelTrait() {
    ShapeId restJson1 = ShapeId.from("aws.protocols#restJson1");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Svc"))
            .version("1")
            .addTrait(new DynamicTrait(restJson1, Node.objectNode()))
            .build();
    Model model =
        Model.builder().addShape(service).addShape(protocolTraitDefinition(restJson1)).build();
    BeamSettings settings = new BeamSettings();
    settings.protocol(restJson1);

    assertThat(BeamProtocolResolver.resolve(model, service, settings)).contains(restJson1);
  }

  @Test
  void resolve_returnsExplicit_whenExplicitDiffersFromSoleModelTrait() {
    ShapeId modelTrait = ShapeId.from("com.example#ModelProto");
    ShapeId explicit = ShapeId.from("com.example#BuildProto");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Svc"))
            .version("1")
            .addTrait(new DynamicTrait(modelTrait, Node.objectNode()))
            .build();
    Model model =
        Model.builder().addShape(service).addShape(protocolTraitDefinition(modelTrait)).build();
    BeamSettings settings = new BeamSettings();
    settings.protocol(explicit);

    assertThat(BeamProtocolResolver.resolve(model, service, settings)).contains(explicit);
  }

  @Test
  void resolve_returnsExplicit_whenServiceHasMultipleModelTraits() {
    ShapeId first = ShapeId.from("com.example#ProtoOne");
    ShapeId second = ShapeId.from("com.example#ProtoTwo");
    ShapeId explicit = ShapeId.from("aws.protocols#restJson1");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Svc"))
            .version("1")
            .addTrait(new DynamicTrait(first, Node.objectNode()))
            .addTrait(new DynamicTrait(second, Node.objectNode()))
            .build();
    Model model =
        Model.builder()
            .addShape(service)
            .addShape(protocolTraitDefinition(first))
            .addShape(protocolTraitDefinition(second))
            .build();
    BeamSettings settings = new BeamSettings();
    settings.protocol(explicit);

    assertThat(BeamProtocolResolver.resolve(model, service, settings)).contains(explicit);
  }

  @Test
  void resolve_throws_whenNoExplicitSettingAndMultipleModelTraits() {
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
        Model.builder()
            .addShape(service)
            .addShape(protocolTraitDefinition(first))
            .addShape(protocolTraitDefinition(second))
            .build();
    BeamSettings settings = new BeamSettings();

    assertThatThrownBy(() -> BeamProtocolResolver.resolve(model, service, settings))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("declares multiple protocol traits");
  }
}
