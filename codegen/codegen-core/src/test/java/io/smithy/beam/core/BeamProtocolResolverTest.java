package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeamProtocolResolverTest {

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
        var protocol = software.amazon.smithy.model.shapes.ShapeId.from("aws.protocols#restJson1");

        CodegenException ex = assertThrows(CodegenException.class, () ->
                BeamProtocolResolver.assertClosureSupported(model, service, protocol));

        assertThat(ex.getMessage()).contains("streaming blob");
        assertThat(ex.getMessage()).contains("StreamBlob");
    }
}
