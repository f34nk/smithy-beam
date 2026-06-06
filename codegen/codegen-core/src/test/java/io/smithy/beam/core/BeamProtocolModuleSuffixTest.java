package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BeamProtocolModuleSuffixTest {

    @Test
    void unknownProtocolFallsBackToSnakeCaseTraitName() {
        ShapeId unknown = ShapeId.from("com.example#MyCustomProto");

        assertThat(BeamProtocolModuleSuffix.codecSuffix(unknown)).isEqualTo("my_custom_proto");
    }

    @Test
    void integrationOverrideReturnsCustomSuffix() {
        ShapeId protocol = ShapeId.from("smithy.beam.test#TestCustomProtocol");
        BeamProtocolIntegration integration =
                new BeamProtocolIntegration() {
                    @Override
                    public Optional<String> codecModuleSuffix(ShapeId protocolTraitId) {
                        if (protocol.equals(protocolTraitId)) {
                            return Optional.of("test_custom_protocol");
                        }
                        return Optional.empty();
                    }
                };

        assertThat(BeamProtocolModuleSuffix.codecSuffix(protocol, List.of(integration)))
                .isEqualTo("test_custom_protocol");
    }
}
