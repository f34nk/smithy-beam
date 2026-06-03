package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.protocoltests.traits.AppliesTo;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code smithy.test#httpRequestTests} and {@code smithy.test#httpResponseTests}
 * from operations and error shapes for compliance test emission.
 */
public final class BeamHttpComplianceTests {

    public record HttpRequestTestCase(
            String id,
            String method,
            String uri,
            Map<String, String> headers,
            String body,
            ObjectNode params,
            List<String> queryParams,
            Optional<AppliesTo> appliesTo,
            ShapeId protocol) {}

    public record HttpResponseTestCase(
            String id,
            int code,
            Map<String, String> headers,
            String body,
            ObjectNode params,
            Optional<AppliesTo> appliesTo,
            ShapeId protocol,
            Optional<ShapeId> errorShapeId) {}

    public record OperationRequestTests(OperationShape operation, List<HttpRequestTestCase> cases) {}

    public record OperationResponseTests(
            OperationShape operation,
            Optional<StructureShape> errorShape,
            List<HttpResponseTestCase> cases) {}

    private BeamHttpComplianceTests() {}

    public static List<HttpRequestTestCase> requestTests(Model model, OperationShape operation) {
        return operation.getTrait(HttpRequestTestsTrait.class)
                .map(trait -> trait.getTestCases().stream().map(BeamHttpComplianceTests::mapRequestCase).toList())
                .orElse(List.of());
    }

    public static List<HttpResponseTestCase> responseTests(Model model, OperationShape operation) {
        List<HttpResponseTestCase> cases = new ArrayList<>();
        operation.getTrait(HttpResponseTestsTrait.class).ifPresent(trait -> {
            for (var testCase : trait.getTestCases()) {
                cases.add(mapResponseCase(testCase, Optional.empty()));
            }
        });
        for (ShapeId errorId : operation.getErrors()) {
            StructureShape error = model.expectShape(errorId, StructureShape.class);
            error.getTrait(HttpResponseTestsTrait.class).ifPresent(trait -> {
                for (var testCase : trait.getTestCases()) {
                    cases.add(mapResponseCase(testCase, Optional.of(errorId)));
                }
            });
        }
        return List.copyOf(cases);
    }

    public static List<OperationRequestTests> requestTestsForService(Model model, ServiceShape service) {
        TopDownIndex topDown = TopDownIndex.of(model);
        List<OperationRequestTests> bindings = new ArrayList<>();
        for (OperationShape operation : topDown.getContainedOperations(service)) {
            List<HttpRequestTestCase> cases = requestTests(model, operation);
            if (!cases.isEmpty()) {
                bindings.add(new OperationRequestTests(operation, cases));
            }
        }
        return List.copyOf(bindings);
    }

    public static List<OperationResponseTests> responseTestsForService(Model model, ServiceShape service) {
        TopDownIndex topDown = TopDownIndex.of(model);
        List<OperationResponseTests> bindings = new ArrayList<>();
        for (OperationShape operation : topDown.getContainedOperations(service)) {
            List<HttpResponseTestCase> successCases = operation.getTrait(HttpResponseTestsTrait.class)
                    .map(trait -> trait.getTestCases().stream()
                            .map(testCase -> mapResponseCase(testCase, Optional.empty()))
                            .toList())
                    .orElse(List.of());
            if (!successCases.isEmpty()) {
                bindings.add(new OperationResponseTests(operation, Optional.empty(), successCases));
            }
            for (ShapeId errorId : operation.getErrors()) {
                StructureShape error = model.expectShape(errorId, StructureShape.class);
                List<HttpResponseTestCase> errorCases = error.getTrait(HttpResponseTestsTrait.class)
                        .map(trait -> trait.getTestCases().stream()
                                .map(testCase -> mapResponseCase(testCase, Optional.of(errorId)))
                                .toList())
                        .orElse(List.of());
                if (!errorCases.isEmpty()) {
                    bindings.add(new OperationResponseTests(operation, Optional.of(error), errorCases));
                }
            }
        }
        return List.copyOf(bindings);
    }

    public static List<HttpRequestTestCase> filterRequestTests(
            List<HttpRequestTestCase> cases, AppliesTo appliesTo, ShapeId protocol) {
        return cases.stream()
                .filter(testCase -> matchesProtocol(testCase.protocol(), protocol))
                .filter(testCase -> matchesAppliesTo(testCase.appliesTo(), appliesTo))
                .toList();
    }

    public static List<HttpResponseTestCase> filterResponseTests(
            List<HttpResponseTestCase> cases, AppliesTo appliesTo, ShapeId protocol) {
        return cases.stream()
                .filter(testCase -> matchesProtocol(testCase.protocol(), protocol))
                .filter(testCase -> matchesAppliesTo(testCase.appliesTo(), appliesTo))
                .toList();
    }

    private static HttpRequestTestCase mapRequestCase(
            software.amazon.smithy.protocoltests.traits.HttpRequestTestCase testCase) {
        return new HttpRequestTestCase(
                testCase.getId(),
                testCase.getMethod(),
                testCase.getUri(),
                copyHeaders(testCase.getHeaders()),
                testCase.getBody().orElse(null),
                testCase.getParams(),
                List.copyOf(testCase.getQueryParams()),
                testCase.getAppliesTo(),
                testCase.getProtocol());
    }

    private static HttpResponseTestCase mapResponseCase(
            software.amazon.smithy.protocoltests.traits.HttpResponseTestCase testCase,
            Optional<ShapeId> errorShapeId) {
        return new HttpResponseTestCase(
                testCase.getId(),
                testCase.getCode(),
                copyHeaders(testCase.getHeaders()),
                testCase.getBody().orElse(null),
                testCase.getParams(),
                testCase.getAppliesTo(),
                testCase.getProtocol(),
                errorShapeId);
    }

    private static Map<String, String> copyHeaders(Map<String, String> headers) {
        return headers.isEmpty() ? Map.of() : new LinkedHashMap<>(headers);
    }

    private static boolean matchesProtocol(ShapeId testProtocol, ShapeId serviceProtocol) {
        return testProtocol.equals(serviceProtocol);
    }

    private static boolean matchesAppliesTo(Optional<AppliesTo> appliesTo, AppliesTo target) {
        return appliesTo.map(value -> value == target).orElse(true);
    }
}
