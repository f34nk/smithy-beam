package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.ListPattern;
import io.beam.ir.erlang.OpaquePattern;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.WildcardPattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangRuntimeHelpersIr {
  private ErlangRuntimeHelpersIr() {}

  static Module runtimeHelpersModule(
      String moduleName,
      ServiceShape service,
      Model model,
      boolean labelBindings) {
    List<Function> functions = new ArrayList<>();
    List<String> exports = new ArrayList<>();
    if (labelBindings) {
      exports.add("parse_labels/2");
      functions.addAll(labelParsingFunctions());
    }
    return Module.of(
        moduleName,
        functions,
        List.of(
            "Generated runtime helpers for " + service.getId() + ".",
            "Do not edit."),
        null,
        null,
        null,
        exports);
  }

  static List<Function> labelParsingFunctions() {
    return List.of(parseLabels(), segments(), matchSegments(), labelName());
  }

  static Function parseLabels() {
    return Function.of(
        "parse_labels",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Path"), VariablePattern.of("Template")),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "match_segments",
                        List.of(
                            LocalCallExpr.of("segments", List.of(Variable.of("Path"))),
                            LocalCallExpr.of("segments", List.of(Variable.of("Template"))),
                            MapExpr.of(List.of()))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(
                                    AtomPattern.of("ok"), VariablePattern.of("Labels"))),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Labels")))),
                        Clause.of(
                            AtomPattern.of("error"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("error"), AtomExpr.of("path_mismatch")))))))),
        Spec.of("parse_labels(binary(), binary()) -> {ok, map()} | {error, path_mismatch}"),
        null,
        null);
  }

  static Function segments() {
    return Function.of(
        "segments",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Path")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "Parts",
                            RemoteCallExpr.of(
                                "binary",
                                "split",
                                List.of(
                                    Variable.of("Path"),
                                    BinaryExpr.of("/"),
                                    ListExpr.of(List.of(AtomExpr.of("global")))))),
                        ListComprehensionExpr.of(
                            Variable.of("S"),
                            VariablePattern.of("S"),
                            Variable.of("Parts"),
                            InfixExpr.of(Variable.of("S"), "=/=", BinaryExpr.of("")))),
                    false))),
        null,
        null,
        null);
  }

  static Function matchSegments() {
    Expression segmentMatchCase =
        CaseExpr.of(
            InfixExpr.of(Variable.of("Seg"), "=:=", Variable.of("TplSeg")),
            List.of(
                Clause.of(
                    AtomPattern.of("true"),
                    LocalCallExpr.of(
                        "match_segments",
                        List.of(
                            Variable.of("RestPath"),
                            Variable.of("RestTpl"),
                            Variable.of("Acc")))),
                Clause.of(AtomPattern.of("false"), AtomExpr.of("error"))));

    Expression labelNameCase =
        CaseExpr.of(
            LocalCallExpr.of("label_name", List.of(Variable.of("TplSeg"))),
            List.of(
                Clause.of(
                    TuplePattern.of(
                        List.of(AtomPattern.of("ok"), VariablePattern.of("Key"))),
                    BlockExpr.commaSeparated(
                        List.of(
                            MatchExpr.bindValue(
                                "Val",
                                RemoteCallExpr.of(
                                    "uri_string",
                                    "unquote",
                                    List.of(Variable.of("Seg")))),
                            LocalCallExpr.of(
                                "match_segments",
                                List.of(
                                    Variable.of("RestPath"),
                                    Variable.of("RestTpl"),
                                    MapExpr.of(
                                        Variable.of("Acc"),
                                        List.of(
                                            MapEntry.of(
                                                Variable.of("Key"), Variable.of("Val"))))))),
                        false)),
                Clause.of(AtomPattern.of("error"), segmentMatchCase)));

    return Function.of(
        "match_segments",
        List.of(
            FunctionClause.of(
                List.of(
                    ListPattern.of(List.of()),
                    ListPattern.of(List.of()),
                    VariablePattern.of("Acc")),
                TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Acc")))),
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("Seg"), VariablePattern.of("RestPath")),
                    ListPattern.cons(VariablePattern.of("TplSeg"), VariablePattern.of("RestTpl")),
                    VariablePattern.of("Acc")),
                labelNameCase),
            FunctionClause.of(
                List.of(WildcardPattern.of(), WildcardPattern.of(), WildcardPattern.of()),
                AtomExpr.of("error"))),
        null,
        null,
        null);
  }

  static Function labelName() {
    CaseExpr splitCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "binary",
                "split",
                List.of(Variable.of("Rest"), BinaryExpr.of("}"))),
            List.of(
                Clause.of(
                    ListPattern.cons(
                        VariablePattern.of("Label"),
                        ListPattern.of(List.of(BinaryPattern.of("")))),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Label")))),
                Clause.of(WildcardPattern.of(), AtomExpr.of("error"))));

    return Function.of(
        "label_name",
        List.of(
            FunctionClause.of(
                List.of(OpaquePattern.of("<<\"{\", Rest/binary>>")), splitCase),
            FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("error"))),
        null,
        null,
        null);
  }
}
