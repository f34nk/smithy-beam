package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class BeamHttpChecksumIndexTest {

    private static final ShapeId REQUIRED =
            ShapeId.from("smithy.beam.test.checksum#RequiredChecksum");
    private static final ShapeId FLEXIBLE =
            ShapeId.from("smithy.beam.test.checksum#FlexibleChecksum");

    private static Model loadModel() {
        URL resource = BeamHttpChecksumIndexTest.class.getResource("/model/http_checksum_index_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void requestChecksumsReturnsEmptyWhenTraitAbsent() {
        Model model = loadModel();
        BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
        OperationShape ping = OperationShape.builder()
                .id(ShapeId.from("smithy.beam.test.checksum#Ping"))
                .input(ShapeId.from("smithy.beam.test.checksum#Unit"))
                .output(ShapeId.from("smithy.beam.test.checksum#Unit"))
                .build();

        assertThat(index.requestChecksums(ping)).isEmpty();
    }

    @Test
    void requestChecksumsMapsRequiredTraitToContentMd5() {
        Model model = loadModel();
        BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
        OperationShape operation = model.expectShape(REQUIRED, OperationShape.class);

        assertThat(index.requestChecksums(operation)).containsExactly(
                new BeamHttpChecksumIndex.ChecksumBinding("MD5", "Content-MD5", true, false));
    }

    @Test
    void requestChecksumsResolvesFlexibleAlgorithmsFromEnumMember() {
        Model model = loadModel();
        BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
        OperationShape operation = model.expectShape(FLEXIBLE, OperationShape.class);

        assertThat(index.requestChecksums(operation)).containsExactly(
                new BeamHttpChecksumIndex.ChecksumBinding(
                        "CRC32C", "x-amz-checksum-crc32c", true, false),
                new BeamHttpChecksumIndex.ChecksumBinding(
                        "SHA256", "x-amz-checksum-sha256", true, false));
        assertThat(index.requestAlgorithmMemberName(operation)).contains("checksumAlgorithm");
    }

    @Test
    void responseChecksumsUsesModeledAlgorithms() {
        Model model = loadModel();
        BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
        OperationShape operation = model.expectShape(FLEXIBLE, OperationShape.class);

        assertThat(index.responseChecksums(operation)).containsExactly(
                new BeamHttpChecksumIndex.ChecksumBinding(
                        "CRC32C", "x-amz-checksum-crc32c", false, true),
                new BeamHttpChecksumIndex.ChecksumBinding(
                        "SHA256", "x-amz-checksum-sha256", false, true));
        assertThat(index.requestValidationModeMemberName(operation)).contains("validationMode");
    }
}
