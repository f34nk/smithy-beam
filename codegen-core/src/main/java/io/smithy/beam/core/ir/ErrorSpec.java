package io.smithy.beam.core.ir;

import java.util.List;

public record ErrorSpec(List<ErrorBinding> errors, ErrorCodeStrategy codeStrategy) {}
