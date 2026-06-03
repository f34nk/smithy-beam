package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BeamProtocolResolverTest {

    @Test
    void closureAuditAllowsStreamingBlobShapes() {
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
        var protocol = software.amazon.smithy.model.shapes.ShapeId.from("aws.protocols#restJson1");

        assertDoesNotThrow(() -> BeamProtocolResolver.assertClosureSupported(
                model, service, protocol, BeamEdition.V2026));
    }

    @Test
    void closureAuditAllowsEventStreamUnions() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace test

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
                            events: EventStream
                        }

                        @streaming
                        union EventStream {
                            member: MemberEvent
                        }

                        structure MemberEvent {
                            value: String
                        }
                        """)
                .assemble()
                .unwrap();

        ServiceShape service = model.getServiceShapes().iterator().next();
        var protocol = software.amazon.smithy.model.shapes.ShapeId.from("aws.protocols#restJson1");

        assertDoesNotThrow(() -> BeamProtocolResolver.assertClosureSupported(
                model, service, protocol, BeamEdition.V2026));
    }
}
