package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BeamHttpPathPatternsTest {

  @Test
  void parseTemplate_singleLabel() {
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate("/types/{name}");
    assertThat(segments)
        .containsExactly(
            new BeamHttpPathPatterns.PathSegment(
                BeamHttpPathPatterns.SegmentKind.LITERAL, "/types/"),
            new BeamHttpPathPatterns.PathSegment(BeamHttpPathPatterns.SegmentKind.LABEL, "name"));
  }

  @Test
  void parseTemplate_singleLabelAtRoot() {
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate("/items/{id}");
    assertThat(segments)
        .containsExactly(
            new BeamHttpPathPatterns.PathSegment(
                BeamHttpPathPatterns.SegmentKind.LITERAL, "/items/"),
            new BeamHttpPathPatterns.PathSegment(BeamHttpPathPatterns.SegmentKind.LABEL, "id"));
  }

  @Test
  void parseTemplate_multipleLabels() {
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate("/types/{name}/items/{id}");
    assertThat(segments)
        .containsExactly(
            new BeamHttpPathPatterns.PathSegment(
                BeamHttpPathPatterns.SegmentKind.LITERAL, "/types/"),
            new BeamHttpPathPatterns.PathSegment(BeamHttpPathPatterns.SegmentKind.LABEL, "name"),
            new BeamHttpPathPatterns.PathSegment(
                BeamHttpPathPatterns.SegmentKind.LITERAL, "/items/"),
            new BeamHttpPathPatterns.PathSegment(BeamHttpPathPatterns.SegmentKind.LABEL, "id"));
  }
}
