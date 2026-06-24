package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ErlangRestJsonIr {
    private ErlangRestJsonIr() {}

    static ErlFunction encodeRequest(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            boolean encodeWithConfig,
            String eventStreamModule) {
        return ErlangRestJsonOperationIr.buildEncodeRequest(
                model, service, op, httpIndex, sp, encodeWithConfig, eventStreamModule);
    }

    static ErlFunction decodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String eventStreamModule) {
        return ErlangRestJsonOperationIr.buildDecodeRequest(model, op, httpIndex, sp, eventStreamModule);
    }

    static ErlFunction decodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            BeamErlangLayout layout) {
        return ErlangRestJsonOperationIr.buildDecodeResponse(
                model, service, op, httpIndex, sp, layout);
    }

    static ErlFunction errorDispatch(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return ErlangRestJsonOperationIr.buildErrorDispatch(model, service, op, httpIndex, sp);
    }

    static List<ErlFunction> structureHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        Set<StructureShape> structures = new LinkedHashSet<>();
        Set<StructureShape> listElementStructures = new LinkedHashSet<>();
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        ErlangRestJson1Emitter.collectStructureHelperTargets(
                model, service, httpIndex, structures, listElementStructures);
        for (StructureShape structure : structures) {
            functions.addAll(ErlangStructureHelperIr.structureDecodeEncode(model, httpIndex, structure, sp));
            if (listElementStructures.contains(structure)) {
                functions.addAll(buildStructureListDecodeEncodeFunctions(structure, sp));
            }
        }
        return functions;
    }

    static List<ErlFunction> buildStructureDecodeEncode(
            Model model, HttpBindingIndex httpIndex, StructureShape structure, SymbolProvider sp) {
        return ErlangStructureHelperIr.structureDecodeEncode(model, httpIndex, structure, sp);
    }

    static List<ErlFunction> buildStructureListDecodeEncodeFunctions(StructureShape structure, SymbolProvider sp) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, structure);
        return List.of(
                buildStructureListDecode(helperName),
                buildStructureListEncode(helperName));
    }

    private static ErlFunction buildStructureListDecode(String helperName) {
        return ErlFunction.function(
                "decode_" + helperName + "_list",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("null")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("List")),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("List"))),
                                ErlListComprehension.comprehensionWithFilters(
                                        ErlCallLocal.callLocal("decode_" + helperName, ErlVar.var("V")),
                                        ErlVarPattern.varPattern("V"),
                                        ErlVar.var("List"),
                                        List.of(ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("null")))))));
    }

    private static ErlFunction buildStructureListEncode(String helperName) {
        return ErlFunction.function(
                "encode_" + helperName + "_list",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("List")),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("List"))),
                                ErlListComprehension.comprehensionWithFilters(
                                        ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var("V")),
                                        ErlVarPattern.varPattern("V"),
                                        ErlVar.var("List"),
                                        List.of(ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))))));
    }

    static List<ErlFunction> enumHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        for (EnumShape enumShape : ErlangRestJson1Emitter.reachableEnumShapes(model, service)) {
            functions.addAll(ErlangEnumHelperIr.enumDecodeEncode(enumShape, sp));
        }
        for (IntEnumShape intEnumShape : ErlangRestJson1Emitter.reachableIntEnumShapes(model, service)) {
            functions.addAll(ErlangEnumHelperIr.intEnumDecodeEncode(intEnumShape, sp));
        }
        return functions;
    }

    static List<ErlFunction> unionHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        for (UnionShape union : ErlangRestJson1Emitter.reachableUnionShapes(model, service)) {
            functions.addAll(ErlangUnionHelperIr.unionDecodeEncode(union, sp));
        }
        return functions;
    }

    static void writeFunction(ErlangWriter writer, ErlFunction fn) {
        writer.write("$L", fn.asString());
        writer.write("");
    }

    static void writeFunctions(ErlangWriter writer, List<ErlFunction> functions) {
        for (ErlFunction fn : functions) {
            writeFunction(writer, fn);
        }
    }
}
