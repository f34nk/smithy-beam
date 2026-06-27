package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamResourceIndexTest {

  @Test
  void identifierChainWalksParents() {
    URL resource = BeamResourceIndexTest.class.getResource("/model/resource_lifecycle.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();

    BeamResourceIndex index = BeamResourceIndex.of(model);
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.resource_lifecycle#ResourceLifecycleService"),
            ServiceShape.class);
    ResourceShape employee =
        index.containedResourcesSorted(service).stream()
            .filter(r -> r.getId().getName().equals("Employee"))
            .findFirst()
            .orElseThrow();

    assertThat(index.identifierChain(employee))
        .containsExactly(
            ShapeId.from("smithy.beam.demo.resource_lifecycle#Organization"),
            ShapeId.from("smithy.beam.demo.resource_lifecycle#Employee"));
  }
}
