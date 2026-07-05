package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.AndGuard;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BinarySegmentPattern;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.ExpressionGuard;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Guard;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.MatchPattern;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.RecordPatternField;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpPathPatterns;
import io.smithy.beam.core.BeamProtocolIds;
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

  static Module routerModule(
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

    List<String> preambleComments;
    List<Function> functions;
    if (BeamProtocolIds.AWS_JSON_1_0.equals(protocol)
        || BeamProtocolIds.AWS_JSON_1_1.equals(protocol)) {
      preambleComments =
          List.of("Generated AWS JSON 1.0 router for " + service.getId() + ".");
      functions =
          List.of(awsJsonDispatch(serverMod), awsJsonRoute(service, operations, sp, codecMod));
    } else {
      preambleComments = List.of("Generated HTTP router for " + service.getId() + ".");
      HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
      functions =
          List.of(
              httpDispatch(serverMod), httpRoute(httpIndex, operations, sp, codecMod, helpersMod));
    }

    return Module.of(
        routerMod,
        functions,
        preambleComments,
        null,
        List.of(layout.typesHeaderFile(), layout.runtimeTypesHeaderFile()),
        null,
        List.of("dispatch/2"));
  }

  private static Function httpDispatch(String serverMod) {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Handler"),
                    RecordPattern.bind(
                        "Req",
                        "http_request",
                        List.of(
                            RecordPatternField.of("method", VariablePattern.of("Method")),
                            RecordPatternField.of("path", VariablePattern.of("Path"))))),
                LocalCallExpr.of(
                    "route",
                    List.of(
                        Variable.of("Method"),
                        Variable.of("Path"),
                        Variable.of("Handler"),
                        Variable.of("Req"))))),
        null,
        Edoc.of(
            "Routes an incoming HTTP request to the appropriate server handler.\n"
                + "Handler must export handle_<operation>/3; typically "
                + serverMod
                + " after init_handlers/0."),
        null);
  }

  private static Function awsJsonDispatch(String serverMod) {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Handler"),
                    RecordPattern.bind(
                        "Req",
                        "http_request",
                        List.of(
                            RecordPatternField.of("method", VariablePattern.of("Method")),
                            RecordPatternField.of("path", VariablePattern.of("Path")),
                            RecordPatternField.of("headers", VariablePattern.of("Headers"))))),
                LocalCallExpr.of(
                    "route",
                    List.of(
                        Variable.of("Method"),
                        Variable.of("Path"),
                        Variable.of("Headers"),
                        Variable.of("Handler"),
                        Variable.of("Req"))))),
        null,
        Edoc.of("Routes POST / requests by X-Amz-Target header."),
        null);
  }

  private static Function httpRoute(
      HttpBindingIndex httpIndex,
      List<OperationShape> operations,
      SymbolProvider sp,
      String codecMod,
      String helpersMod) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (OperationShape op : operations) {
      List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
      clauses.add(routeClause(op, httpIndex, sp, codecMod, helpersMod, !labels.isEmpty()));
    }
    clauses.add(notFoundClause(4));
    return Function.of("route", clauses);
  }

  private static Function awsJsonRoute(
      ServiceShape service, List<OperationShape> operations, SymbolProvider sp, String codecMod) {
    String targetPrefix = service.getId().getName();
    List<Clause> postClauses = new ArrayList<>();
    for (OperationShape op : operations) {
      String opName = sp.toSymbol(op).getName();
      String handlerFn = "handle_" + opName;
      String amzTarget = targetPrefix + "." + op.getId().getName();
      postClauses.add(
          Clause.of(
              BinaryPattern.of(amzTarget),
              BlockExpr.commaSeparated(
                  List.of(
                      MatchExpr.bind(
                          "Input",
                          RemoteCallExpr.of(
                              codecMod, "decode_" + opName + "_request", List.of(Variable.of("Req"))),
                          RemoteCallExpr.of(
                              Variable.of("Handler"),
                              AtomExpr.of(handlerFn),
                              List.of(
                                  MapExpr.of(List.of()),
                                  Variable.of("Input"),
                                  MapExpr.of(List.of()))))), false)));
    }
    postClauses.add(
        Clause.of(
            WildcardPattern.of(),
            TupleExpr.of(
                List.of(
                    AtomExpr.of("error"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("not_found"),
                            BinaryExpr.of("POST"),
                            BinaryExpr.of("/")))))));

    Expression targetCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "proplists",
                "get_value",
                List.of(
                    BinaryExpr.of("X-Amz-Target"),
                    Variable.of("Headers"),
                    AtomExpr.of("undefined"))),
            postClauses);

    List<FunctionClause> clauses = new ArrayList<>();
    clauses.add(
        FunctionClause.of(
            List.of(
                BinaryPattern.of("POST"),
                BinaryPattern.of("/"),
                VariablePattern.of("Headers"),
                VariablePattern.of("Handler"),
                VariablePattern.of("Req")),
            targetCase));
    clauses.add(notFoundClause(5));
    return Function.of("route", clauses);
  }

  private static FunctionClause routeClause(
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
    Pattern pathPattern = pathMatchPattern(uriTemplate, httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL));
    List<Guard> guards = trailingLabelGuard(uriTemplate);
    Guard guard = guardOrNull(guards);

    List<Pattern> patterns =
        List.of(
            BinaryPattern.of(method),
            MatchPattern.of(pathPattern, VariablePattern.of("Path")),
            VariablePattern.of("Handler"),
            VariablePattern.of("Req"));

    Expression body =
        labeled
            ? labeledRouteBody(helpersMod, uriTemplate, codecMod, opName, handlerFn, method)
            : literalRouteBody(codecMod, opName, handlerFn);

    return FunctionClause.of(patterns, guard, body);
  }

  private static Expression labeledRouteBody(
      String helpersMod,
      String uriTemplate,
      String codecMod,
      String opName,
      String handlerFn,
      String method) {
    return CaseExpr.of(
        RemoteCallExpr.of(
            helpersMod, "parse_labels", List.of(Variable.of("Path"), BinaryExpr.of(uriTemplate))),
        List.of(
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("ok"), VariablePattern.of("LabelMap"))),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bind(
                            "Input",
                            RemoteCallExpr.of(
                                codecMod,
                                "decode_" + opName + "_request",
                                List.of(Variable.of("Req"), Variable.of("LabelMap"))),
                            RemoteCallExpr.of(
                                Variable.of("Handler"),
                                AtomExpr.of(handlerFn),
                                List.of(
                                    MapExpr.of(List.of()),
                                    Variable.of("Input"),
                                    MapExpr.of(List.of()))))), false)),
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("error"), AtomPattern.of("path_mismatch"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("not_found"),
                                BinaryExpr.of(method),
                                Variable.of("Path"))))))));
  }

  private static Expression literalRouteBody(String codecMod, String opName, String handlerFn) {
    return BlockExpr.commaSeparated(
        List.of(
            MatchExpr.bind(
                "Input",
                RemoteCallExpr.of(
                    codecMod, "decode_" + opName + "_request", List.of(Variable.of("Req"))),
                RemoteCallExpr.of(
                    Variable.of("Handler"),
                    AtomExpr.of(handlerFn),
                    List.of(
                        MapExpr.of(List.of()), Variable.of("Input"), MapExpr.of(List.of()))))), false);
  }

  private static FunctionClause notFoundClause(int arity) {
    List<Pattern> patterns = new ArrayList<>();
    patterns.add(VariablePattern.of("Method"));
    patterns.add(VariablePattern.of("Path"));
    if (arity == 5) {
      patterns.add(VariablePattern.of("_Headers"));
    }
    patterns.add(VariablePattern.of("_Handler"));
    patterns.add(VariablePattern.of("_Req"));
    return FunctionClause.of(
        patterns,
        TupleExpr.of(
            List.of(
                AtomExpr.of("error"),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("not_found"),
                        Variable.of("Method"),
                        Variable.of("Path"))))));
  }

  private static Pattern pathMatchPattern(String uriTemplate, List<HttpBinding> labels) {
    if (labels.isEmpty()) {
      return BinaryPattern.of(uriTemplate);
    }
    return BinaryPattern.of(buildBinarySegmentPatterns(uriTemplate));
  }

  private static List<BinarySegmentPattern> buildBinarySegmentPatterns(String uriTemplate) {
    List<BinarySegmentPattern> segments = new ArrayList<>();
    int labelIndex = 0;
    List<BeamHttpPathPatterns.PathSegment> parsed =
        BeamHttpPathPatterns.parseTemplate(uriTemplate);
    for (BeamHttpPathPatterns.PathSegment seg : parsed) {
      if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
        segments.add(
            BinarySegmentPattern.of(VariablePattern.of(labelVarName(labelIndex++)), "binary"));
      } else {
        segments.add(BinarySegmentPattern.literal(seg.value()));
      }
    }
    return segments;
  }

  private static List<Guard> trailingLabelGuard(String uriTemplate) {
    String var = trailingLabelVarName(uriTemplate);
    if (var == null) {
      return List.of();
    }
    return List.of(
        ExpressionGuard.of(InfixExpr.of(Variable.of(var), "=/=", BinaryExpr.of(""))));
  }

  private static Guard guardOrNull(List<Guard> guards) {
    if (guards.isEmpty()) {
      return null;
    }
    if (guards.size() == 1) {
      return guards.get(0);
    }
    return AndGuard.of(guards);
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
