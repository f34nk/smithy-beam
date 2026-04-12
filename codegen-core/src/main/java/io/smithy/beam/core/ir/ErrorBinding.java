package io.smithy.beam.core.ir;

public record ErrorBinding(String smithyName, int httpCode, ErrorCodeStrategy strategy) {}
