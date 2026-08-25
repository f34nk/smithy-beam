package io.beam.dsl.erlang;

public sealed interface Guard
    permits AndGuard, EqualGuard, ExpressionGuard, IsTypeGuard, NotEqualGuard {}
