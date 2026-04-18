package io.smithy.beam.erlang.codegen;

import software.amazon.smithy.codegen.core.Symbol;

/**
 * Factory helper for building Erlang-specific {@link Symbol} instances.
 *
 * <p>All symbols produced here carry an {@code "erlang.kind"} property so
 * that {@code ErlangWriter}'s {@code $T} formatter can differentiate atoms
 * from builtins and module references:
 * <ul>
 *   <li>{@code "atom"} — a bare Erlang atom (e.g. {@code ok}, {@code error})</li>
 *   <li>{@code "builtin"} — a built-in Erlang type expression (e.g. {@code binary()}, {@code integer()})</li>
 *   <li>{@code "module_ref"} — a {@code module:function} qualified reference</li>
 * </ul>
 */
public final class ErlangSymbol {

    static final String PROPERTY_KIND = "erlang.kind";
    static final String KIND_ATOM = "atom";
    static final String KIND_BUILTIN = "builtin";
    static final String KIND_MODULE_REF = "module_ref";

    private ErlangSymbol() {}

    /**
     * Builds a {@link Symbol} whose name is an Erlang atom.
     *
     * @param name the atom name, e.g. {@code "ok"} or {@code "error"}
     * @return a Symbol representing the atom
     */
    public static Symbol atom(String name) {
        return Symbol.builder()
                .name(name)
                .putProperty(PROPERTY_KIND, KIND_ATOM)
                .build();
    }

    /**
     * Builds a {@link Symbol} for a built-in Erlang type expression.
     *
     * <p>The {@code erlangType} is emitted verbatim by the {@code $T} formatter,
     * e.g. {@code "binary()"}, {@code "integer()"}, {@code "boolean()"}.
     *
     * @param erlangType the Erlang type expression string
     * @return a Symbol representing the built-in type
     */
    public static Symbol builtin(String erlangType) {
        return Symbol.builder()
                .name(erlangType)
                .putProperty(PROPERTY_KIND, KIND_BUILTIN)
                .build();
    }

    /**
     * Builds a {@link Symbol} for a {@code module:function} qualified reference.
     *
     * <p>The resulting symbol's {@code name} is {@code function}, its
     * {@code namespace} is {@code module}, and the {@code $T} formatter will
     * render it as {@code module:function}.
     *
     * @param module   the Erlang module name, e.g. {@code "smithy_json"}
     * @param function the function name within the module, e.g. {@code "encode"}
     * @return a Symbol representing the module:function reference
     */
    public static Symbol moduleRef(String module, String function) {
        return Symbol.builder()
                .name(function)
                .namespace(module, ":")
                .putProperty(PROPERTY_KIND, KIND_MODULE_REF)
                .build();
    }
}
