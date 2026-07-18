package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.InputTrait;
import software.amazon.smithy.model.traits.OutputTrait;

class BeamCodegenTransformsTest {

  private static final ShapeId SERVICE_ID = ShapeId.from("example.com#MyService");
  private static final ShapeId ORPHAN_ID = ShapeId.from("example.com#Orphan");
  private static final ShapeId PING_ID = ShapeId.from("example.com#Ping");
  private static final ShapeId DEPRECATED_STRING_ID = ShapeId.from("example.com#OldName");

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void applySharedCodegenTransforms_setsPrunedModelWithoutDirectorDeferredTransforms() {
    Model model = modelWithOrphanShape();
    CodegenDirector runner = Mockito.spy(new CodegenDirector<>());
    runner.service(SERVICE_ID);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.service(SERVICE_ID);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, model);

    verify(runner, never()).performDefaultCodegenTransforms();
    verify(runner, never()).createDedicatedInputsAndOutputs();
    ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
    verify(runner).model(modelCaptor.capture());
    Model pruned = modelCaptor.getValue();
    assertThat(pruned.getShape(ORPHAN_ID)).isEmpty();
    assertThat(pruned.getShape(SERVICE_ID)).isPresent();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void applySharedCodegenTransforms_createsDedicatedInputsAndOutputsBeforePrune() {
    Model model = modelWithOrphanShape();
    CodegenDirector runner = Mockito.spy(new CodegenDirector<>());
    runner.service(SERVICE_ID);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.service(SERVICE_ID);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, model);

    ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
    verify(runner).model(modelCaptor.capture());
    Model transformed = modelCaptor.getValue();
    OperationShape ping = transformed.expectShape(PING_ID, OperationShape.class);
    assertThat(transformed.expectShape(ping.getInputShape()).hasTrait(InputTrait.class)).isTrue();
    assertThat(transformed.expectShape(ping.getOutputShape()).hasTrait(OutputTrait.class)).isTrue();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void applySharedCodegenTransforms_appliesRelativeDeprecationFilters() {
    Model model = modelWithDeprecatedStringInClosure();
    CodegenDirector runner = Mockito.spy(new CodegenDirector<>());
    runner.service(SERVICE_ID);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.service(SERVICE_ID);
    settings.relativeDate("2026-06-01");

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, model);

    ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
    verify(runner).model(modelCaptor.capture());
    assertThat(modelCaptor.getValue().getShape(DEPRECATED_STRING_ID)).isEmpty();
    assertThat(modelCaptor.getValue().getShape(SERVICE_ID)).isPresent();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void applySharedCodegenTransforms_skipsDeprecationFilters_whenRelativeFieldsBlankOrWhitespace() {
    Model model = modelWithDeprecatedStringInClosure();
    CodegenDirector runner = Mockito.spy(new CodegenDirector<>());
    runner.service(SERVICE_ID);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.service(SERVICE_ID);
    settings.relativeDate("   ");
    settings.relativeVersion("\t");

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, model);

    ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
    verify(runner).model(modelCaptor.capture());
    assertThat(modelCaptor.getValue().getShape(DEPRECATED_STRING_ID)).isPresent();
  }

  @Test
  void pruneModelToServiceClosure_retainsProtocolTraitDefinitions() {
    URL resource =
        BeamCodegenTransformsTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    ShapeId serviceId = ShapeId.from("smithy.beam.demo.protocoljson#DemoRestJson");
    ServiceShape service = model.expectShape(serviceId, ServiceShape.class);

    Model pruned = BeamCodegenTransforms.pruneModelToServiceClosure(model, service);

    assertThat(BeamProtocolResolver.resolveServiceProtocol(pruned, service)).isPresent();
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
  void pruneModelToServiceClosure_doesNotThrow_onLargeAwsLikeClosure() {
    Model model = modelWithEnumInServiceClosure();
    ServiceShape service = model.expectShape(SERVICE_ID, ServiceShape.class);
    ShapeId enumId = ShapeId.from("example.com#OrderStatus");

    assertThatCode(() -> BeamCodegenTransforms.pruneModelToServiceClosure(model, service))
        .doesNotThrowAnyException();

    Model pruned = BeamCodegenTransforms.pruneModelToServiceClosure(model, service);
    assertThat(pruned.getShape(SERVICE_ID)).isPresent();
    assertThat(pruned.getShape(enumId)).isPresent();
    EnumShape status = pruned.expectShape(enumId, EnumShape.class);
    assertThat(status.getEnumValues()).isNotEmpty();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void applySharedCodegenTransforms_prunesUnreachableShapesBeforeRun() {
    Model model = modelWithOrphanShape();
    CodegenDirector runner = Mockito.spy(new CodegenDirector<>());
    runner.service(SERVICE_ID);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.service(SERVICE_ID);

    assertThatCode(() -> BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, model))
        .doesNotThrowAnyException();

    ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
    verify(runner).model(modelCaptor.capture());
    Model pruned = modelCaptor.getValue();
    assertThat(pruned.getShape(ORPHAN_ID)).isEmpty();
    assertThat(pruned.getShape(SERVICE_ID)).isPresent();
  }

  @Test
  void pruneModelToServiceClosure_retainsWaiterReferencedErrorShapes() {
    URL resource =
        BeamCodegenTransformsTest.class.getResource("/model/waiter_external_error_fixture.smithy");
    assertThat(resource).isNotNull();
    Model model =
        Model.assembler()
            .addImport(resource)
            .addImport(
                BeamCodegenTransformsTest.class.getResource(
                    "/model/waiter_external_error_external.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    ShapeId serviceId = ShapeId.from("smithy.beam.test.waiter_errors#WaiterExternalErrorService");
    ShapeId errorId = ShapeId.from("smithy.beam.test.waiter_errors.external#ExternalNotFound");
    ServiceShape service = model.expectShape(serviceId, ServiceShape.class);

    Model pruned = BeamCodegenTransforms.pruneModelToServiceClosure(model, service);

    assertThat(pruned.getShape(errorId)).isPresent();
    assertThat(
            BeamWaiterIndex.referencedErrorShapeIds(
                pruned, pruned.expectShape(serviceId, ServiceShape.class)))
        .contains(errorId);
  }

  private static Model modelWithEnumInServiceClosure() {
    ShapeId enumId = ShapeId.from("example.com#OrderStatus");
    ShapeId orphanEnumId = ShapeId.from("example.com#OrphanStatus");
    ShapeId bundleId = ShapeId.from("example.com#OrderBundle");
    ShapeId opId = ShapeId.from("example.com#GetOrder");
    ServiceShape service =
        ServiceShape.builder().id(SERVICE_ID).version("1").addOperation(opId).build();
    OperationShape operation = OperationShape.builder().id(opId).output(bundleId).build();
    EnumShape status =
        EnumShape.builder()
            .id(enumId)
            .addMember("ACTIVE", "ACTIVE")
            .addMember("INACTIVE", "INACTIVE")
            .build();
    EnumShape orphanStatus = EnumShape.builder().id(orphanEnumId).addMember("A", "A").build();
    StructureShape bundle =
        StructureShape.builder().id(bundleId).addMember("status", enumId).build();
    return Model.assembler()
        .addShape(service)
        .addShape(operation)
        .addShape(status)
        .addShape(orphanStatus)
        .addShape(bundle)
        .assemble()
        .unwrap();
  }

  private static Model modelWithOrphanShape() {
    ServiceShape service =
        ServiceShape.builder().id(SERVICE_ID).version("1").addOperation(PING_ID).build();
    OperationShape ping = OperationShape.builder().id(PING_ID).build();
    StructureShape orphan = StructureShape.builder().id(ORPHAN_ID).build();
    return Model.assembler().addShape(service).addShape(ping).addShape(orphan).assemble().unwrap();
  }

  private static Model modelWithDeprecatedStringInClosure() {
    ShapeId bundleId = ShapeId.from("example.com#NameBundle");
    ServiceShape service =
        ServiceShape.builder().id(SERVICE_ID).version("1").addOperation(PING_ID).build();
    OperationShape ping = OperationShape.builder().id(PING_ID).output(bundleId).build();
    software.amazon.smithy.model.shapes.StringShape oldName =
        software.amazon.smithy.model.shapes.StringShape.builder()
            .id(DEPRECATED_STRING_ID)
            .addTrait(DeprecatedTrait.builder().since("2020-01-01").build())
            .build();
    StructureShape bundle =
        StructureShape.builder().id(bundleId).addMember("name", DEPRECATED_STRING_ID).build();
    return Model.assembler()
        .addShape(service)
        .addShape(ping)
        .addShape(oldName)
        .addShape(bundle)
        .assemble()
        .unwrap();
  }
}
