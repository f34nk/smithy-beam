package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExBinaryConcatPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirRuntimeHelpersIr {
  private ElixirRuntimeHelpersIr() {}

  static ExModule runtimeHelpersModule(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), service.getId().getNamespace());
    String moduleName = ElixirSymbolProvider.toModuleName(layout.runtimeHelpersModuleName());
    boolean awsMetadata = BeamAwsServiceMetadata.from(service).isPresent();
    boolean labelBindings =
        ElixirRuntimeHelpersEmitter.serviceHasLabelBindings(ctx.model(), service);

    List<ExFunction> functions = new ArrayList<>();
    if (awsMetadata) {
      functions.add(resolveBaseUrl());
    }
    if (labelBindings) {
      functions.addAll(labelParsingFunctions());
    }

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Generated runtime helpers for " + service.getId() + ". Do not edit.")),
        List.of(),
        functions);
  }

  static List<ExFunction> labelParsingFunctions() {
    return List.of(parseLabels(), segments(), matchSegments(), labelName());
  }

  static ExFunction resolveBaseUrl() {
    return ExFunction.functionWithSpec(
        "def",
        "resolve_base_url",
        ExSpec.functionSpec("resolve_base_url", "map()", "String.t()"),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExExprBlock.block(
                    ExMatch.match(
                        ExVarPattern.var("prefix"),
                        ExCall.call(
                            "Map", "fetch!", ExVar.var("config"), ExAtom.atom("endpoint_prefix"))),
                    ExMatch.match(
                        ExVarPattern.var("region"),
                        ExCall.call(
                            "Map",
                            "get",
                            ExVar.var("config"),
                            ExAtom.atom("region"),
                            ExString.string("us-east-1"))),
                    ExCapturedBlock.capturedBlock(
                        "\"https://#{prefix}.#{region}.amazonaws.com\"")))));
  }

  static ExFunction parseLabels() {
    return ExFunction.functionWithSpec(
        "def",
        "parse_labels",
        ExSpec.functionSpec(
            "parse_labels", "String.t(), String.t()", "{:ok, map()} | {:error, :path_mismatch}"),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("path"), ExVarPattern.var("template")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "match_segments",
                        ExCallLocal.callLocal("segments", ExVar.var("path")),
                        ExCallLocal.callLocal("segments", ExVar.var("template")),
                        ExMap.map()),
                    List.of(
                        ExCaseBranch.branch(
                            ExTuplePattern.tuple(
                                ExAtomPattern.atom("ok"), ExVarPattern.var("labels")),
                            ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("labels"))),
                        ExCaseBranch.branch(
                            ExVarPattern.var("_"),
                            ExTuple.tuple(ExAtom.atom("error"), ExAtom.atom("path_mismatch")))),
                    true))));
  }

  static ExFunction segments() {
    return ExFunction.defpFunction(
        "segments",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("path")),
                ExPipeline.pipeChain(
                    ExVar.var("path"),
                    ExCapturedBlock.capturedBlock("String.split(\"/\", trim: true)")))));
  }

  static ExFunction matchSegments() {
    return ExFunction.defpFunction(
        "match_segments",
        List.of(
            ExClause.inlineClause(
                List.of(ExListPattern.list(), ExListPattern.list(), ExVarPattern.var("acc")),
                ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("acc"))),
            ExClause.blockClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("seg"), ExVarPattern.var("rest_path")),
                    ExConsPattern.consPattern(
                        ExVarPattern.var("tpl_seg"), ExVarPattern.var("rest_tpl")),
                    ExVarPattern.var("acc")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal("label_name", ExVar.var("tpl_seg")),
                    List.of(
                        ExCaseBranch.branch(
                            ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("key")),
                            ExExprBlock.block(
                                ExMatch.match(
                                    ExVarPattern.var("val"),
                                    ExCall.call("URI", "decode", ExVar.var("seg"))),
                                ExCallLocal.callLocal(
                                    "match_segments",
                                    ExVar.var("rest_path"),
                                    ExVar.var("rest_tpl"),
                                    ExCall.call(
                                        "Map",
                                        "put",
                                        ExVar.var("acc"),
                                        ExVar.var("key"),
                                        ExVar.var("val"))))),
                        ExCaseBranch.branch(
                            ExVarPattern.var("_"),
                            List.of(
                                ExGuard.exprGuard(
                                    ExOp.op("==", ExVar.var("seg"), ExVar.var("tpl_seg")))),
                            ExCallLocal.callLocal(
                                "match_segments",
                                ExVar.var("rest_path"),
                                ExVar.var("rest_tpl"),
                                ExVar.var("acc"))),
                        ExCaseBranch.branch(ExVarPattern.var("_"), ExAtom.atom("error"))),
                    true)),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_"), ExVarPattern.var("_"), ExVarPattern.var("_")),
                ExAtom.atom("error"))));
  }

  static ExFunction labelName() {
    return ExFunction.defpFunction(
        "label_name",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExBinaryConcatPattern.concat(
                        ExStringPattern.string("{"), ExVarPattern.var("rest"))),
                ExCase.caseExpr(
                    ExCall.call(
                        "String",
                        "split",
                        ExVar.var("rest"),
                        ExString.string("}"),
                        ExCapturedBlock.capturedBlock("parts: 2")),
                    ExCaseBranch.branch(
                        ExListPattern.list(ExVarPattern.var("label"), ExStringPattern.string("")),
                        ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("label"))),
                    ExCaseBranch.branch(ExVarPattern.var("_"), ExAtom.atom("error")))),
            ExClause.inlineClause(List.of(ExVarPattern.var("_")), ExAtom.atom("error"))));
  }
}
