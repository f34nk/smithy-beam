package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.waiters.Acceptor;
import software.amazon.smithy.waiters.Matcher;
import software.amazon.smithy.waiters.PathMatcher;
import software.amazon.smithy.waiters.WaitableTrait;
import software.amazon.smithy.waiters.Waiter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects {@code smithy.waiters#waitable} definitions from operations in a service closure.
 */
public final class BeamWaiterIndex {

    public record WaiterBinding(
            String name,
            OperationShape operation,
            int minDelaySeconds,
            int maxDelaySeconds,
            List<AcceptorInfo> acceptors) {}

    public record PathMatcherInfo(String path, String expected, String comparator) {}

    public record AcceptorInfo(
            String state,
            String matcherKind,
            Optional<Boolean> successExpected,
            Optional<String> errorTypeName,
            Optional<StructureShape> resolvedError,
            Optional<PathMatcherInfo> pathMatcher) {}

    private final List<WaiterBinding> bindings;

    private BeamWaiterIndex(List<WaiterBinding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public static BeamWaiterIndex of(Model model, ServiceShape service) {
        List<WaiterBinding> bindings = new ArrayList<>();
        TopDownIndex topDown = TopDownIndex.of(model);
        for (OperationShape operation : topDown.getContainedOperations(service)) {
            operation.getTrait(WaitableTrait.class).ifPresent(trait -> {
                for (Map.Entry<String, Waiter> entry : trait.getWaiters().entrySet()) {
                    Waiter waiter = entry.getValue();
                    List<AcceptorInfo> acceptors = new ArrayList<>();
                    for (Acceptor acceptor : waiter.getAcceptors()) {
                        acceptors.add(toAcceptorInfo(model, operation, acceptor));
                    }
                    bindings.add(new WaiterBinding(
                            entry.getKey(),
                            operation,
                            waiter.getMinDelay(),
                            waiter.getMaxDelay(),
                            List.copyOf(acceptors)));
                }
            });
        }
        bindings.sort(Comparator.comparing(WaiterBinding::name));
        return new BeamWaiterIndex(bindings);
    }

    public List<WaiterBinding> bindings() {
        return bindings;
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    public List<AcceptorInfo> acceptors(WaiterBinding binding) {
        return binding.acceptors();
    }

    private static AcceptorInfo toAcceptorInfo(Model model, OperationShape operation, Acceptor acceptor) {
        Matcher<?> matcher = acceptor.getMatcher();
        Optional<Boolean> successExpected = successMatcher(matcher);
        Optional<String> errorTypeName = errorTypeName(matcher);
        Optional<StructureShape> resolvedError =
                errorTypeName.flatMap(name -> resolveErrorType(model, operation, name));
        Optional<PathMatcherInfo> pathMatcher = pathMatcher(matcher).map(pm -> new PathMatcherInfo(
                pm.getPath(), pm.getExpected(), pm.getComparator().toString()));
        return new AcceptorInfo(
                acceptor.getState().toString(),
                matcher.getMemberName(),
                successExpected,
                errorTypeName,
                resolvedError,
                pathMatcher);
    }

    private static Optional<PathMatcher> pathMatcher(Matcher<?> matcher) {
        return matcher.accept(new Matcher.Visitor<>() {
            @Override
            public Optional<PathMatcher> visitOutput(Matcher.OutputMember outputPath) {
                return Optional.of(outputPath.getValue());
            }

            @Override
            public Optional<PathMatcher> visitInputOutput(Matcher.InputOutputMember inputOutputPath) {
                return Optional.of(inputOutputPath.getValue());
            }

            @Override
            public Optional<PathMatcher> visitSuccess(Matcher.SuccessMember success) {
                return Optional.empty();
            }

            @Override
            public Optional<PathMatcher> visitErrorType(Matcher.ErrorTypeMember errorType) {
                return Optional.empty();
            }

            @Override
            public Optional<PathMatcher> visitUnknown(Matcher.UnknownMember unknown) {
                return Optional.empty();
            }
        });
    }

    private static Optional<String> errorTypeName(Matcher<?> matcher) {
        return matcher.accept(new Matcher.Visitor<>() {
            @Override
            public Optional<String> visitOutput(Matcher.OutputMember outputPath) {
                return Optional.empty();
            }

            @Override
            public Optional<String> visitInputOutput(Matcher.InputOutputMember inputOutputPath) {
                return Optional.empty();
            }

            @Override
            public Optional<String> visitSuccess(Matcher.SuccessMember success) {
                return Optional.empty();
            }

            @Override
            public Optional<String> visitErrorType(Matcher.ErrorTypeMember errorType) {
                return Optional.of(errorType.getValue());
            }

            @Override
            public Optional<String> visitUnknown(Matcher.UnknownMember unknown) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Boolean> successMatcher(Matcher<?> matcher) {
        return matcher.accept(new Matcher.Visitor<>() {
            @Override
            public Optional<Boolean> visitOutput(Matcher.OutputMember outputPath) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> visitInputOutput(Matcher.InputOutputMember inputOutputPath) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> visitSuccess(Matcher.SuccessMember success) {
                return Optional.of(success.getValue());
            }

            @Override
            public Optional<Boolean> visitErrorType(Matcher.ErrorTypeMember errorType) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> visitUnknown(Matcher.UnknownMember unknown) {
                return Optional.empty();
            }
        });
    }

    public static Optional<StructureShape> resolveErrorType(
            Model model, OperationShape operation, String errorType) {
        if (errorType.contains("#")) {
            ShapeId id = ShapeId.from(errorType);
            if (model.getShape(id).filter(StructureShape.class::isInstance).isPresent()) {
                return Optional.of(model.expectShape(id, StructureShape.class));
            }
        }
        for (ShapeId errorId : operation.getErrors()) {
            if (errorId.getName().equals(errorType)) {
                return Optional.of(model.expectShape(errorId, StructureShape.class));
            }
        }
        return Optional.empty();
    }
}
