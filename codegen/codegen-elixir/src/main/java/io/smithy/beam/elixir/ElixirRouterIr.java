package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpPathPatterns;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExBinaryConcatPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExComment;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPattern;
import io.smithy.beam.ir.elixir.ExPinPattern;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExRemoteCall;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;

final class ElixirRouterIr {
  private ElixirRouterIr() {}

  static ExModule routerModule(
      Model model,
      ServiceShape service,
      BeamElixirLayout layout,
      ShapeId protocol,
      List<OperationShape> operations,
      SymbolProvider sp) {
    String codecMod = ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String routerMod = ElixirSymbolProvider.toModuleName(layout.routerModuleName());
    String helpersMod = ElixirSymbolProvider.toModuleName(layout.runtimeHelpersModuleName());
    String serverMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());

    List<ExPreambleEntry> preamble;
    List<ExFunction> functions;
    if (BeamProtocolIds.AWS_JSON_1_0.equals(protocol)
        || BeamProtocolIds.AWS_JSON_1_1.equals(protocol)) {
      preamble =
          List.of(
              ExModuledoc.moduledoc("Generated AWS JSON router for " + service.getId() + "."),
              ExComment.comment(
                  "Handler must export handle_<operation>/3; typically "
                      + serverMod
                      + " after init_handlers/0."));
      functions = List.of(awsJsonDispatch(), awsJsonRoute(service, operations, sp, codecMod));
    } else {
      preamble =
          List.of(
              ExModuledoc.moduledoc("Generated HTTP router for " + service.getId() + "."),
              ExComment.comment(
                  "Handler must export handle_<operation>/3; typically "
                      + serverMod
                      + " after init_handlers/0."));
      HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
      functions =
          List.of(httpDispatch(), httpRoute(httpIndex, operations, sp, codecMod, helpersMod));
    }

    return ExModule.module(routerMod, preamble, List.of(), functions);
  }

  private static ExFunction httpDispatch() {
    return ExFunction.functionWithSpec(
        "def",
        "dispatch",
        ExSpec.functionSpec("dispatch", "module(), map()", "term()"),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("handler"), ExVarPattern.var("request")),
                ExCallLocal.callLocal(
                    "route",
                    ExStructAccess.structAccess(ExVar.var("request"), "method"),
                    ExStructAccess.structAccess(ExVar.var("request"), "path"),
                    ExVar.var("handler"),
                    ExVar.var("request")))));
  }

  private static ExFunction awsJsonDispatch() {
    return ExFunction.functionWithSpec(
        "def",
        "dispatch",
        ExSpec.functionSpec("dispatch", "module(), map()", "term()"),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("handler"), ExVarPattern.var("request")),
                ExCallLocal.callLocal(
                    "route",
                    ExStructAccess.structAccess(ExVar.var("request"), "method"),
                    ExStructAccess.structAccess(ExVar.var("request"), "path"),
                    ExStructAccess.structAccess(ExVar.var("request"), "headers"),
                    ExVar.var("handler"),
                    ExVar.var("request")))));
  }

  private static ExFunction httpRoute(
      HttpBindingIndex httpIndex,
      List<OperationShape> operations,
      SymbolProvider sp,
      String codecMod,
      String helpersMod) {
    List<ExClause> clauses = new ArrayList<>();
    for (OperationShape op : operations) {
      List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
      clauses.add(routeClause(op, httpIndex, sp, codecMod, helpersMod, !labels.isEmpty()));
    }
    clauses.add(notFoundClause(4));
    return ExFunction.defpFunction("route", clauses);
  }

  private static ExFunction awsJsonRoute(
      ServiceShape service, List<OperationShape> operations, SymbolProvider sp, String codecMod) {
    String targetPrefix = service.getId().getName();
    List<ExCaseBranch> targetBranches = new ArrayList<>();
    for (OperationShape op : operations) {
      String opName = sp.toSymbol(op).getName();
      String handlerFn = "handle_" + opName;
      String amzTarget = targetPrefix + "." + op.getId().getName();
      targetBranches.add(
          ExCaseBranch.branch(
              ExTuplePattern.tuple(ExVarPattern.var("_"), ExStringPattern.string(amzTarget)),
              ExExprBlock.block(
                  ExMatch.match(
                      ExVarPattern.var("input"),
                      ExCall.call(codecMod, "decode_" + opName + "_request", ExVar.var("request"))),
                  ExRemoteCall.call(
                      ExVar.var("handler"),
                      handlerFn,
                      ExMap.map(),
                      ExVar.var("input"),
                      ExMap.map()))));
    }
    targetBranches.add(
        ExCaseBranch.branch(
            ExVarPattern.var("_"),
            ExTuple.tuple(
                ExAtom.atom("error"),
                ExTuple.tuple(ExAtom.atom("not_found"), ExString.string("POST"), ExString.string("/")))));

    ExCase targetCase =
        ExCase.caseExpr(
            ExCall.call(
                "List",
                "keyfind",
                ExVar.var("headers"),
                ExString.string("X-Amz-Target"),
                ExInteger.integer(0)),
            targetBranches.toArray(ExCaseBranch[]::new));

    List<ExClause> clauses = new ArrayList<>();
    clauses.add(
        ExClause.blockClause(
            List.of(
                ExStringPattern.string("POST"),
                ExStringPattern.string("/"),
                ExVarPattern.var("headers"),
                ExVarPattern.var("handler"),
                ExVarPattern.var("request")),
            targetCase));
    clauses.add(notFoundClause(5));
    return ExFunction.defpFunction("route", clauses);
  }

  private static ExClause routeClause(
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String codecMod,
      String helpersMod,
      boolean labeled) {
    HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
    String method = httpTrait.getMethod().toUpperCase();
    String uriTemplate = httpTrait.getUri().toString();
    String opName = sp.toSymbol(op).getName();
    String handlerFn = "handle_" + opName;
    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    ExPattern pathPattern = pathMatchPattern(uriTemplate, labels);
    List<ExGuard> guards = trailingLabelGuard(uriTemplate);

    if (labeled) {
      return ExClause.blockClause(
          List.of(
              ExStringPattern.string(method),
              pathPattern,
              ExVarPattern.var("handler"),
              ExVarPattern.var("request")),
          guards,
          labeledRouteBody(helpersMod, uriTemplate, codecMod, opName, handlerFn, method));
    }

    return ExClause.blockClause(
        List.of(
            ExStringPattern.string(method),
            pathPattern,
            ExVarPattern.var("handler"),
            ExVarPattern.var("request")),
        guards,
        literalRouteBody(codecMod, opName, handlerFn));
  }

  private static ExCase labeledRouteBody(
      String helpersMod,
      String uriTemplate,
      String codecMod,
      String opName,
      String handlerFn,
      String method) {
    return ExCase.caseExpr(
        ExCall.call(helpersMod, "parse_labels", ExVar.var("path"), ExString.string(uriTemplate)),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("label_map")),
            ExExprBlock.block(
                ExMatch.match(
                    ExVarPattern.var("input"),
                    ExCall.call(
                        codecMod,
                        "decode_" + opName + "_request",
                        ExVar.var("request"),
                        ExVar.var("label_map"))),
                ExRemoteCall.call(
                    ExVar.var("handler"),
                    handlerFn,
                    ExMap.map(),
                    ExVar.var("input"),
                    ExMap.map()))),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("error"), ExAtomPattern.atom("path_mismatch")),
            ExTuple.tuple(
                ExAtom.atom("error"),
                ExTuple.tuple(ExAtom.atom("not_found"), ExString.string(method), ExVar.var("path")))));
  }

  private static ExExprBlock literalRouteBody(String codecMod, String opName, String handlerFn) {
    return ExExprBlock.block(
        ExMatch.match(
            ExVarPattern.var("input"),
            ExCall.call(codecMod, "decode_" + opName + "_request", ExVar.var("request"))),
        ExRemoteCall.call(
            ExVar.var("handler"), handlerFn, ExMap.map(), ExVar.var("input"), ExMap.map()));
  }

  private static ExClause notFoundClause(int arity) {
    List<ExPattern> patterns = new ArrayList<>();
    patterns.add(ExVarPattern.var("method"));
    patterns.add(ExVarPattern.var("path"));
    if (arity == 5) {
      patterns.add(ExVarPattern.var("_headers"));
    }
    patterns.add(ExVarPattern.var("_handler"));
    patterns.add(ExVarPattern.var("_request"));
    return ExClause.clause(
        patterns,
        ExTuple.tuple(
            ExAtom.atom("error"),
            ExTuple.tuple(ExAtom.atom("not_found"), ExVar.var("method"), ExVar.var("path"))));
  }

  private static ExPattern pathMatchPattern(String uriTemplate, List<HttpBinding> labels) {
    if (labels.isEmpty()) {
      return ExStringPattern.string(uriTemplate);
    }
    return ExPinPattern.pin(buildPathConcatPattern(uriTemplate), ExVarPattern.var("path"));
  }

  private static ExPattern buildPathConcatPattern(String uriTemplate) {
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate(uriTemplate);
    ExPattern current = null;
    int labelIndex = 0;
    for (BeamHttpPathPatterns.PathSegment seg : segments) {
      ExPattern next;
      if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
        next = ExVarPattern.var(labelVarName(labelIndex++));
      } else {
        next = ExStringPattern.string(seg.value());
      }
      current =
          current == null
              ? next
              : ExBinaryConcatPattern.concat(current, next);
    }
    return current == null ? ExStringPattern.string("") : current;
  }

  private static List<ExGuard> trailingLabelGuard(String uriTemplate) {
    String var = trailingLabelVarName(uriTemplate);
    if (var == null) {
      return List.of();
    }
    return List.of(ExGuard.exprGuard(ExOp.op("!=", ExVar.var(var), ExString.string(""))));
  }

  private static String labelVarName(int index) {
    return index == 0 ? "name_seg" : "label_seg" + index;
  }

  private static String trailingLabelVarName(String uriTemplate) {
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate(uriTemplate);
    if (segments.isEmpty()
        || segments.get(segments.size() - 1).kind() != BeamHttpPathPatterns.SegmentKind.LABEL) {
      return null;
    }
    int labelCount = 0;
    for (BeamHttpPathPatterns.PathSegment seg : segments) {
      if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
        labelCount++;
      }
    }
    return labelVarName(labelCount - 1);
  }
}
