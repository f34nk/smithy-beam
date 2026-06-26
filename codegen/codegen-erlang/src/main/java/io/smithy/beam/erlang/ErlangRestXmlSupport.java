package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ErlangRestXmlSupport {
    private ErlangRestXmlSupport() {}

    static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    static String toBindingVar(String snakeField) {
        return BeamNameUtils.toCamelCaseVariable(snakeField);
    }

    @SafeVarargs
    static <T> List<T> concat(List<T>... lists) {
        List<T> out = new ArrayList<>();
        for (List<T> l : lists) {
            out.addAll(l);
        }
        return out;
    }

    static List<ErlClause> buildResponseErrorDispatchClauses(
            Model model,
            OperationShape op,
            SymbolProvider sp) {
        List<ErlClause> clauses = new ArrayList<>();
        for (ShapeId errorId : op.getErrors()) {
            StructureShape errShape = model.expectShape(errorId, StructureShape.class);
            String recName = recordName(sp.toSymbol(errShape));
            int httpStatus = errShape.hasTrait(HttpErrorTrait.class)
                    ? errShape.expectTrait(HttpErrorTrait.class).getCode()
                    : -1;
            if (httpStatus <= 0) {
                continue;
            }
            clauses.add(ErlClause.clause(
                    List.of(
                            ErlIntegerPattern.integerPattern(httpStatus),
                            ErlVarPattern.varPattern("_Body")),
                    ErlTuple.tuple(ErlAtom.atom("error"), restXmlErrorRecord(errShape, recName))));
        }
        clauses.add(ErlClause.clause(
                List.of(
                        ErlVarPattern.varPattern("Status"),
                        ErlVarPattern.varPattern("Body")),
                ErlCallLocal.callLocal("decode_rest_xml_error", ErlVar.var("Status"), ErlVar.var("Body"))));
        return clauses;
    }

    private static ErlRecord restXmlErrorRecord(StructureShape errShape, String recName) {
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : errShape.members()) {
            if (member.getMemberName().equals("__beam_error_kind")) {
                continue;
            }
            fields.add(ErlRecordField.field(
                    BeamNameUtils.toSnakeCase(member.getMemberName()), ErlAtom.atom("undefined")));
        }
        if (fields.isEmpty()) {
            return ErlRecord.record(recName);
        }
        return ErlRecord.record(recName, fields.toArray(ErlRecordField[]::new));
    }

    static String buildStructureXmlMap(
            Model model, StructureShape structure, String recordVar, String recordTag) {
        List<String> entries = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            if (BeamXmlBindingIndex.isXmlAttribute(member)) {
                continue;
            }
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            String wireName = BeamXmlBindingIndex.memberElementName(member);
            entries.add("<<\"" + wireName + "\">> => " + recordVar + "#" + recordTag + "." + field);
        }
        if (entries.isEmpty()) {
            return "#{}" ;
        }
        return "#{" + String.join(", ", entries) + "}";
    }

    static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
        BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
        for (OperationShape op : ErlangTopDown.containedOperationsSorted(model, service)) {
            if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
                return true;
            }
        }
        return false;
    }

    static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
        return serviceHasHostLabelOperations(model, service)
                || BeamS3CustomizationIndex.of(model).serviceUsesBucketAddressing(service);
    }

    static List<EnumShape> reachableEnumShapes(Model model, ServiceShape service) {
        Set<ShapeId> emitted = new LinkedHashSet<>();
        List<EnumShape> shapes = new ArrayList<>();
        for (Shape shape : new Walker(model).walkShapes(service)) {
            if (shape instanceof EnumShape enumShape && emitted.add(enumShape.getId())) {
                shapes.add(enumShape);
            }
        }
        return shapes;
    }

    static List<IntEnumShape> reachableIntEnumShapes(Model model, ServiceShape service) {
        Set<ShapeId> emitted = new LinkedHashSet<>();
        List<IntEnumShape> shapes = new ArrayList<>();
        for (Shape shape : new Walker(model).walkShapes(service)) {
            if (shape instanceof IntEnumShape intEnumShape && emitted.add(intEnumShape.getId())) {
                shapes.add(intEnumShape);
            }
        }
        return shapes;
    }
}
