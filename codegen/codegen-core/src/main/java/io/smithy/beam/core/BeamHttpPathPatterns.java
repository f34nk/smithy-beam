package io.smithy.beam.core;

import software.amazon.smithy.model.knowledge.HttpBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds language-specific HTTP path match patterns from Smithy @http uri templates.
 */
public final class BeamHttpPathPatterns {

    public enum SegmentKind { LITERAL, LABEL }

    public record PathSegment(SegmentKind kind, String value) {}

    private BeamHttpPathPatterns() {}

    /** Split "/types/{name}/items/{id}" into literal and label segments. */
    public static List<PathSegment> parseTemplate(String uriTemplate) {
        List<PathSegment> out = new ArrayList<>();
        int pos = 0;
        while (pos < uriTemplate.length()) {
            int start = uriTemplate.indexOf('{', pos);
            if (start < 0) {
                if (pos < uriTemplate.length()) {
                    out.add(new PathSegment(SegmentKind.LITERAL, uriTemplate.substring(pos)));
                }
                break;
            }
            if (start > pos) {
                out.add(new PathSegment(SegmentKind.LITERAL, uriTemplate.substring(pos, start)));
            }
            int end = uriTemplate.indexOf('}', start);
            out.add(new PathSegment(SegmentKind.LABEL, uriTemplate.substring(start + 1, end)));
            pos = end + 1;
        }
        return out;
    }

    public static boolean hasLabels(List<HttpBinding> labels) {
        return labels != null && !labels.isEmpty();
    }
}
