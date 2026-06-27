package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpPathPatterns;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlBinPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlMatchPattern;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlPreambleEntry;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlRemoteCall;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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

final class ErlangRouterIr {
  private ErlangRouterIr() {}

  static ErlModule routerModule(
      Model model,
      ServiceShape service,
      BeamErlangLayout layout,
      ShapeId protocol,
      List<OperationShape> operations,
      SymbolProvider sp) {
    String codecMod = layout.serverCodecModuleName(protocol);
    String routerMod = layout.routerModuleName();
    String helpersMod = layout.runtimeHelpersModuleName();
    String serverMod = layout.serverModuleName();

    List<ErlPreambleEntry> preamble;
    List<ErlFunction> functions;
    if (BeamProtocolIds.AWS_JSON_1_0.equals(protocol)
        || BeamProtocolIds.AWS_JSON_1_1.equals(protocol)) {
      preamble =
          List.of(ErlComment.comment("Generated AWS JSON 1.0 router for " + service.getId() + "."));
      functions =
          List.of(awsJsonDispatch(serverMod), awsJsonRoute(service, operations, sp, codecMod));
    } else {
      preamble = List.of(ErlComment.comment("Generated HTTP router for " + service.getId() + "."));
      HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
      functions =
          List.of(
              httpDispatch(serverMod), httpRoute(httpIndex, operations, sp, codecMod, helpersMod));
    }

    return new ErlModule(
        routerMod,
        preamble,
        List.of(
            new ErlAttribute("include", "\"" + layout.typesHeaderFile() + "\""),
            new ErlAttribute("include", "\"" + layout.runtimeTypesHeaderFile() + "\""),
            ErlExportAttribute.export(List.of("dispatch/2"))),
        functions);
  }

  private static ErlFunction httpDispatch(String serverMod) {
    ErlFunctionDoc doc =
        ErlFunctionDoc.functionDoc(
            "Routes an incoming HTTP request to the appropriate server handler.\n"
                + "Handler must export handle_<operation>/3; typically "
                + serverMod
                + " after init_handlers/0.");
    return new ErlFunction(
        "dispatch",
        2,
        doc,
        null,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Handler"),
                    new ErlRecordPattern(
                        "http_request",
                        List.of(
                            ErlRecordFieldPattern.fieldPattern(
                                "method", ErlVarPattern.varPattern("Method")),
                            ErlRecordFieldPattern.fieldPattern(
                                "path", ErlVarPattern.varPattern("Path"))),
                        "Req")),
                ErlCallLocal.callLocal(
                    "route",
                    ErlVar.var("Method"),
                    ErlVar.var("Path"),
                    ErlVar.var("Handler"),
                    ErlVar.var("Req")))));
  }

  private static ErlFunction awsJsonDispatch(String serverMod) {
    return new ErlFunction(
        "dispatch",
        2,
        ErlFunctionDoc.functionDoc("Routes POST / requests by X-Amz-Target header."),
        null,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Handler"),
                    new ErlRecordPattern(
                        "http_request",
                        List.of(
                            ErlRecordFieldPattern.fieldPattern(
                                "method", ErlVarPattern.varPattern("Method")),
                            ErlRecordFieldPattern.fieldPattern(
                                "path", ErlVarPattern.varPattern("Path")),
                            ErlRecordFieldPattern.fieldPattern(
                                "headers", ErlVarPattern.varPattern("Headers"))),
                        "Req")),
                ErlCallLocal.callLocal(
                    "route",
                    ErlVar.var("Method"),
                    ErlVar.var("Path"),
                    ErlVar.var("Headers"),
                    ErlVar.var("Handler"),
                    ErlVar.var("Req")))));
  }

  private static ErlFunction httpRoute(
      HttpBindingIndex httpIndex,
      List<OperationShape> operations,
      SymbolProvider sp,
      String codecMod,
      String helpersMod) {
    List<ErlClause> clauses = new ArrayList<>();
    for (OperationShape op : operations) {
      List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
      clauses.add(routeClause(op, httpIndex, sp, codecMod, helpersMod, !labels.isEmpty()));
    }
    clauses.add(notFoundClause(4));
    return ErlFunction.function("route", 4, clauses);
  }

  private static ErlFunction awsJsonRoute(
      ServiceShape service, List<OperationShape> operations, SymbolProvider sp, String codecMod) {
    String targetPrefix = service.getId().getName();
    List<ErlClause> postClauses = new ArrayList<>();
    for (OperationShape op : operations) {
      String opName = sp.toSymbol(op).getName();
      String handlerFn = "handle_" + opName;
      String amzTarget = targetPrefix + "." + op.getId().getName();
      postClauses.add(
          ErlClause.blockClause(
              List.of(ErlBinaryPattern.binaryPattern(amzTarget)),
              ErlExprBlock.block(
                  ErlMatch.match(
                      ErlVarPattern.varPattern("Input"),
                      ErlRemoteCall.call(
                          ErlAtom.atom(codecMod),
                          "decode_" + opName + "_request",
                          ErlVar.var("Req"))),
                  ErlRemoteCall.call(
                      ErlVar.var("Handler"),
                      handlerFn,
                      ErlMap.map(),
                      ErlVar.var("Input"),
                      ErlMap.map()))));
    }
    postClauses.add(
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("_")),
            ErlTuple.tuple(
                ErlAtom.atom("error"),
                ErlTuple.tuple(
                    ErlAtom.atom("not_found"), ErlBinary.binary("POST"), ErlBinary.binary("/")))));

    ErlCase targetCase =
        ErlCase.caseExpr(
            ErlCall.call(
                "proplists",
                "get_value",
                ErlBinary.binary("X-Amz-Target"),
                ErlVar.var("Headers"),
                ErlAtom.atom("undefined")),
            postClauses.toArray(ErlClause[]::new));

    List<ErlClause> clauses = new ArrayList<>();
    clauses.add(
        ErlClause.blockClause(
            List.of(
                ErlBinaryPattern.binaryPattern("POST"),
                ErlBinaryPattern.binaryPattern("/"),
                ErlVarPattern.varPattern("Headers"),
                ErlVarPattern.varPattern("Handler"),
                ErlVarPattern.varPattern("Req")),
            targetCase));
    clauses.add(notFoundClause(5));
    return ErlFunction.function("route", 5, clauses);
  }

  private static ErlClause routeClause(
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
    ErlPattern pathPattern = pathMatchPattern(uriTemplate, labels);
    List<ErlGuard> guards = trailingLabelGuard(uriTemplate);

    if (labeled) {
      return ErlClause.blockClause(
          List.of(
              ErlBinaryPattern.binaryPattern(method),
              ErlMatchPattern.matchPattern(pathPattern, ErlVarPattern.varPattern("Path")),
              ErlVarPattern.varPattern("Handler"),
              ErlVarPattern.varPattern("Req")),
          guards,
          labeledRouteBody(helpersMod, uriTemplate, codecMod, opName, handlerFn, method));
    }

    return ErlClause.blockClause(
        List.of(
            ErlBinaryPattern.binaryPattern(method),
            ErlMatchPattern.matchPattern(pathPattern, ErlVarPattern.varPattern("Path")),
            ErlVarPattern.varPattern("Handler"),
            ErlVarPattern.varPattern("Req")),
        guards,
        literalRouteBody(codecMod, opName, handlerFn));
  }

  private static ErlCase labeledRouteBody(
      String helpersMod,
      String uriTemplate,
      String codecMod,
      String opName,
      String handlerFn,
      String method) {
    return ErlCase.caseExpr(
        ErlCall.call(helpersMod, "parse_labels", ErlVar.var("Path"), ErlBinary.binary(uriTemplate)),
        ErlClause.blockClause(
            List.of(
                ErlTuplePattern.tuplePattern(
                    ErlAtomPattern.atomPattern("ok"), ErlVarPattern.varPattern("LabelMap"))),
            ErlExprBlock.block(
                ErlMatch.match(
                    ErlVarPattern.varPattern("Input"),
                    ErlRemoteCall.call(
                        ErlAtom.atom(codecMod),
                        "decode_" + opName + "_request",
                        ErlVar.var("Req"),
                        ErlVar.var("LabelMap"))),
                ErlRemoteCall.call(
                    ErlVar.var("Handler"),
                    handlerFn,
                    ErlMap.map(),
                    ErlVar.var("Input"),
                    ErlMap.map()))),
        ErlClause.blockClause(
            List.of(
                ErlTuplePattern.tuplePattern(
                    ErlAtomPattern.atomPattern("error"),
                    ErlAtomPattern.atomPattern("path_mismatch"))),
            ErlTuple.tuple(
                ErlAtom.atom("error"),
                ErlTuple.tuple(
                    ErlAtom.atom("not_found"), ErlBinary.binary(method), ErlVar.var("Path")))));
  }

  private static ErlExprBlock literalRouteBody(String codecMod, String opName, String handlerFn) {
    return ErlExprBlock.block(
        ErlMatch.match(
            ErlVarPattern.varPattern("Input"),
            ErlRemoteCall.call(
                ErlAtom.atom(codecMod), "decode_" + opName + "_request", ErlVar.var("Req"))),
        ErlRemoteCall.call(
            ErlVar.var("Handler"), handlerFn, ErlMap.map(), ErlVar.var("Input"), ErlMap.map()));
  }

  private static ErlClause notFoundClause(int arity) {
    List<ErlPattern> patterns = new ArrayList<>();
    patterns.add(ErlVarPattern.varPattern("Method"));
    patterns.add(ErlVarPattern.varPattern("Path"));
    if (arity == 5) {
      patterns.add(ErlVarPattern.varPattern("_Headers"));
    }
    patterns.add(ErlVarPattern.varPattern("_Handler"));
    patterns.add(ErlVarPattern.varPattern("_Req"));
    return ErlClause.clause(
        patterns,
        ErlTuple.tuple(
            ErlAtom.atom("error"),
            ErlTuple.tuple(ErlAtom.atom("not_found"), ErlVar.var("Method"), ErlVar.var("Path"))));
  }

  private static io.smithy.beam.ir.erlang.ErlPattern pathMatchPattern(
      String uriTemplate, List<HttpBinding> labels) {
    if (labels.isEmpty()) {
      return ErlBinPattern.binPattern("\"" + uriTemplate + "\"");
    }
    return ErlBinPattern.binPattern(buildBinPatternSyntax(uriTemplate));
  }

  private static String buildBinPatternSyntax(String uriTemplate) {
    StringBuilder sb = new StringBuilder();
    int labelIndex = 0;
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate(uriTemplate);
    for (BeamHttpPathPatterns.PathSegment seg : segments) {
      if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
        String var = labelVarName(labelIndex++);
        if (!sb.isEmpty()) {
          sb.append(", ");
        }
        sb.append(var).append("/binary");
      } else {
        sb.append("\"").append(seg.value()).append("\"");
      }
    }
    return sb.toString();
  }

  private static List<ErlGuard> trailingLabelGuard(String uriTemplate) {
    String var = trailingLabelVarName(uriTemplate);
    if (var == null) {
      return List.of();
    }
    return List.of(
        ErlGuard.exprGuard(
            io.smithy.beam.ir.erlang.ErlOp.op("=/=", ErlVar.var(var), ErlBinary.binary(""))));
  }

  private static String labelVarName(int index) {
    return index == 0 ? "NameSeg" : "LabelSeg" + index;
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
