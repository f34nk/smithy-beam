package io.beam.lang.erlang;

public sealed interface Guard
    permits AndGuard, EqualGuard, ExpressionGuard, IsTypeGuard, NotEqualGuard {}
