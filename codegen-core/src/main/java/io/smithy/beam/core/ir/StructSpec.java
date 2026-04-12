package io.smithy.beam.core.ir;

import java.util.List;

public record StructSpec(String name, List<FieldSpec> fields, boolean isError, int httpErrorCode) {}
