package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class BeamDocumentationTest {

    @Mock
    private SymbolWriter<?, ?> writer;

    @Test
    void forShape_preservesMarkdownLineBreaks() {
        OperationShape op = OperationShape.builder()
                .id(ShapeId.from("demo.basic#Get"))
                .addTrait(new DocumentationTrait("""
                        First paragraph.

                        ## Section

                        Second paragraph.
                        """))
                .build();

        String doc = BeamDocumentation.forShape(op).orElseThrow();

        assertThat(doc).contains("First paragraph.\n\n## Section");
        assertThat(doc).contains("Second paragraph.");
    }

    @Test
    void toMarkdown_convertsHtmlHeadingsAndCodeBlocks() {
        String raw = """
                <h2>How to call</h2>
                <p>Use <code>name</code> in the path.</p>
                <pre>
                foo()
                </pre>
                """;
        OperationShape op = OperationShape.builder()
                .id(ShapeId.from("demo.basic#Get"))
                .addTrait(new DocumentationTrait(raw))
                .build();

        String doc = BeamDocumentation.forShape(op).orElseThrow();

        assertThat(doc).contains("<h2>How to call</h2>");
        assertThat(doc).contains("<code>name</code>");
        assertThat(doc).contains("<pre>");
        assertThat(doc).contains("foo()");
    }

    @Test
    void dedent_stripsCommonLeadingWhitespace() {
        String raw = "    Line one.\n\n        Indented code line.\n";

        assertThat(BeamDocumentation.dedent(raw)).isEqualTo("Line one.\n\n    Indented code line.");
    }

    @Test
    void writeElixirDoc_usesHeredocForMultilineText() {
        BeamDocumentation.writeElixirDoc(writer, "Line one.\n\nLine two.");

        InOrder order = inOrder(writer);
        order.verify(writer).openBlock("$L \"\"\"", "@doc");
        order.verify(writer).write("$L", "Line one.");
        order.verify(writer).write("$L", "");
        order.verify(writer).write("$L", "Line two.");
        order.verify(writer).closeBlock("\"\"\"");
        verifyNoMoreInteractions(writer);
    }

    @Test
    void writeErlangDoc_usesContinuationLinesForMultilineText() {
        BeamDocumentation.writeErlangDoc(writer, "Line one.\n\nLine two.");

        InOrder order = inOrder(writer);
        order.verify(writer).write("%% @doc");
        order.verify(writer).write("%% $L", "Line one.");
        order.verify(writer).write("%%");
        order.verify(writer).write("%% $L", "Line two.");
        verifyNoMoreInteractions(writer);
    }

    @Test
    void memberModuledocAppendix_listsDocumentedMembers() {
        StructureShape struct = StructureShape.builder()
                .id(ShapeId.from("demo.basic#Item"))
                .addMember("name", ShapeId.from("smithy.api#String"),
                        b -> b.addTrait(new DocumentationTrait("Display name.")))
                .addMember("count", ShapeId.from("smithy.api#Integer"))
                .build();

        String appendix = BeamDocumentation.memberModuledocAppendix(struct).orElseThrow();

        assertThat(appendix).contains("## Members");
        assertThat(appendix).contains("`name` - Display name.");
        assertThat(appendix).doesNotContain("count");
    }

    @Test
    void writeElixirTypedoc_usesHeredocForMultilineText() {
        BeamDocumentation.writeElixirTypedoc(writer, "Line one.\n\nLine two.");

        InOrder order = inOrder(writer);
        order.verify(writer).openBlock("$L \"\"\"", "@typedoc");
        order.verify(writer).write("$L", "Line one.");
        order.verify(writer).write("$L", "");
        order.verify(writer).write("$L", "Line two.");
        order.verify(writer).closeBlock("\"\"\"");
        verifyNoMoreInteractions(writer);
    }
}
