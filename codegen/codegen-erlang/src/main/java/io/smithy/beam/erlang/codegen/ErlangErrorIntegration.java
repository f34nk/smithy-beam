package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.sections.ServiceErrorHelpersSection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Erlang feature integration that emits service-level error reflection
 * helpers into every generated client module, complementing the per-operation
 * {@code parse_error/2} clauses produced by the protocol integrations.
 *
 * <p>Three helpers are emitted into the {@link ServiceErrorHelpersSection}
 * injection point pushed at the bottom of the client module by
 * {@code ErlangClientCodegen.generateService}:
 *
 * <ul>
 *   <li>{@code errors/0} — returns a flat list of every error atom known to
 *       the service (operation-level + service-level errors, deduplicated).
 *       The atoms match the snake-case record names emitted by
 *       {@link ErlangWriter#writeRecord} into the types {@code .hrl}.</li>
 *   <li>{@code is_error/1} — predicate over an arbitrary term. Returns
 *       {@code true} when called with {@code {error, Err}} where {@code Err}
 *       is an Erlang record whose first element is one of the atoms
 *       returned by {@code errors/0}.</li>
 *   <li>{@code error_to_atom/1} — projects {@code {error, #err{}}} onto its
 *       record-name atom (or {@code unknown_error} for unrecognised terms).
 *       Used by the AWS-JSON codecs in the {@code parse_error/2} body to
 *       look up the canonical error atom from the wire {@code __type}.</li>
 * </ul>
 *
 * <p>The helpers are <em>always</em> emitted — even for services with no
 * declared errors — so downstream code that pattern-matches on the helper
 * exports can rely on their presence regardless of model shape.
 *
 * <p>Mode-gated on {@link Mode#CLIENT}: the server module owns the
 * other side of the contract and emits errors rather than consuming them,
 * so this integration is a no-op when the codegen director is configured
 * for {@link Mode#SERVER}.
 */
public final class ErlangErrorIntegration implements ErlangIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (ctx.settings().mode() != Mode.CLIENT) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(ServiceErrorHelpersSection.class, (writer, section) ->
                        emit(writer, ctx, section.service())));
    }

    /**
     * Emits the three helper functions into the open writer and registers
     * their exports so they appear in the generated module's
     * {@code -export([…])} attribute.
     */
    private static void emit(ErlangWriter w, ErlangContext ctx, ServiceShape service) {
        List<String> errorAtoms = collectErrorAtoms(ctx, service);

        w.write("");
        emitErrorsFn(w, errorAtoms);
        w.write("");
        emitIsErrorFn(w);
        w.write("");
        emitErrorToAtomFn(w);

        w.addExport("errors", 0);
        w.addExport("is_error", 1);
        w.addExport("error_to_atom", 1);
    }

    /**
     * Collects the snake-case record-name atoms for every distinct error
     * shape reachable from the service: union of {@code service.getErrors()}
     * and {@code op.getErrors(service)} across every operation. Insertion
     * order is preserved so the generated list is deterministic.
     */
    private static List<String> collectErrorAtoms(ErlangContext ctx, ServiceShape service) {
        Set<ShapeId> errors = new LinkedHashSet<>(service.getErrors());
        for (ShapeId opId : service.getAllOperations()) {
            OperationShape op = ctx.model().expectShape(opId, OperationShape.class);
            errors.addAll(op.getErrors(service));
        }
        return errors.stream()
                .map(id -> CaseUtils.toSnakeCase(id.getName()))
                .toList();
    }

    private static void emitErrorsFn(ErlangWriter w, List<String> errorAtoms) {
        if (errorAtoms.isEmpty()) {
            w.write("errors() ->");
            w.write("    [].");
            return;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (String atom : errorAtoms) {
            joiner.add(atom);
        }
        w.write("errors() ->");
        w.write("    [$L].", joiner.toString());
    }

    /**
     * Emits a tag-based predicate that delegates the actual classification
     * to {@code error_to_atom/1}. Avoids generating one clause per error
     * shape — the model's authoritative list lives in {@code errors/0}.
     */
    private static void emitIsErrorFn(ErlangWriter w) {
        w.write("is_error({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->");
        w.write("    lists:member(element(1, Err), errors());");
        w.write("is_error(_) ->");
        w.write("    false.");
    }

    /**
     * Emits a record-name projection that returns the matching error atom
     * for known errors and {@code unknown_error} otherwise. Mirrors the
     * shape of {@code is_error/1} so the two helpers always agree.
     */
    private static void emitErrorToAtomFn(ErlangWriter w) {
        w.write("error_to_atom({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->");
        w.write("    case lists:member(element(1, Err), errors()) of");
        w.write("        true -> element(1, Err);");
        w.write("        false -> unknown_error");
        w.write("    end;");
        w.write("error_to_atom(_) ->");
        w.write("    unknown_error.");
    }
}
