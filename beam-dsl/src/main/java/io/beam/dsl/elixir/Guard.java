package io.beam.dsl.elixir;

public sealed interface Guard
    permits IsTypeGuard, ComparisonGuard, AndGuard, OrGuard, FunctionArityGuard, ExpressionGuard {}
