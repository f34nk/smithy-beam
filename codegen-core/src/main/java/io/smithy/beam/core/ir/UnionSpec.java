package io.smithy.beam.core.ir;

import java.util.List;

public record UnionSpec(String name, List<FieldSpec> variants) {}
