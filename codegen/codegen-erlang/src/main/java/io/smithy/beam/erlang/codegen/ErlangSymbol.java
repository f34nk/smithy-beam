package io.smithy.beam.erlang.codegen;

import software.amazon.smithy.codegen.core.Symbol;

/**
 * Factory helpers for Erlang-typed {@link Symbol} instances.
 *
 * <p>Not a subclass of {@code Symbol}. All returned symbols carry a
 * {@code "erlang.kind"} property so the {@link ErlangWriter} {@code $T}
 * formatter can differentiate atoms from module refs from builtins.
 */
public final class ErlangSymbol {

    public static final String PROP_KIND = "erlang.kind";

    public enum Kind { ATOM, BUILTIN, MODULE_REF }

    private ErlangSymbol() {}

    /** Returns a symbol representing an Erlang atom literal, e.g. {@code ok}. */
    public static Symbol atom(String name) {
        return Symbol.builder()
                .name(name)
                .putProperty(PROP_KIND, Kind.ATOM)
                .build();
    }

    /** Returns a symbol representing a built-in Erlang type, e.g. {@code binary()}. */
    public static Symbol builtin(String erlangType) {
        return Symbol.builder()
                .name(erlangType)
                .putProperty(PROP_KIND, Kind.BUILTIN)
                .build();
    }

    /**
     * Returns a symbol representing a qualified module reference,
     * e.g. {@code Module:function}.
     */
    public static Symbol moduleRef(String module, String function) {
        return Symbol.builder()
                .name(module + ":" + function)
                .putProperty(PROP_KIND, Kind.MODULE_REF)
                .build();
    }
}
