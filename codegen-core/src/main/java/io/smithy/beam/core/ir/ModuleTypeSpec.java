package io.smithy.beam.core.ir;

import java.util.List;

public record ModuleTypeSpec(
        String serviceName,
        List<StructSpec> structures,
        List<EnumSpec> enums,
        List<UnionSpec> unions,
        List<StructSpec> errors,
        boolean hasServiceErrors) {}
