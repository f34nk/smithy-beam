package io.smithy.beam.elixir.codegen;

import io.smithy.beam.elixir.codegen.sections.OperationSendSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Base class for all Elixir auth-scheme integrations.
 *
 * <p>Identical structure to {@link DefaultElixirProtocolIntegration} but gated
 * on an auth-trait {@link ShapeId}.
 *
 * <p>Subclasses override {@link #authDependencies()} to declare their runtime
 * dependencies, and may further override {@link #interceptors(ElixirContext)} to
 * inject auth-specific code into {@link OperationSendSection}.
 */
public abstract class DefaultElixirAuthIntegration implements ElixirIntegration {

    /** Returns the Smithy auth-trait shape ID this integration handles. */
    public abstract ShapeId authTraitId();

    /**
     * Returns the runtime {@link ElixirDependency} values this auth scheme requires.
     * Defaults to none; override in concrete classes to declare dependencies.
     */
    protected List<ElixirDependency> authDependencies() {
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if the service being generated has the auth trait
     * this integration targets.
     */
    protected boolean isApplicable(ElixirContext ctx) {
        return ctx.service().hasTrait(authTraitId());
    }

    /**
     * Registers an {@link OperationSendSection} interceptor that injects the auth
     * runtime dependencies declared by {@link #authDependencies()}.
     *
     * <p>Concrete classes may override this method to also emit stub Elixir code
     * (e.g. {@code SmithyBeam.SigV4.sign(...)}).
     */
    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        List<ElixirDependency> deps = authDependencies();
        if (deps.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(
                CodeInterceptor.appender(OperationSendSection.class, (writer, section) ->
                        deps.forEach(writer::addDependency)));
    }
}
