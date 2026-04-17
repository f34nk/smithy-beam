package io.smithy.beam.core.model;

import io.smithy.beam.core.CodegenException;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.StructSpec;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the IR produced by the type and protocol analyzers before any rendering begins.
 *
 * <p>Call {@link #validate} immediately after {@code buildTypeSpec} and
 * {@code analyzeOperations} and before any writer call. A {@link CodegenException}
 * is thrown on the first violation found, naming the service and the offending IR node.
 *
 * <p>Rules enforced:
 * <ol>
 *   <li>Every {@code EnumSpec} has at least one value.</li>
 *   <li>Within the service, the deduplicated {@code ErrorBinding} list has no two
 *       entries with the same Smithy name (catches accidental double-registration).</li>
 *   <li>Every {@code OperationSpec.inputTypeName()} and {@code outputTypeName()}
 *       resolves to a {@code StructSpec} in {@code ModuleTypeSpec}, or to the
 *       literal {@code "map"} (the unit / unnamed-input/output convention), or is
 *       {@code null} (absent input/output).</li>
 *   <li>{@code BodySpec} consistency: when {@code encoding == NONE}, {@code bodyMemberNames}
 *       must be empty and {@code payloadMember} must be {@code null}.</li>
 * </ol>
 */
public final class ModelValidator {

    private ModelValidator() {}

    /**
     * Validates the IR triple for one service.
     *
     * @param svc   the Smithy service shape (used only for error messages)
     * @param types the type IR built by {@link TypeSpecBuilder}
     * @param ops   the operation IR built by the protocol analyzer
     * @throws CodegenException on the first validation failure
     */
    public static void validate(ServiceShape svc, ModuleTypeSpec types, List<OperationSpec> ops) {
        String svcName = svc.getId().toString();
        validateEnums(svcName, types);
        validateErrorBindingNames(svcName, ops);
        validateTypeRefs(svcName, types, ops);
        validateBodySpecs(svcName, ops);
    }

    // -------------------------------------------------------------------------
    // Rule 1 — every enum has at least one value
    // -------------------------------------------------------------------------

    private static void validateEnums(String svcName, ModuleTypeSpec types) {
        for (EnumSpec e : types.enums()) {
            if (e.values().isEmpty()) {
                throw new CodegenException(
                        svcName + ": enum '" + e.name() + "' has no values");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rule 2 — no duplicate Smithy names in the aggregated error-binding list
    // -------------------------------------------------------------------------

    private static void validateErrorBindingNames(String svcName, List<OperationSpec> ops) {
        Map<String, String> seenByName = new LinkedHashMap<>(); // name -> operation
        for (OperationSpec op : ops) {
            if (op.errors() == null) continue;
            for (ErrorBinding eb : op.errors().errors()) {
                String existing = seenByName.put(eb.smithyName(), op.operationName());
                if (existing != null && !existing.equals(op.operationName())) {
                    // Same Smithy name appeared in two different operations — that is fine
                    // (shared errors); but if it appears twice in the *same* operation that
                    // is a double-registration bug in the analyzer.
                }
                // Detect duplicate within the same deduplicated list across operations:
                // We track occurrences by name → first-seen operation. Multiple ops sharing
                // the same error shape is legal. Re-registering the same name twice within
                // the same operation's error list is the bug.
            }
            // Check within a single operation's error list for duplicates.
            Set<String> withinOp = new HashSet<>();
            for (ErrorBinding eb : op.errors().errors()) {
                if (!withinOp.add(eb.smithyName())) {
                    throw new CodegenException(
                            svcName + ": operation '" + op.operationName()
                            + "' has duplicate error binding for '" + eb.smithyName() + "'");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rule 3 — inputTypeName / outputTypeName must resolve in ModuleTypeSpec
    // -------------------------------------------------------------------------

    private static void validateTypeRefs(
            String svcName, ModuleTypeSpec types, List<OperationSpec> ops) {

        Set<String> knownStructs = new HashSet<>();
        for (StructSpec s : types.structures()) knownStructs.add(s.name());
        for (StructSpec e : types.errors())     knownStructs.add(e.name());

        for (OperationSpec op : ops) {
            checkTypeRef(svcName, op, op.inputTypeName(),  "input",  knownStructs);
            checkTypeRef(svcName, op, op.outputTypeName(), "output", knownStructs);
        }
    }

    private static void checkTypeRef(
            String svcName, OperationSpec op, String typeName,
            String direction, Set<String> knownStructs) {
        if (typeName == null || typeName.equals("map")) return;
        if (!knownStructs.contains(typeName)) {
            throw new CodegenException(
                    svcName + ": operation '" + op.operationName()
                    + "' references unknown " + direction + " type '" + typeName + "'");
        }
    }

    // -------------------------------------------------------------------------
    // Rule 4 — NONE-encoding body specs must be empty
    // -------------------------------------------------------------------------

    private static void validateBodySpecs(String svcName, List<OperationSpec> ops) {
        for (OperationSpec op : ops) {
            if (op.body() == null) continue;
            if (op.body().encoding() == BodyEncoding.NONE) {
                if (!op.body().bodyMemberNames().isEmpty()) {
                    throw new CodegenException(
                            svcName + ": operation '" + op.operationName()
                            + "' has encoding=NONE but non-empty bodyMemberNames: "
                            + op.body().bodyMemberNames());
                }
                if (op.body().payloadMember() != null) {
                    throw new CodegenException(
                            svcName + ": operation '" + op.operationName()
                            + "' has encoding=NONE but non-null payloadMember: '"
                            + op.body().payloadMember() + "'");
                }
            }
        }
    }
}
