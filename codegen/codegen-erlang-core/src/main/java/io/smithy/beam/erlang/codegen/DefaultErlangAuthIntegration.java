package io.smithy.beam.erlang.codegen;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Abstract base class for all Erlang auth-scheme integrations.
 *
 * <p>Mirrors the structure of {@link DefaultErlangProtocolIntegration} but
 * gates activation on an auth trait rather than a protocol trait. Auth
 * traits are looked up directly on the service shape without going through
 * {@link io.smithy.beam.core.ProtocolResolver}, because auth schemes are not
 * centralised in the same registry.
 *
 * <p>The {@link #preprocessModel(Model, ErlangSettings)} override returns the
 * model unchanged when {@link #isApplicable(Model, ErlangSettings)} is
 * {@code false}, so multiple auth integrations can coexist on the classpath
 * without interfering with one another.
 *
 * <p>Concrete subclasses should override
 * {@link ErlangIntegration#customize} and/or
 * {@link ErlangIntegration#interceptors} to inject auth-specific signing
 * calls via the {@code OperationSendSection} interception point and add the
 * relevant {@link ErlangDependency} constant (e.g.
 * {@link ErlangDependency#SMITHY_SIGV4}) to operation symbols.
 *
 * <h2>Example subclass skeleton</h2>
 * <pre>{@code
 * public final class ErlangSigV4Integration
 *         extends DefaultErlangAuthIntegration {
 *
 *     @Override
 *     public ShapeId authTraitId() {
 *         return ShapeId.from("aws.auth#sigv4");
 *     }
 * }
 * }</pre>
 */
public abstract class DefaultErlangAuthIntegration implements ErlangIntegration {

    /**
     * Returns the Smithy auth trait shape ID that this integration handles.
     *
     * <p>The returned ID is used directly to check for the trait on the
     * service shape via {@link ServiceShape#hasTrait(ShapeId)}.
     *
     * @return the auth trait shape ID, e.g.
     *         {@code ShapeId.from("aws.auth#sigv4")}
     */
    public abstract ShapeId authTraitId();

    /**
     * Returns {@code true} when the service shape declared in {@code settings}
     * carries the auth trait returned by {@link #authTraitId()}.
     *
     * @param model    the (possibly pre-processed) Smithy model
     * @param settings the resolved Erlang settings for this invocation
     * @return {@code true} if this integration should activate
     */
    public boolean isApplicable(Model model, ErlangSettings settings) {
        ServiceShape service = model.expectShape(settings.getService(), ServiceShape.class);
        return service.hasTrait(authTraitId());
    }

    /**
     * No-op when {@link #isApplicable(Model, ErlangSettings)} returns
     * {@code false}; otherwise returns the model unchanged.
     *
     * <p>Auth integrations that need to transform the model should override
     * this method, call {@code super.preprocessModel(model, settings)} first,
     * and then apply their own transforms only when the integration is
     * applicable.
     */
    @Override
    public Model preprocessModel(Model model, ErlangSettings settings) {
        if (!isApplicable(model, settings)) {
            return model;
        }
        return model;
    }
}
