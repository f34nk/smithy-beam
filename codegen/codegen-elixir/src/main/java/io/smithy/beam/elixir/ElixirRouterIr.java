package io.smithy.beam.elixir;

import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.AssignPattern;
import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.CaptureExpr;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.ComparisonGuard;
import io.beam.ir.elixir.ConcatPattern;
import io.beam.ir.elixir.ConsListPattern;
import io.beam.ir.elixir.DotCallExpr;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.Guard;
import io.beam.ir.elixir.IntegerExpr;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.ListPattern;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MapExpr;
import io.beam.ir.elixir.MatchExpr;
import io.beam.ir.elixir.Moduledoc;
import io.beam.ir.elixir.Module;
import io.beam.ir.elixir.Pattern;
import io.beam.ir.elixir.PipeExpr;
import io.beam.ir.elixir.PipeStep;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.Spec;
import io.beam.ir.elixir.StringExpr;
import io.beam.ir.elixir.StringPattern;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.beam.ir.elixir.WildcardPattern;
import io.smithy.beam.core.BeamElixirLayout;
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

final class ElixirRouterIr {
  private ElixirRouterIr() {}

  static Module routerModule(
      Model model,
      ServiceShape service,
      BeamElixirLayout layout,
      ShapeId protocol,
      List<OperationShape> operations,
      SymbolProvider sp) {
    String codecMod = ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String routerMod = ElixirSymbolProvider.toModuleName(layout.routerModuleName());
    String serverMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());

    Moduledoc moduledoc;
    List<Function> functions = new ArrayList<>();
    if (BeamProtocolIds.AWS_JSON_1_0.equals(protocol)
        || BeamProtocolIds.AWS_JSON_1_1.equals(protocol)) {
      moduledoc = Moduledoc.of("Generated AWS JSON router for " + service.getId() + ".");
      functions.add(awsJsonDispatch());
      functions.addAll(awsJsonRoute(service, operations, sp, codecMod));
    } else {
      moduledoc = Moduledoc.of("Generated HTTP router for " + service.getId() + ".");
      HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
      functions.add(httpDispatch());
      functions.addAll(httpRoute(httpIndex, operations, sp, codecMod));
    }
    return new Module(
        routerMod,
        moduledoc,
        List.of(),
        List.of(),
        List.of(
            "# Handler must export handle_<operation>/3; typically "
                + serverMod
                + " after init_handlers/0."),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  private static Function httpDispatch() {
    return new Function(
        "dispatch",
        false,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("handler"), VariablePattern.of("request")))),
        LocalCallExpr.of(
            "route",
            List.of(
                new DotCallExpr(Variable.of("request"), "method", List.of()),
                new DotCallExpr(Variable.of("request"), "path", List.of()),
                Variable.of("handler"),
                Variable.of("request"))),
        Spec.of("@spec dispatch(module(), map()) :: term()"),
        null,
        false);
  }

  private static Function awsJsonDispatch() {
    return new Function(
        "dispatch",
        false,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("handler"), VariablePattern.of("request")))),
        LocalCallExpr.of(
            "route",
            List.of(
                new DotCallExpr(Variable.of("request"), "method", List.of()),
                new DotCallExpr(Variable.of("request"), "path", List.of()),
                new DotCallExpr(Variable.of("request"), "headers", List.of()),
                Variable.of("handler"),
                Variable.of("request"))),
        Spec.of("@spec dispatch(module(), map()) :: term()"),
        null,
        false);
  }

  private static List<Function> httpRoute(
      HttpBindingIndex httpIndex,
      List<OperationShape> operations,
      SymbolProvider sp,
      String codecMod) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
      functions.add(routeClause(op, httpIndex, sp, codecMod, !labels.isEmpty()));
    }
    functions.add(notFoundFunction(4));
    return functions;
  }

  private static List<Function> awsJsonRoute(
      ServiceShape service, List<OperationShape> operations, SymbolProvider sp, String codecMod) {
    String targetPrefix = service.getId().getName();
    List<Clause> targetBranches = new ArrayList<>();
    for (OperationShape op : operations) {
      String opName = sp.toSymbol(op).getName();
      String handlerFn = "handle_" + opName;
      String amzTarget = targetPrefix + "." + op.getId().getName();
      targetBranches.add(
          Clause.of(
              TuplePattern.of(List.of(WildcardPattern.of(), StringPattern.of(amzTarget))),
              new BlockExpr(
                  List.of(
                      MatchExpr.bind(
                          "input",
                          RemoteCallExpr.of(
                              codecMod,
                              "decode_" + opName + "_request",
                              List.of(Variable.of("request"))),
                          handlerCall(handlerFn))))));
    }
    targetBranches.add(
        Clause.of(
            WildcardPattern.of(),
            TupleExpr.of(
                List.of(
                    AtomExpr.of("error"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("not_found"),
                            StringExpr.of("POST"),
                            StringExpr.of("/")))))));

    Expression targetCase =
        new CaseExpr(
            RemoteCallExpr.of(
                "List",
                "keyfind",
                List.of(
                    Variable.of("headers"),
                    StringExpr.of("X-Amz-Target"),
                    IntegerExpr.of(0))),
            targetBranches);

    List<Function> functions = new ArrayList<>();
    functions.add(
        routeFunction(
            List.of(
                StringPattern.of("POST"),
                StringPattern.of("/"),
                VariablePattern.of("headers"),
                VariablePattern.of("handler"),
                VariablePattern.of("request")),
            null,
            targetCase,
            false));
    functions.add(notFoundFunction(5));
    return functions;
  }

  private static Function routeClause(
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
    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    Pattern pathPattern = pathMatchPattern(uriTemplate, labels);
    Guard guard = trailingLabelGuard(uriTemplate);

    List<Pattern> params =
        List.of(
            StringPattern.of(method),
            pathPattern,
            VariablePattern.of("handler"),
            VariablePattern.of("request"));

    Expression body =
        labeled
            ? labeledRouteBody(uriTemplate, codecMod, opName, handlerFn, method)
            : literalRouteBody(codecMod, opName, handlerFn);

    return routeFunction(params, guard, body, false);
  }

  private static Expression labeledRouteBody(
      String uriTemplate, String codecMod, String opName, String handlerFn, String method) {
    return new CaseExpr(
        LocalCallExpr.of(
            "parse_labels", List.of(Variable.of("path"), StringExpr.of(uriTemplate))),
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("label_map"))),
                new BlockExpr(
                    List.of(
                        MatchExpr.bind(
                            "input",
                            RemoteCallExpr.of(
                                codecMod,
                                "decode_" + opName + "_request",
                                List.of(Variable.of("request"), Variable.of("label_map"))),
                            handlerCall(handlerFn))))),
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("error"), AtomPattern.of("path_mismatch"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("not_found"),
                                StringExpr.of(method),
                                Variable.of("path"))))))));
  }

  private static Expression literalRouteBody(String codecMod, String opName, String handlerFn) {
    return new BlockExpr(
        List.of(
            MatchExpr.bind(
                "input",
                RemoteCallExpr.of(
                    codecMod, "decode_" + opName + "_request", List.of(Variable.of("request"))),
                handlerCall(handlerFn))));
  }

  private static Expression handlerCall(String handlerFn) {
    return new DotCallExpr(
        Variable.of("handler"),
        handlerFn,
        List.of(MapExpr.of(List.of()), Variable.of("input"), MapExpr.of(List.of())));
  }

  private static Function notFoundFunction(int arity) {
    List<Pattern> patterns = new ArrayList<>();
    patterns.add(VariablePattern.of("method"));
    patterns.add(VariablePattern.of("path"));
    if (arity == 5) {
      patterns.add(VariablePattern.of("_headers"));
    }
    patterns.add(VariablePattern.of("_handler"));
    patterns.add(VariablePattern.of("_request"));
    return routeFunction(
        patterns,
        null,
        TupleExpr.of(
            List.of(
                AtomExpr.of("error"),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("not_found"),
                        Variable.of("method"),
                        Variable.of("path"))))),
        true);
  }

  private static Function routeFunction(
      List<Pattern> params, Guard guard, Expression body, boolean oneLiner) {
    if (guard != null) {
      return new Function(
          "route", true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
    }
    return new Function("route", true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Pattern pathMatchPattern(String uriTemplate, List<HttpBinding> labels) {
    if (labels.isEmpty()) {
      return StringPattern.of(uriTemplate);
    }
    return AssignPattern.of(buildPathConcatPattern(uriTemplate), VariablePattern.of("path"));
  }

  private static Pattern buildPathConcatPattern(String uriTemplate) {
    List<BeamHttpPathPatterns.PathSegment> segments =
        BeamHttpPathPatterns.parseTemplate(uriTemplate);
    Pattern current = null;
    int labelIndex = 0;
    for (BeamHttpPathPatterns.PathSegment seg : segments) {
      Pattern next;
      if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
        next = VariablePattern.of(labelVarName(labelIndex++));
      } else {
        next = StringPattern.of(seg.value());
      }
      current = current == null ? next : ConcatPattern.of(current, next);
    }
    return current == null ? StringPattern.of("") : current;
  }

  private static Guard trailingLabelGuard(String uriTemplate) {
    String var = trailingLabelVarName(uriTemplate);
    if (var == null) {
      return null;
    }
    return new ComparisonGuard(Variable.of(var), "!=", StringExpr.of(""));
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

  static boolean serviceHasLabelBindings(Model model, List<OperationShape> operations) {
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    for (OperationShape op : operations) {
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  static List<Function> labelParsingFunctions() {
    List<Function> functions = new ArrayList<>();
    functions.add(parseLabels());
    functions.add(segments());
    functions.addAll(matchSegments());
    functions.addAll(labelName());
    return functions;
  }

  static Function parseLabels() {
    return new Function(
        "parse_labels",
        true,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("path"), VariablePattern.of("template")))),
        new PipeExpr(
            LocalCallExpr.of(
                "match_segments",
                List.of(
                    LocalCallExpr.of("segments", List.of(Variable.of("path"))),
                    LocalCallExpr.of("segments", List.of(Variable.of("template"))),
                    MapExpr.of(List.of()))),
            List.of(
                new PipeStep(
                    CaseExpr.piped(
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(
                                        AtomPattern.of("ok"), VariablePattern.of("labels"))),
                                TupleExpr.of(
                                    List.of(AtomExpr.of("ok"), Variable.of("labels")))),
                            Clause.of(
                                WildcardPattern.of(),
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("error"), AtomExpr.of("path_mismatch")))))),
                    List.of()))),
        Spec.of(
            "@spec parse_labels(String.t(), String.t()) :: {:ok, map()} | {:error, :path_mismatch}"),
        null,
        false);
  }

  static Function segments() {
    return defp(
        "segments",
        List.of(VariablePattern.of("path")),
        new PipeExpr(
            Variable.of("path"),
            List.of(
                new PipeStep(CaptureExpr.of("String.split(\"/\", trim: true)", 1), List.of()))),
        false);
  }

  static List<Function> matchSegments() {
    return List.of(
        defp(
            "match_segments",
            List.of(
                ListPattern.of(List.of()),
                ListPattern.of(List.of()),
                VariablePattern.of("acc")),
            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("acc"))),
            true),
        defp(
            "match_segments",
            List.of(
                ConsListPattern.of(VariablePattern.of("seg"), VariablePattern.of("rest_path")),
                ConsListPattern.of(VariablePattern.of("tpl_seg"), VariablePattern.of("rest_tpl")),
                VariablePattern.of("acc")),
            matchSegmentsConsBody(),
            false),
        defp(
            "match_segments",
            List.of(WildcardPattern.of(), WildcardPattern.of(), WildcardPattern.of()),
            AtomExpr.of("error"),
            true));
  }

  private static Expression matchSegmentsConsBody() {
    return new CaseExpr(
        LocalCallExpr.of("label_name", List.of(Variable.of("tpl_seg"))),
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("key"))),
                MatchExpr.bind(
                    "val",
                    RemoteCallExpr.of("URI", "decode", List.of(Variable.of("seg"))),
                    LocalCallExpr.of(
                        "match_segments",
                        List.of(
                            Variable.of("rest_path"),
                            Variable.of("rest_tpl"),
                            RemoteCallExpr.of(
                                "Map",
                                "put",
                                List.of(
                                    Variable.of("acc"),
                                    Variable.of("key"),
                                    Variable.of("val"))))))),
            Clause.of(
                WildcardPattern.of(),
                new ComparisonGuard(Variable.of("seg"), "==", Variable.of("tpl_seg")),
                LocalCallExpr.of(
                    "match_segments",
                    List.of(
                        Variable.of("rest_path"),
                        Variable.of("rest_tpl"),
                        Variable.of("acc")))),
            Clause.of(WildcardPattern.of(), AtomExpr.of("error"))));
  }

  static List<Function> labelName() {
    return List.of(
        defp(
            "label_name",
            List.of(
                AssignPattern.of(
                    ConcatPattern.of(StringPattern.of("{"), VariablePattern.of("rest")),
                    VariablePattern.of("tpl_seg"))),
            new CaseExpr(
                RemoteCallExpr.of(
                    "String",
                    "split",
                    List.of(
                        Variable.of("rest"),
                        StringExpr.of("}"),
                        ListExpr.of(
                            List.of(
                                TupleExpr.of(
                                    List.of(AtomExpr.of("parts"), IntegerExpr.of(2))))))),
                List.of(
                    Clause.of(
                        ListPattern.of(
                            List.of(VariablePattern.of("label"), StringPattern.of(""))),
                        TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("label")))),
                    Clause.of(WildcardPattern.of(), AtomExpr.of("error")))),
            false),
        defp("label_name", List.of(WildcardPattern.of()), AtomExpr.of("error"), true));
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }
}
