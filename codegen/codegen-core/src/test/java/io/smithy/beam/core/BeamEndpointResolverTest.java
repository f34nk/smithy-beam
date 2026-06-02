package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BeamEndpointResolverTest {

    @Test
    void buildsRegionalUrlFromServiceMetadata() {
        URL resource = BeamEndpointResolverTest.class.getResource(
                "/model/aws_service_metadata_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.test#MetadataService"), ServiceShape.class);
        Optional<String> url = BeamEndpointResolver.defaultRegionalBaseUrl(service, "eu-west-1");
        assertThat(url).hasValueSatisfying(value -> assertThat(value)
                .contains("https://")
                .contains("eu-west-1.amazonaws.com"));
    }
}
