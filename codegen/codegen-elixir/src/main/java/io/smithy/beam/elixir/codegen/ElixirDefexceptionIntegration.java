package io.smithy.beam.elixir.codegen;

import io.smithy.beam.elixir.codegen.sections.StructTypeSection;
import java.util.List;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir feature integration that turns generated error structs into proper
 * {@code Exception}-implementing modules by emitting a {@code message/1}
 * callback inside every shape that carries an {@link ErrorTrait}.
 *
 * <p>{@code ElixirWriter.writeStructModule} already emits {@code defexception}
 * (rather than {@code defstruct}) for error shapes — this integration completes
 * the {@code Exception} behaviour by appending a {@code def message/1} clause:
 *
 * <ul>
 *   <li>For error shapes that carry a {@code message} member (the Smithy
 *       convention), emit
 *       {@code def message(%__MODULE__{message: msg}), do: msg}.</li>
 *   <li>For error shapes without a {@code message} member, emit a fallback
 *       {@code def message(%__MODULE__{}), do: "<ShapeName>"} so the resulting
 *       struct still satisfies the {@code Exception} behaviour without
 *       crashing on {@code Exception.message/1}.</li>
 * </ul>
 *
 * <p>The integration appends to {@link StructTypeSection}. That section is
 * pushed by {@code ElixirWriter.writeStructModule} <em>inside</em> the
 * surrounding {@code defmodule} block, so anything written here lands inside
 * the module body — alongside the {@code defexception} line and the
 * {@code @type t()} alias.
 *
 * <p>Non-error shapes are filtered out via the
 * {@link CodeInterceptor#isIntercepted} hook so the appender adds nothing to
 * regular {@code defstruct} modules and does not perturb their output.
 */
public final class ElixirDefexceptionIntegration implements ElixirIntegration {

    /**
     * Conventional Smithy member name carrying the human-readable error
     * message. Matched case-insensitively against the structure's members.
     */
    private static final String MESSAGE_MEMBER = "message";

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        return List.of(new MessageAppender());
    }

    private static final class MessageAppender
            implements CodeInterceptor.Appender<StructTypeSection, ElixirWriter> {

        @Override
        public Class<StructTypeSection> sectionType() {
            return StructTypeSection.class;
        }

        @Override
        public boolean isIntercepted(StructTypeSection section) {
            return section.structure().hasTrait(ErrorTrait.class);
        }

        @Override
        public void append(ElixirWriter writer, StructTypeSection section) {
            StructureShape shape = section.structure();
            writer.write("");
            if (hasMessageMember(shape)) {
                writer.write("def message(%__MODULE__{message: msg}), do: msg");
            } else {
                // Fallback: no `message` member — surface the shape's local
                // name as a static description so Exception.message/1 still
                // returns a useful string.
                writer.write("def message(%__MODULE__{}), do: $S",
                        shape.getId().getName());
            }
        }

        private static boolean hasMessageMember(StructureShape shape) {
            for (MemberShape member : shape.getAllMembers().values()) {
                if (member.getMemberName().equalsIgnoreCase(MESSAGE_MEMBER)) {
                    return true;
                }
            }
            return false;
        }
    }
}
