package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AndGuard;
import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BinaryPattern;
import io.beam.dsl.erlang.BinarySegmentPattern;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Edoc;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.ExpressionGuard;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.Guard;
import io.beam.dsl.erlang.InfixExpr;
import io.beam.dsl.erlang.ListComprehensionExpr;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.ListPattern;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapEntry;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.MatchPattern;
import io.beam.dsl.erlang.Module;
import io.beam.dsl.erlang.Pattern;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.RecordPatternField;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.Spec;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
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

final class ErlangRouterDsl {
  private ErlangRouterDsl() {}

  static Module routerModule(
      Model model,
      ServiceShape service,
      BeamErlangLayout layout,
      ShapeId protocol,
      List<OperationShape> operations,
      SymbolProvider sp) {
    String codecMod = layout.codecModuleName(protocol);
    String routerMod = layout.routerModuleName();
    String serverMod = layout.serverModuleName();
    boolean labelBindings = serviceHasLabelBindings(model, operations);

    List<String> preambleComments;
    List<Function> functions = new ArrayList<>();
    if (BeamProtocolIds.AWS_JSON_1_0.equals(protocol)
        || BeamProtocolIds.AWS_JSON_1_1.equals(protocol)) {
      preambleComments = List.of("Generated AWS JSON 1.0 router for " + service.getId() + ".");
      functions.add(awsJsonDispatch(serverMod));
      functions.add(awsJsonRoute(service, operations, sp, codecMod));
    } else {
      preambleComments = List.of("Generated HTTP router for " + service.getId() + ".");
      HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
      functions.add(httpDispatch(serverMod));
      functions.add(httpRoute(httpIndex, operations, sp, codecMod));
    }
    if (labelBindings) {
      functions.addAll(labelParsingFunctions());
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

  static boolean serviceHasLabelBindings(Model model, List<OperationShape> operations) {
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    for (OperationShape op : operations) {
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
        return true;
      }
    }
    return false;
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
                + " after init_handlers/0."));
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
        Edoc.of("Routes POST / requests by X-Amz-Target header."));
  }

  private static Function httpRoute(
      HttpBindingIndex httpIndex,
      List<OperationShape> operations,
      SymbolProvider sp,
      String codecMod) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (OperationShape op : operations) {
      List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
      clauses.add(routeClause(op, httpIndex, sp, codecMod, !labels.isEmpty()));
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
                              codecMod,
                              "decode_" + opName + "_request",
                              List.of(Variable.of("Req"))),
                          RemoteCallExpr.of(
                              Variable.of("Handler"),
                              AtomExpr.of(handlerFn),
                              List.of(
                                  MapExpr.of(List.of()),
                                  Variable.of("Input"),
                                  MapExpr.of(List.of()))))),
                  false)));
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
      boolean labeled) {
    HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
    String method = httpTrait.getMethod().toUpperCase();
    String uriTemplate = httpTrait.getUri().toString();
    String opName = sp.toSymbol(op).getName();
    String handlerFn = "handle_" + opName;
    Pattern pathPattern =
        pathMatchPattern(uriTemplate, httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL));
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
            ? labeledRouteBody(uriTemplate, codecMod, opName, handlerFn, method)
            : literalRouteBody(codecMod, opName, handlerFn);

    return FunctionClause.of(patterns, guard, body);
  }

  private static Expression labeledRouteBody(
      String uriTemplate, String codecMod, String opName, String handlerFn, String method) {
    return CaseExpr.of(
        LocalCallExpr.of("parse_labels", List.of(Variable.of("Path"), BinaryExpr.of(uriTemplate))),
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("LabelMap"))),
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
                                    MapExpr.of(List.of()))))),
                    false)),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), AtomPattern.of("path_mismatch"))),
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
                    List.of(MapExpr.of(List.of()), Variable.of("Input"), MapExpr.of(List.of()))))),
        false);
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
                        AtomExpr.of("not_found"), Variable.of("Method"), Variable.of("Path"))))));
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
    List<BeamHttpPathPatterns.PathSegment> parsed = BeamHttpPathPatterns.parseTemplate(uriTemplate);
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
    return List.of(ExpressionGuard.of(InfixExpr.of(Variable.of(var), "=/=", BinaryExpr.of(""))));
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
                                List.of(AtomPattern.of("ok"), VariablePattern.of("Labels"))),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Labels")))),
                        Clause.of(
                            AtomPattern.of("error"),
                            TupleExpr.of(
                                List.of(AtomExpr.of("error"), AtomExpr.of("path_mismatch")))))))),
        Spec.of("parse_labels(binary(), binary()) -> {ok, map()} | {error, path_mismatch}"));
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
                    false))));
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
                            Variable.of("RestPath"), Variable.of("RestTpl"), Variable.of("Acc")))),
                Clause.of(AtomPattern.of("false"), AtomExpr.of("error"))));

    Expression labelNameCase =
        CaseExpr.of(
            LocalCallExpr.of("label_name", List.of(Variable.of("TplSeg"))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Key"))),
                    BlockExpr.commaSeparated(
                        List.of(
                            MatchExpr.bindValue(
                                "Val",
                                RemoteCallExpr.of(
                                    "uri_string", "unquote", List.of(Variable.of("Seg")))),
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
                AtomExpr.of("error"))));
  }

  static Function labelName() {
    CaseExpr splitCase =
        CaseExpr.of(
            RemoteCallExpr.of("binary", "split", List.of(Variable.of("Rest"), BinaryExpr.of("}"))),
            List.of(
                Clause.of(
                    ListPattern.cons(
                        VariablePattern.of("Label"), ListPattern.of(List.of(BinaryPattern.of("")))),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Label")))),
                Clause.of(WildcardPattern.of(), AtomExpr.of("error"))));

    return Function.of(
        "label_name",
        List.of(
            FunctionClause.of(
                List.of(
                    BinaryPattern.of(
                        List.of(
                            BinarySegmentPattern.literal("{"),
                            BinarySegmentPattern.of(VariablePattern.of("Rest"), "binary")))),
                splitCase),
            FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("error"))));
  }
}
