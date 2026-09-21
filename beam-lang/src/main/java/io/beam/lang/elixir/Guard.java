package io.beam.lang.elixir;

public sealed interface Guard
    permits IsTypeGuard, ComparisonGuard, AndGuard, OrGuard, FunctionArityGuard, ExpressionGuard {}
