package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpComplianceTests;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExBlankBodyLine;
import io.smithy.beam.ir.elixir.ExBlankModuleAttr;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMacroCall;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExUseAttr;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import io.smithy.beam.ir.elixir.IrObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirComplianceTestIr {
  private ElixirComplianceTestIr() {}

  static ExModule complianceTestsModule(ElixirContext ctx, ServiceShape service) {
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
    Function<StructureShape, String> structNameFn =
        shape -> ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(shape));

    List<ExFunction> tests = new ArrayList<>();
    for (BeamHttpComplianceTests.OperationRequestTests binding : requestBindings) {
      OperationShape operation = binding.operation();
      Symbol opSym = sp.toSymbol(operation);
      StructureShape input =
          model.expectShape(operation.getInputShape(), StructureShape.class);
      List<HttpBinding> labels =
          httpIndex.getRequestBindings(operation, HttpBinding.Location.LABEL);

      for (BeamHttpComplianceTests.HttpRequestTestCase testCase :
          BeamHttpComplianceTests.clientRequestTests(binding.cases(), protocol)) {
        tests.add(
            clientRequestTest(
                model,
                testCase,
                opSym,
                input,
                clientCodecMod,
                sp,
                encodeWithConfig,
                structNameFn));
      }
      for (BeamHttpComplianceTests.HttpRequestTestCase testCase :
          BeamHttpComplianceTests.serverRequestTests(binding.cases(), protocol)) {
        tests.add(
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
              .orElseGet(
                  () -> model.expectShape(operation.getOutputShape(), StructureShape.class));
      boolean errorCase = binding.errorShape().isPresent();

      for (BeamHttpComplianceTests.HttpResponseTestCase testCase :
          BeamHttpComplianceTests.clientResponseTests(binding.cases(), protocol)) {
        tests.add(
            clientResponseTest(
                model,
                testCase,
                opSym,
                outputShape,
                clientCodecMod,
                sp,
                errorCase,
                structNameFn));
      }
      for (BeamHttpComplianceTests.HttpResponseTestCase testCase :
          BeamHttpComplianceTests.serverResponseTests(binding.cases(), protocol)) {
        tests.add(
            serverResponseTest(
                model,
                testCase,
                opSym,
                outputShape,
                serverCodecMod,
                sp,
                errorCase,
                structNameFn));
      }
    }
    tests.addAll(assertionHelperFunctions());

    return ExModule.module(
        moduleName,
        List.of(),
        List.of(
            ExUseAttr.use("ExUnit.Case", "async: true"),
            ExBlankModuleAttr.blankLine(),
            ExAliasAttr.alias(typesMod, "Types"),
            ExAliasAttr.alias(clientCodecMod),
            ExAliasAttr.alias(serverCodecMod, "ServerCodec"),
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes")),
        tests);
  }

  static ExFunction clientRequestTest(
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      boolean encodeWithConfig,
      Function<StructureShape, String> structNameFn) {
    ExExpr inputLiteral =
        ElixirComplianceLiteralIr.structLiteral(
            model, input, testCase.params(), sp, structNameFn);
    ExExpr encodeCall =
        encodeWithConfig
            ? ExCall.call(
                codecMod,
                "encode_" + opSym.getName() + "_request",
                ExMap.map(
                    ExMapEntry.entry(ExAtom.atom("region"), ExString.string("us-east-1"))),
                inputLiteral)
            : ExCall.call(codecMod, "encode_" + opSym.getName() + "_request", inputLiteral);

    List<ExExpr> body = new ArrayList<>();
    body.add(ExMatch.match(ExVarPattern.var("request"), encodeCall));
    body.add(
        ExMacroCall.assertExpr(
            ExOp.op(
                "==",
                ExStructAccess.structAccess(ExVar.var("request"), "method"),
                ExString.string(testCase.method()))));
    body.add(
        ExMacroCall.assertExpr(
            ExOp.op(
                "==",
                ExStructAccess.structAccess(ExVar.var("request"), "path"),
                ExString.string(testCase.uri()))));
    if (!testCase.queryParams().isEmpty()) {
      body.add(
          ExCallLocal.callLocal(
              "assert_query_params",
              ElixirComplianceLiteralIr.queryParamsList(testCase.queryParams()),
              ExStructAccess.structAccess(ExVar.var("request"), "query")));
    }
    if (!testCase.headers().isEmpty()) {
      body.add(
          ExCallLocal.callLocal(
              "assert_headers",
              ElixirComplianceLiteralIr.headersMap(testCase.headers()),
              ExStructAccess.structAccess(ExVar.var("request"), "headers")));
    }
    if (testCase.body() != null) {
      body.add(
          ExMacroCall.assertExpr(
              ExOp.op(
                  "==",
                  ExCall.call(
                      "IO",
                      "iodata_to_binary",
                      ExStructAccess.structAccess(ExVar.var("request"), "body")),
                  ExString.string(testCase.body()))));
    }

    return new ExFunction(
        "test",
        "\"" + escapeElixir(testCase.id()) + "\"",
        null,
        null,
        List.of(ExClause.blockClause(List.of(), body.toArray(ExExpr[]::new))));
  }

  static ExFunction serverRequestTest(
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      List<HttpBinding> labels,
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      Function<StructureShape, String> structNameFn) {
    ExStruct requestStruct =
        ExStruct.struct(
            "RuntimeTypes.HttpRequest",
            ExMapEntry.entry(ExAtom.atom("method"), ExString.string(testCase.method())),
            ExMapEntry.entry(ExAtom.atom("path"), ExString.string(testCase.uri())),
            ExMapEntry.entry(
                ExAtom.atom("query"),
                ExCallLocal.callLocal(
                    "query_params_to_map",
                    ElixirComplianceLiteralIr.queryParamsList(testCase.queryParams()))),
            ExMapEntry.entry(
                ExAtom.atom("headers"),
                ExCallLocal.callLocal(
                    "headers_to_list",
                    ElixirComplianceLiteralIr.headersMap(testCase.headers()))),
            ExMapEntry.entry(
                ExAtom.atom("body"),
                ElixirComplianceLiteralIr.optionalBinary(testCase.body())));

    ExExpr decodeCall;
    if (labels.isEmpty()) {
      decodeCall =
          ExCall.call(
              codecMod,
              "decode_" + opSym.getName() + "_request",
              ExVar.var("request"));
    } else {
      decodeCall =
          ExCall.call(
              codecMod,
              "decode_" + opSym.getName() + "_request",
              ExVar.var("request"),
              labelMapExpr(hostLabelIndex, operation, testCase.params()));
    }

    List<ExExpr> body = new ArrayList<>();
    body.add(structAssign("request", requestStruct));
    body.add(ExBlankBodyLine.blankLine());
    body.add(ExMatch.match(ExVarPattern.var("input"), decodeCall));
    body.addAll(
        assertMemberAsserts(model, input, testCase.params(), sp, "input", structNameFn));

    return new ExFunction(
        "test",
        "\"" + escapeElixir(testCase.id()) + " server\"",
        null,
        null,
        List.of(ExClause.blockClause(List.of(), body.toArray(ExExpr[]::new))));
  }

  static ExFunction clientResponseTest(
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      Function<StructureShape, String> structNameFn) {
    ExStruct responseStruct =
        ExStruct.struct(
            "RuntimeTypes.HttpResponse",
            ExMapEntry.entry(ExAtom.atom("status"), ExInteger.integer(testCase.code())),
            ExMapEntry.entry(
                ExAtom.atom("headers"),
                ExCallLocal.callLocal(
                    "headers_to_list",
                    ElixirComplianceLiteralIr.headersMap(testCase.headers()))),
            ExMapEntry.entry(
                ExAtom.atom("body"),
                ElixirComplianceLiteralIr.optionalBinary(testCase.body())));

    ExExpr decodeCall =
        ExCall.call(
            codecMod, "decode_" + opSym.getName() + "_response", ExVar.var("response"));

    List<ExExpr> body = new ArrayList<>();
    body.add(structAssign("response", responseStruct));
    body.add(ExBlankBodyLine.blankLine());
    body.add(
        ExMatch.match(
            ExTuplePattern.tuple(
                ExAtomPattern.atom(errorCase ? "error" : "ok"), ExVarPattern.var("output")),
            decodeCall));
    body.addAll(
        assertMemberAsserts(model, outputShape, testCase.params(), sp, "output", structNameFn));

    return new ExFunction(
        "test",
        "\"" + escapeElixir(testCase.id()) + "\"",
        null,
        null,
        List.of(ExClause.blockClause(List.of(), body.toArray(ExExpr[]::new))));
  }

  static ExFunction serverResponseTest(
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      Function<StructureShape, String> structNameFn) {
    ExExpr outputLiteral =
        ElixirComplianceLiteralIr.structLiteral(
            model, outputShape, testCase.params(), sp, structNameFn);
    String encodeFn =
        errorCase
            ? "encode_" + structName(sp.toSymbol(outputShape)) + "_response"
            : "encode_" + opSym.getName() + "_response";

    List<ExExpr> body = new ArrayList<>();
    body.add(
        ExMatch.match(
            ExVarPattern.var("response"),
            ExCall.call(codecMod, encodeFn, outputLiteral)));
    body.add(
        ExMacroCall.assertExpr(
            ExOp.op(
                "==",
                ExStructAccess.structAccess(ExVar.var("response"), "status"),
                ExInteger.integer(testCase.code()))));
    if (!testCase.headers().isEmpty()) {
      body.add(
          ExCallLocal.callLocal(
              "assert_headers",
              ElixirComplianceLiteralIr.headersMap(testCase.headers()),
              ExStructAccess.structAccess(ExVar.var("response"), "headers")));
    }
    if (testCase.body() != null) {
      body.add(
          ExMacroCall.assertExpr(
              ExOp.op(
                  "==",
                  ExCall.call(
                      "IO",
                      "iodata_to_binary",
                      ExStructAccess.structAccess(ExVar.var("response"), "body")),
                  ExString.string(testCase.body()))));
    }

    return new ExFunction(
        "test",
        "\"" + escapeElixir(testCase.id()) + " server\"",
        null,
        null,
        List.of(ExClause.blockClause(List.of(), body.toArray(ExExpr[]::new))));
  }

  static List<ExExpr> assertMemberAsserts(
      Model model,
      StructureShape shape,
      ObjectNode params,
      SymbolProvider sp,
      String structVar,
      Function<StructureShape, String> structNameFn) {
    List<ExExpr> asserts = new ArrayList<>();
    for (var entry : params.getMembers().entrySet()) {
      String memberName = entry.getKey().getValue();
      shape
          .getMember(memberName)
          .ifPresent(
              member -> {
                String fieldName = BeamNameUtils.toSnakeCase(memberName);
                ExExpr expected =
                    ElixirComplianceLiteralIr.memberValue(
                        model, member, entry.getValue(), sp, structNameFn);
                asserts.add(
                    ExMacroCall.assertExpr(
                        ExOp.op(
                            "==",
                            ExStructAccess.structAccess(ExVar.var(structVar), fieldName),
                            expected)));
              });
    }
    return asserts;
  }

  static ExExpr labelMapExpr(
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      ObjectNode params) {
    return ElixirComplianceLiteralIr.labelMap(hostLabelIndex, operation, params);
  }

  static List<ExFunction> assertionHelperFunctions() {
    return List.of(
        headersToList(), queryParamsToMap(), queryParam(), assertHeaders(), assertQueryParams());
  }

  private static ExFunction headersToList() {
    return ExFunction.defpFunction(
        "headers_to_list",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers")),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("headers"),
                    ExAnonymousFn.compactFn(
                        ExClause.blockClause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("k"), ExVarPattern.var("v"))),
                            ExTuple.tuple(ExVar.var("k"), ExVar.var("v"))))))));
  }

  private static ExFunction queryParamsToMap() {
    return ExFunction.defpFunction(
        "query_params_to_map",
        List.of(
            ExClause.inlineClause(List.of(ExListPattern.list()), ExMap.map()),
            ExClause.blockClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("param"), ExVarPattern.var("rest"))),
                ExCall.call(
                    "Map",
                    "merge",
                    ExCallLocal.callLocal("query_param", ExVar.var("param")),
                    ExCallLocal.callLocal("query_params_to_map", ExVar.var("rest"))))));
  }

  private static ExFunction queryParam() {
    return ExFunction.defpFunction(
        "query_param",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("param")),
                ExCase.caseExpr(
                    ExCapturedBlock.capturedBlock("String.split(param, \"=\", parts: 2)"),
                    ExCaseBranch.branch(
                        ExListPattern.list(
                            ExVarPattern.var("key"), ExVarPattern.var("value")),
                        ExMap.map(
                            ExMapEntry.entry(ExVar.var("key"), ExVar.var("value")))),
                    ExCaseBranch.branch(
                        ExListPattern.list(ExVarPattern.var("key")),
                        ExMap.map(
                            ExMapEntry.entry(ExVar.var("key"), ExString.string(""))))))));
  }

  private static ExFunction assertHeaders() {
    return ExFunction.defpFunction(
        "assert_headers",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("expected"), ExVarPattern.var("actual")),
                ExCapturedBlock.capturedBlock(
                    """
                    Enum.each(expected, fn {key, value} ->
                      assert Keyword.get(actual, key) == value
                    end)"""))));
  }

  private static ExFunction assertQueryParams() {
    return ExFunction.defpFunction(
        "assert_query_params",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("expected"), ExVarPattern.var("query")),
                ExCapturedBlock.capturedBlock(
                    """
                    Enum.each(expected, fn param ->
                      case String.split(param, "=", parts: 2) do
                        [key, value] -> assert Map.fetch!(query, key) == value
                        [key] -> assert Map.has_key?(query, key)
                      end
                    end)"""))));
  }

  private static ExExpr structAssign(String varName, ExStruct struct) {
    List<String> lines = struct.lines(1);
    StringBuilder sb = new StringBuilder();
    sb.append(varName).append(" = ").append(stripIndentStep(lines.get(0)));
    for (int i = 1; i < lines.size(); i++) {
      sb.append('\n').append(stripIndentStep(lines.get(i)));
    }
    return ExCapturedBlock.capturedBlock(sb.toString());
  }

  private static String stripIndentStep(String line) {
    if (line.startsWith(IrObject.INDENT_STEP)) {
      return line.substring(IrObject.INDENT_STEP.length());
    }
    return line;
  }

  private static String structName(Symbol symbol) {
    return symbol.getName();
  }

  private static String escapeElixir(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
