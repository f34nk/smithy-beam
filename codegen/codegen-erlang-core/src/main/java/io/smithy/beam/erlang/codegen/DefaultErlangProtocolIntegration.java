package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.ProtocolResolver;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

/**
 * Abstract base class for all Erlang protocol integrations.
 *
 * <p>Subclasses declare which protocol they implement via {@link #protocolId()}
 * and inherit gate logic that makes all {@code SmithyIntegration} hooks
 * no-ops when the target service does not carry that protocol trait.
 *
 * <p>The {@link #preprocessModel(Model, ErlangSettings)} override returns the
 * model unchanged when {@link #isApplicable(Model, ErlangSettings)} is
 * {@code false}, so multiple protocol integrations can coexist on the
 * classpath without interfering with one another.
 *
 * <p>Concrete subclasses should override
 * {@link ErlangIntegration#customize} and/or
 * {@link ErlangIntegration#interceptors} to emit protocol-specific codec
 * stubs via the {@code ErlangContext}'s writer delegator. They should also
 * add the relevant {@link ErlangDependency} constants to operation symbols
 * so that {@code ErlangRuntimeIntegration} copies the right runtime files.
 *
 * <h2>Example subclass skeleton</h2>
 * <pre>{@code
 * public final class ErlangRestJson1Integration
 *         extends DefaultErlangProtocolIntegration {
 *
 *     @Override
 *     public ShapeId protocolId() {
 *         return ShapeId.from("aws.protocols#restJson1");
 *     }
 * }
 * }</pre>
 */
public abstract class DefaultErlangProtocolIntegration implements ErlangIntegration {

    /**
     * Returns the Smithy protocol trait shape ID that this integration handles.
     *
     * <p>Must match one of the IDs registered in {@link ProtocolResolver}.
     *
     * @return the protocol trait shape ID, e.g.
     *         {@code ShapeId.from("aws.protocols#restJson1")}
     */
    public abstract ShapeId protocolId();

    /**
     * Returns {@code true} when the service shape declared in {@code settings}
     * carries the protocol trait returned by {@link #protocolId()}.
     *
     * @param model    the (possibly pre-processed) Smithy model
     * @param settings the resolved Erlang settings for this invocation
     * @return {@code true} if this integration should activate
     */
    public boolean isApplicable(Model model, ErlangSettings settings) {
        Class<? extends Trait> traitClass = ProtocolResolver.resolve(protocolId())
                .orElseThrow(() -> new IllegalStateException(
                        "Protocol ID " + protocolId() + " is not registered in ProtocolResolver"));
        ServiceShape service = model.expectShape(settings.getService(), ServiceShape.class);
        return service.hasTrait(traitClass);
    }

    /**
     * No-op when {@link #isApplicable(Model, ErlangSettings)} returns
     * {@code false}; otherwise returns the model unchanged.
     *
     * <p>Protocol integrations that need to transform the model should override
     * this method, call {@code super.preprocessModel(model, settings)} first,
     * and then apply their own transforms only when the result is non-null
     * (i.e. when the protocol is applicable).
     */
    @Override
    public Model preprocessModel(Model model, ErlangSettings settings) {
        if (!isApplicable(model, settings)) {
            return model;
        }
        return model;
    }
}
