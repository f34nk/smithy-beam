package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.BooleanExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.ConsListPattern;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.ListPattern;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.StructField;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.UseDirective;
import io.beam.dsl.elixir.UseOption;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpComplianceTests;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirComplianceTestIr {
  private ElixirComplianceTestIr() {}

  static Module complianceTestsModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return null;
    }
    List<BeamHttpComplianceTests.OperationRequestTests> requestBindings =
        BeamHttpComplianceTests.requestTestsForService(model, service);
    List<BeamHttpComplianceTests.OperationResponseTests> responseBindings =
        BeamHttpComplianceTests.responseTestsForService(model, service);
    if (requestBindings.isEmpty() && responseBindings.isEmpty()) {
      return null;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.complianceTestsModuleName());
    String clientCodecMod =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String serverCodecMod =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    SymbolProvider sp = ctx.symbolProvider();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    boolean encodeWithConfig =
        ElixirRestJsonSupport.serviceHasHostLabelOperations(model, service)
            || ElixirRestXmlSupport.serviceHasHostLabelOperations(model, service);
    java.util.function.Function<StructureShape, String> structNameFn =
        shape -> ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(shape));

    List<String> testLines = new ArrayList<>();
    for (BeamHttpComplianceTests.OperationRequestTests binding : requestBindings) {
      OperationShape operation = binding.operation();
      Symbol opSym = sp.toSymbol(operation);
      StructureShape input = model.expectShape(operation.getInputShape(), StructureShape.class);
      List<HttpBinding> labels =
          httpIndex.getRequestBindings(operation, HttpBinding.Location.LABEL);

      for (BeamHttpComplianceTests.HttpRequestTestCase testCase :
          BeamHttpComplianceTests.clientRequestTests(binding.cases(), protocol)) {
        addTestLines(
            testLines,
            clientRequestTest(
                model, testCase, opSym, input, clientCodecMod, sp, encodeWithConfig, structNameFn));
      }
      for (BeamHttpComplianceTests.HttpRequestTestCase testCase :
          BeamHttpComplianceTests.serverRequestTests(binding.cases(), protocol)) {
        addTestLines(
            testLines,
            serverRequestTest(
                model,
                testCase,
                opSym,
                input,
                serverCodecMod,
                sp,
                labels,
                hostLabelIndex,
                operation,
                structNameFn));
      }
    }
    for (BeamHttpComplianceTests.OperationResponseTests binding : responseBindings) {
      OperationShape operation = binding.operation();
      Symbol opSym = sp.toSymbol(operation);
      StructureShape outputShape =
          binding
              .errorShape()
              .orElseGet(() -> model.expectShape(operation.getOutputShape(), StructureShape.class));
      boolean errorCase = binding.errorShape().isPresent();

      for (BeamHttpComplianceTests.HttpResponseTestCase testCase :
          BeamHttpComplianceTests.clientResponseTests(binding.cases(), protocol)) {
        addTestLines(
            testLines,
            clientResponseTest(
                model, testCase, opSym, outputShape, clientCodecMod, sp, errorCase, structNameFn));
      }
      for (BeamHttpComplianceTests.HttpResponseTestCase testCase :
          BeamHttpComplianceTests.serverResponseTests(binding.cases(), protocol)) {
        addTestLines(
            testLines,
            serverResponseTest(
                model, testCase, opSym, outputShape, serverCodecMod, sp, errorCase, structNameFn));
      }
    }
    List<Function> helpers = assertionHelperFunctions();

    return new Module(
        moduleName,
        null,
        List.of(
            new UseDirective("ExUnit.Case", List.of(new UseOption("async", BooleanExpr.of(true))))),
        List.of(
            Alias.of(typesMod, "Types"),
            Alias.of(clientCodecMod),
            Alias.of(serverCodecMod, "ServerCodec"),
            Alias.of(runtimeMod, "RuntimeTypes")),
        testLines,
        List.of(),
        List.of(),
        List.of(),
        helpers);
  }

  private static void addTestLines(List<String> target, List<String> testLines) {
    if (!target.isEmpty()) {
      target.add("");
    }
    target.addAll(testLines);
  }

  static List<String> clientRequestTest(
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      boolean encodeWithConfig,
      java.util.function.Function<StructureShape, String> structNameFn) {
    Expression inputLiteral =
        ElixirComplianceLiteralIr.structLiteral(model, input, testCase.params(), sp, structNameFn);
    Expression encodeCall =
        encodeWithConfig
            ? RemoteCallExpr.of(
                codecMod,
                "encode_" + opSym.getName() + "_request",
                List.of(
                    MapExpr.of(List.of(MapEntry.atomKey("region", StringExpr.of("us-east-1")))),
                    inputLiteral))
            : RemoteCallExpr.of(
                codecMod, "encode_" + opSym.getName() + "_request", List.of(inputLiteral));

    List<Expression> body = new ArrayList<>();
    body.add(MatchExpr.bind("request", encodeCall));
    body.add(
        LocalCallExpr.of(
            "assert",
            List.of(
                InfixExpr.of(
                    new DotCallExpr(Variable.of("request"), "method", List.of()),
                    "==",
                    StringExpr.of(testCase.method())))));
    body.add(
        LocalCallExpr.of(
            "assert",
            List.of(
                InfixExpr.of(
                    new DotCallExpr(Variable.of("request"), "path", List.of()),
                    "==",
                    StringExpr.of(testCase.uri())))));
    if (!testCase.queryParams().isEmpty()) {
      body.add(
          LocalCallExpr.of(
              "assert_query_params",
              List.of(
                  ElixirComplianceLiteralIr.queryParamsList(testCase.queryParams()),
                  new DotCallExpr(Variable.of("request"), "query", List.of()))));
    }
    if (!testCase.headers().isEmpty()) {
      body.add(
          LocalCallExpr.of(
              "assert_headers",
              List.of(
                  ElixirComplianceLiteralIr.headersMap(testCase.headers()),
                  new DotCallExpr(Variable.of("request"), "headers", List.of()))));
    }
    if (testCase.body() != null) {
      body.add(
          LocalCallExpr.of(
              "assert",
              List.of(
                  InfixExpr.of(
                      RemoteCallExpr.of(
                          "IO",
                          "iodata_to_binary",
                          List.of(new DotCallExpr(Variable.of("request"), "body", List.of()))),
                      "==",
                      StringExpr.of(testCase.body())))));
    }

    return renderTestLines(escapeElixir(testCase.id()), body);
  }

  static List<String> serverRequestTest(
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      List<HttpBinding> labels,
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      java.util.function.Function<StructureShape, String> structNameFn) {
    Expression requestStruct =
        StructExpr.of(
            "RuntimeTypes.HttpRequest",
            List.of(
                StructField.of("method", StringExpr.of(testCase.method())),
                StructField.of("path", StringExpr.of(testCase.uri())),
                StructField.of(
                    "query",
                    LocalCallExpr.of(
                        "query_params_to_map",
                        List.of(
                            ElixirComplianceLiteralIr.queryParamsList(testCase.queryParams())))),
                StructField.of(
                    "headers",
                    LocalCallExpr.of(
                        "headers_to_list",
                        List.of(ElixirComplianceLiteralIr.headersMap(testCase.headers())))),
                StructField.of("body", ElixirComplianceLiteralIr.optionalBinary(testCase.body()))));

    Expression decodeCall;
    if (labels.isEmpty()) {
      decodeCall =
          RemoteCallExpr.of(
              codecMod, "decode_" + opSym.getName() + "_request", List.of(Variable.of("request")));
    } else {
      decodeCall =
          RemoteCallExpr.of(
              codecMod,
              "decode_" + opSym.getName() + "_request",
              List.of(
                  Variable.of("request"),
                  labelMapExpr(hostLabelIndex, operation, testCase.params())));
    }

    List<Expression> body = new ArrayList<>();
    body.add(MatchExpr.bind("request", requestStruct));
    body.add(MatchExpr.bind("input", decodeCall));
    body.addAll(assertMemberAsserts(model, input, testCase.params(), sp, "input", structNameFn));

    return renderTestLines(escapeElixir(testCase.id()) + " server", body);
  }

  static List<String> clientResponseTest(
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      java.util.function.Function<StructureShape, String> structNameFn) {
    Expression responseStruct =
        StructExpr.of(
            "RuntimeTypes.HttpResponse",
            List.of(
                StructField.of("status", IntegerExpr.of(testCase.code())),
                StructField.of(
                    "headers",
                    LocalCallExpr.of(
                        "headers_to_list",
                        List.of(ElixirComplianceLiteralIr.headersMap(testCase.headers())))),
                StructField.of("body", ElixirComplianceLiteralIr.optionalBinary(testCase.body()))));

    Expression decodeCall =
        RemoteCallExpr.of(
            codecMod, "decode_" + opSym.getName() + "_response", List.of(Variable.of("response")));

    List<Expression> body = new ArrayList<>();
    body.add(MatchExpr.bind("response", responseStruct));
    body.add(
        MatchExpr.bind(
            TuplePattern.of(
                List.of(AtomPattern.of(errorCase ? "error" : "ok"), VariablePattern.of("output"))),
            decodeCall));
    body.addAll(
        assertMemberAsserts(model, outputShape, testCase.params(), sp, "output", structNameFn));

    return renderTestLines(escapeElixir(testCase.id()), body);
  }

  static List<String> serverResponseTest(
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      java.util.function.Function<StructureShape, String> structNameFn) {
    Expression outputLiteral =
        ElixirComplianceLiteralIr.structLiteral(
            model, outputShape, testCase.params(), sp, structNameFn);
    String encodeFn =
        errorCase
            ? "encode_" + structName(sp.toSymbol(outputShape)) + "_response"
            : "encode_" + opSym.getName() + "_response";

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind("response", RemoteCallExpr.of(codecMod, encodeFn, List.of(outputLiteral))));
    body.add(
        LocalCallExpr.of(
            "assert",
            List.of(
                InfixExpr.of(
                    new DotCallExpr(Variable.of("response"), "status", List.of()),
                    "==",
                    IntegerExpr.of(testCase.code())))));
    if (!testCase.headers().isEmpty()) {
      body.add(
          LocalCallExpr.of(
              "assert_headers",
              List.of(
                  ElixirComplianceLiteralIr.headersMap(testCase.headers()),
                  new DotCallExpr(Variable.of("response"), "headers", List.of()))));
    }
    if (testCase.body() != null) {
      body.add(
          LocalCallExpr.of(
              "assert",
              List.of(
                  InfixExpr.of(
                      RemoteCallExpr.of(
                          "IO",
                          "iodata_to_binary",
                          List.of(new DotCallExpr(Variable.of("response"), "body", List.of()))),
                      "==",
                      StringExpr.of(testCase.body())))));
    }

    return renderTestLines(escapeElixir(testCase.id()) + " server", body);
  }

  static List<Expression> assertMemberAsserts(
      Model model,
      StructureShape shape,
      ObjectNode params,
      SymbolProvider sp,
      String structVar,
      java.util.function.Function<StructureShape, String> structNameFn) {
    List<Expression> asserts = new ArrayList<>();
    for (var entry : params.getMembers().entrySet()) {
      String memberName = entry.getKey().getValue();
      shape
          .getMember(memberName)
          .ifPresent(
              member -> {
                String fieldName = BeamNameUtils.toSnakeCase(memberName);
                Expression expected =
                    ElixirComplianceLiteralIr.memberValue(
                        model, member, entry.getValue(), sp, structNameFn);
                asserts.add(
                    LocalCallExpr.of(
                        "assert",
                        List.of(
                            InfixExpr.of(
                                new DotCallExpr(Variable.of(structVar), fieldName, List.of()),
                                "==",
                                expected))));
              });
    }
    return asserts;
  }

  static Expression labelMapExpr(
      BeamHostLabelIndex hostLabelIndex, OperationShape operation, ObjectNode params) {
    return ElixirComplianceLiteralIr.labelMap(hostLabelIndex, operation, params);
  }

  static List<Function> assertionHelperFunctions() {
    List<Function> helpers = new ArrayList<>();
    helpers.add(headersToList());
    helpers.addAll(queryParamsToMap());
    helpers.add(queryParam());
    helpers.add(assertHeaders());
    helpers.add(assertQueryParams());
    return helpers;
  }

  private static List<String> renderTestLines(String name, List<Expression> body) {
    List<String> lines = new ArrayList<>();
    lines.add("test \"" + name + "\" do");
    for (String line : ElixirRenderer.renderStatement(blockBody(body)).split("\n", -1)) {
      lines.add(line.isEmpty() ? "" : "  " + line);
    }
    lines.add("end");
    return lines;
  }

  private static Expression blockBody(List<Expression> statements) {
    return statements.size() == 1 ? statements.get(0) : new BlockExpr(statements);
  }

  private static Function headersToList() {
    return defp(
        "headers_to_list",
        List.of(VariablePattern.of("headers")),
        RemoteCallExpr.of(
            "Enum",
            "map",
            List.of(
                Variable.of("headers"),
                new AnonFun(
                    List.of(
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(
                                    List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                            TupleExpr.of(List.of(Variable.of("k"), Variable.of("v")))))))),
        false);
  }

  private static List<Function> queryParamsToMap() {
    return List.of(
        defp(
            "query_params_to_map", List.of(ListPattern.of(List.of())), MapExpr.of(List.of()), true),
        defp(
            "query_params_to_map",
            List.of(ConsListPattern.of(VariablePattern.of("param"), VariablePattern.of("rest"))),
            RemoteCallExpr.of(
                "Map",
                "merge",
                List.of(
                    LocalCallExpr.of("query_param", List.of(Variable.of("param"))),
                    LocalCallExpr.of("query_params_to_map", List.of(Variable.of("rest"))))),
            false));
  }

  private static Function queryParam() {
    return defp(
        "query_param",
        List.of(VariablePattern.of("param")),
        new CaseExpr(
            splitQueryParam(Variable.of("param")),
            List.of(
                Clause.of(
                    ListPattern.of(List.of(VariablePattern.of("key"), VariablePattern.of("value"))),
                    MapExpr.of(
                        List.of(
                            MapEntry.atomKey("key", Variable.of("key")),
                            MapEntry.atomKey("value", Variable.of("value"))))),
                Clause.of(
                    ListPattern.of(List.of(VariablePattern.of("key"))),
                    MapExpr.of(
                        List.of(
                            MapEntry.atomKey("key", Variable.of("key")),
                            MapEntry.atomKey("value", StringExpr.of(""))))))),
        false);
  }

  private static Function assertHeaders() {
    Expression assertKeywordMatch =
        LocalCallExpr.of(
            "assert",
            List.of(
                InfixExpr.of(
                    RemoteCallExpr.of(
                        "Keyword", "get", List.of(Variable.of("actual"), Variable.of("key"))),
                    "==",
                    Variable.of("value"))));
    AnonFun eachFn =
        new AnonFun(
            List.of(
                AnonFunClause.of(
                    List.of(
                        TuplePattern.of(
                            List.of(VariablePattern.of("key"), VariablePattern.of("value")))),
                    assertKeywordMatch)));
    return defp(
        "assert_headers",
        List.of(VariablePattern.of("expected"), VariablePattern.of("actual")),
        RemoteCallExpr.of("Enum", "each", List.of(Variable.of("expected"), eachFn)),
        true);
  }

  private static Function assertQueryParams() {
    Expression assertFetchMatch =
        LocalCallExpr.of(
            "assert",
            List.of(
                InfixExpr.of(
                    RemoteCallExpr.of(
                        "Map", "fetch!", List.of(Variable.of("query"), Variable.of("key"))),
                    "==",
                    Variable.of("value"))));
    Expression assertHasKey =
        LocalCallExpr.of(
            "assert",
            List.of(
                RemoteCallExpr.of(
                    "Map", "has_key?", List.of(Variable.of("query"), Variable.of("key")))));
    CaseExpr paramCase =
        new CaseExpr(
            splitQueryParam(Variable.of("param")),
            List.of(
                Clause.of(
                    ListPattern.of(List.of(VariablePattern.of("key"), VariablePattern.of("value"))),
                    assertFetchMatch),
                Clause.of(ListPattern.of(List.of(VariablePattern.of("key"))), assertHasKey)));
    AnonFun eachFn =
        new AnonFun(List.of(AnonFunClause.of(List.of(VariablePattern.of("param")), paramCase)));
    return defp(
        "assert_query_params",
        List.of(VariablePattern.of("expected"), VariablePattern.of("query")),
        RemoteCallExpr.of("Enum", "each", List.of(Variable.of("expected"), eachFn)),
        true);
  }

  private static Expression splitQueryParam(Expression param) {
    if (!(param instanceof Variable variable)) {
      throw new IllegalArgumentException("splitQueryParam expects a variable reference");
    }
    return Variable.of("String.split(" + variable.name() + ", \"=\", parts: 2)");
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static String structName(Symbol symbol) {
    return symbol.getName();
  }

  private static String escapeElixir(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
