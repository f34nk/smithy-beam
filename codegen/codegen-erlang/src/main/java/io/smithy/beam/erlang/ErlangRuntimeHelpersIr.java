package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlBinPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMapUpdate;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.List;

final class ErlangRuntimeHelpersIr {
    private ErlangRuntimeHelpersIr() {}

    static ErlModule runtimeHelpersModule(
            String moduleName,
            ServiceShape service,
            Model model,
            boolean awsMetadata,
            boolean labelBindings,
            boolean checksumBindings) {
        List<ErlFunction> functions = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        if (labelBindings) {
            exports.add("parse_labels/2");
            functions.addAll(labelParsingFunctions());
        }
        if (awsMetadata) {
            exports.add("resolve_base_url/1");
            functions.add(resolveBaseUrl());
        }
        if (checksumBindings) {
            exports.addAll(List.of(
                    "headers_set/3", "checksum_header_encode/1", "sha256_hash/1", "crc32_hash/1"));
            functions.addAll(ErlangHttpChecksumIr.checksumHelperFunctions());
        }
        return new ErlModule(
                moduleName,
                List.of(
                        ErlComment.comment("Generated runtime helpers for " + service.getId() + "."),
                        ErlComment.comment("Do not edit.")),
                List.of(ErlExportAttribute.export(exports)),
                functions);
    }

    static List<ErlFunction> labelParsingFunctions() {
        return List.of(parseLabels(), segments(), matchSegments(), labelName());
    }

    static ErlFunction resolveBaseUrl() {
        return ErlFunction.functionWithSpec(
                "resolve_base_url",
                1,
                "map()",
                "binary()",
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Config")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Prefix"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlAtom.atom("endpoint_prefix"),
                                                ErlVar.var("Config"))),
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Region"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlAtom.atom("region"),
                                                ErlVar.var("Config"),
                                                ErlBinary.binary("us-east-1"))),
                                ErlBinaryTemplate.binaryTemplate(
                                        ErlBinaryText.text("https://"),
                                        ErlBinaryExpr.expr(ErlVar.var("Prefix"), true),
                                        ErlBinaryText.text("."),
                                        ErlBinaryExpr.expr(ErlVar.var("Region"), true),
                                        ErlBinaryText.text(".amazonaws.com"))))));
    }

    static ErlFunction parseLabels() {
        return ErlFunction.functionWithSpec(
                "parse_labels",
                2,
                "binary(), binary()",
                "{ok, map()} | {error, path_mismatch}",
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Path"),
                                ErlVarPattern.varPattern("Template")),
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "match_segments",
                                        ErlCallLocal.callLocal("segments", ErlVar.var("Path")),
                                        ErlCallLocal.callLocal("segments", ErlVar.var("Template")),
                                        ErlMap.map()),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("Labels"))),
                                        ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Labels"))),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("error")),
                                        ErlTuple.tuple(
                                                ErlAtom.atom("error"), ErlAtom.atom("path_mismatch")))))));
    }

    static ErlFunction segments() {
        return ErlFunction.function(
                "segments",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Path")),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("Parts"),
                                ErlCall.call(
                                        "binary",
                                        "split",
                                        ErlVar.var("Path"),
                                        ErlBinary.binary("/"),
                                        ErlList.list(ErlAtom.atom("global")))),
                        ErlListComprehension.comprehension(
                                ErlVar.var("S"),
                                ErlVarPattern.varPattern("S"),
                                ErlVar.var("Parts"),
                                ErlOp.op("=/=", ErlVar.var("S"), ErlBinary.binary(""))))));
    }

    static ErlFunction matchSegments() {
        ErlCase segmentMatchCase = ErlCase.caseExpr(
                ErlOp.op("=:=", ErlVar.var("Seg"), ErlVar.var("TplSeg")),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("true")),
                        ErlCallLocal.callLocal(
                                "match_segments",
                                ErlVar.var("RestPath"),
                                ErlVar.var("RestTpl"),
                                ErlVar.var("Acc"))),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("false")),
                        ErlAtom.atom("error")));

        ErlCase labelNameCase = ErlCase.caseExpr(
                ErlCallLocal.callLocal("label_name", ErlVar.var("TplSeg")),
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"),
                                ErlVarPattern.varPattern("Key"))),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Val"),
                                        ErlCall.call("uri_string", "unquote", ErlVar.var("Seg"))),
                                ErlCallLocal.callLocal(
                                        "match_segments",
                                        ErlVar.var("RestPath"),
                                        ErlVar.var("RestTpl"),
                                        ErlMapUpdate.mapUpdate(
                                                ErlVar.var("Acc"),
                                                ErlMapEntry.entry(ErlVar.var("Key"), ErlVar.var("Val")))))),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("error")),
                        segmentMatchCase));

        return ErlFunction.function(
                "match_segments",
                3,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlNilPattern.nilPattern(),
                                        ErlNilPattern.nilPattern(),
                                        ErlVarPattern.varPattern("Acc")),
                                ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Acc"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Seg"),
                                                ErlVarPattern.varPattern("RestPath")),
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("TplSeg"),
                                                ErlVarPattern.varPattern("RestTpl")),
                                        ErlVarPattern.varPattern("Acc")),
                                labelNameCase),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_"),
                                        ErlVarPattern.varPattern("_"),
                                        ErlVarPattern.varPattern("_")),
                                ErlAtom.atom("error"))));
    }

    static ErlFunction labelName() {
        ErlCase splitCase = ErlCase.caseExpr(
                ErlCall.call("binary", "split", ErlVar.var("Rest"), ErlBinary.binary("}")),
                ErlClause.clause(
                        List.of(ErlConsPattern.consPattern(
                                ErlVarPattern.varPattern("Label"),
                                ErlConsPattern.consPattern(
                                        ErlBinaryPattern.binaryPattern(""),
                                        ErlNilPattern.nilPattern()))),
                        ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Label"))),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_")),
                        ErlAtom.atom("error")));

        return ErlFunction.function(
                "label_name",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlBinPattern.binPattern("\"{\", Rest/binary")),
                                splitCase),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("_")),
                                ErlAtom.atom("error"))));
    }
}
