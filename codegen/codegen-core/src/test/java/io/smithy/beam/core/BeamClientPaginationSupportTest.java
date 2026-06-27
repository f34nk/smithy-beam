package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamClientPaginationSupportTest {

  private static Model loadModel() {
    URL resource =
        BeamClientPaginationSupportTest.class.getResource("/model/paginated_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void detectsPaginatedOperationsAndItemsPath() {
    Model model = loadModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#PaginatedService"), ServiceShape.class);
    OperationShape listWidgets =
        model.expectShape(
            ShapeId.from("smithy.beam.test.paginated#ListWidgets"), OperationShape.class);

    assertThat(BeamClientPaginationSupport.isPaginated(model, service, listWidgets)).isTrue();

    PaginationInfo info =
        BeamClientPaginationSupport.requirePaginationInfo(model, service, listWidgets);
    assertThat(BeamClientPaginationSupport.hasItemsMember(info)).isTrue();
  }
}
