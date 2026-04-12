package io.smithy.beam.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses URI templates to extract path parameter labels (e.g. {@code /users/{userId}}).
 */
public final class UriTemplate {

    private static final Pattern LABEL_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private final String template;
    private final List<String> labels;
    private final List<Boolean> greedyFlags;

    public UriTemplate(String template) {
        if (template == null) {
            throw new IllegalArgumentException("URI template cannot be null");
        }
        this.template = template;
        this.labels = new ArrayList<>();
        this.greedyFlags = new ArrayList<>();
        extractLabels();
    }

    private void extractLabels() {
        Matcher matcher = LABEL_PATTERN.matcher(template);
        while (matcher.find()) {
            String labelWithModifiers = matcher.group(1);
            boolean isGreedy = labelWithModifiers.endsWith("+");
            String label =
                    isGreedy ? labelWithModifiers.substring(0, labelWithModifiers.length() - 1) : labelWithModifiers;
            labels.add(label);
            greedyFlags.add(isGreedy);
        }
    }

    public String getTemplate() {
        return template;
    }

    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    public boolean isGreedy(int index) {
        return greedyFlags.get(index);
    }

    public boolean isGreedy(String labelName) {
        int index = labels.indexOf(labelName);
        if (index == -1) {
            return false;
        }
        return greedyFlags.get(index);
    }

    public boolean hasLabels() {
        return !labels.isEmpty();
    }

    public int getLabelCount() {
        return labels.size();
    }

    public String getPlaceholder(int index) {
        String label = labels.get(index);
        boolean greedy = greedyFlags.get(index);
        return greedy ? "{" + label + "+}" : "{" + label + "}";
    }

    public String getPlaceholder(String labelName) {
        int index = labels.indexOf(labelName);
        if (index == -1) {
            return null;
        }
        return getPlaceholder(index);
    }

    @Override
    public String toString() {
        return "UriTemplate{template='" + template + "', labels=" + labels + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        UriTemplate other = (UriTemplate) obj;
        return template.equals(other.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template);
    }
}
