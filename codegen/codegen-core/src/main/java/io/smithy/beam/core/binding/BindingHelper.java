package io.smithy.beam.core.binding;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * Thin static facade over {@link HttpBindingIndex} for the language-specific
 * codegen modules. No language-specific logic is present here.
 */
public final class BindingHelper {

    private BindingHelper() {}

    /** Returns all {@code @httpLabel} bindings for the operation's input. */
    public static List<HttpBinding> labels(Model model, OperationShape op) {
        return HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.LABEL);
    }

    /** Returns all {@code @httpQuery} bindings for the operation's input. */
    public static List<HttpBinding> queries(Model model, OperationShape op) {
        return HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.QUERY);
    }

    /** Returns all {@code @httpHeader} bindings for the operation's input. */
    public static List<HttpBinding> headers(Model model, OperationShape op) {
        return HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.HEADER);
    }

    /**
     * Returns the single {@code @httpPayload} binding for the operation's
     * input, or empty if the input has no explicit payload binding.
     */
    public static Optional<HttpBinding> payload(Model model, OperationShape op) {
        List<HttpBinding> bindings = HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        return bindings.isEmpty() ? Optional.empty() : Optional.of(bindings.get(0));
    }

    /** Returns all {@code @httpPrefixHeaders} bindings for the operation's input. */
    public static List<HttpBinding> prefixHeaders(Model model, OperationShape op) {
        return HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    }

    /**
     * Returns all input members that are bound to the HTTP document body
     * (i.e. not bound to label, query, header, or payload), keyed by member name.
     */
    public static Map<String, HttpBinding> documentMembers(Model model, OperationShape op) {
        return HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.DOCUMENT)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        HttpBinding::getMemberName,
                        b -> b));
    }

    /**
     * Returns the {@code @http} trait on the operation, or empty if absent.
     */
    public static Optional<HttpTrait> http(OperationShape op) {
        return op.getTrait(HttpTrait.class);
    }

    /**
     * Returns the URI pattern string (e.g. {@code "/weather/{city}"}) from the
     * {@code @http} trait, or empty if the operation has no {@code @http} trait.
     */
    public static Optional<String> uriPattern(OperationShape op) {
        return op.getTrait(HttpTrait.class)
                .map(t -> t.getUri().toString());
    }

    /**
     * Returns the HTTP method from the {@code @http} trait.
     *
     * @throws IllegalArgumentException if the operation has no {@code @http} trait
     */
    public static String method(OperationShape op) {
        return op.getTrait(HttpTrait.class)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Operation " + op.getId() + " has no @http trait"))
                .getMethod();
    }

    /** Returns all unbound input members (not assigned to any HTTP location). */
    public static List<HttpBinding> unbound(Model model, OperationShape op) {
        return HttpBindingIndex.of(model)
                .getRequestBindings(op, HttpBinding.Location.UNBOUND);
    }

    /**
     * Returns all input bindings for the operation, keyed by member name.
     * This includes bindings of every {@link HttpBinding.Location}.
     */
    public static Map<String, HttpBinding> allRequestBindings(Model model, OperationShape op) {
        return Collections.unmodifiableMap(
                HttpBindingIndex.of(model).getRequestBindings(op));
    }
}
