package io.smithy.beam.elixir.codegen;

import software.amazon.smithy.codegen.core.Symbol;

/**
 * Factory helpers for Elixir-typed {@link Symbol} instances.
 *
 * <p>Not a subclass of {@code Symbol}. All returned symbols carry an
 * {@code "elixir.kind"} property so the {@link ElixirWriter} {@code $T}
 * formatter can differentiate atoms from module names from builtins.
 */
public final class ElixirSymbol {

    public static final String PROP_KIND = "elixir.kind";

    public enum Kind { ATOM, MODULE, BUILTIN }

    private ElixirSymbol() {}

    /** Returns a symbol representing an Elixir atom literal, e.g. {@code :ok}. */
    public static Symbol atom(String name) {
        String atomName = name.startsWith(":") ? name : ":" + name;
        return Symbol.builder()
                .name(atomName)
                .putProperty(PROP_KIND, Kind.ATOM)
                .build();
    }

    /** Returns a symbol representing an Elixir module, e.g. {@code Foo.Bar}. */
    public static Symbol module(String name) {
        return Symbol.builder()
                .name(name)
                .putProperty(PROP_KIND, Kind.MODULE)
                .build();
    }

    /** Returns a symbol representing a built-in Elixir type, e.g. {@code String.t()}. */
    public static Symbol builtin(String elixirType) {
        return Symbol.builder()
                .name(elixirType)
                .putProperty(PROP_KIND, Kind.BUILTIN)
                .build();
    }
}
