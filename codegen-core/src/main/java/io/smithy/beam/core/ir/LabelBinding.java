package io.smithy.beam.core.ir;

public record LabelBinding(String smithyMemberName, String uriPlaceholder, boolean requiresEncoding) {}
