package io.smithy.beam.core.ir;

public record HttpSpec(String method, String uriTemplate, int successCode) {}
