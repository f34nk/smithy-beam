package io.smithy.beam.core.ir;

public record HeaderBinding(String smithyMemberName, String headerName, boolean required) {}
