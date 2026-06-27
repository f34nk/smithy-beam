package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStructFieldPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EndpointTrait;

final class ElixirHostLabelIr {
  private ElixirHostLabelIr() {}

  static List<ExFunction> buildHostFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(splitBaseUrl());
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      List<MemberShape> hostLabels = hostLabelIndex.hostLabelMembers(op);
      if (hostLabels.isEmpty() || !op.hasTrait(EndpointTrait.class)) {
        continue;
      }
      functions.add(buildHostFunction(model, op, hostLabels, sp));
    }
    return functions;
  }

  static ExFunction splitBaseUrl() {
    return ExFunction.defpFunction(
        "split_base_url",
        List.of(
            ExClause.inlineClause(
                List.of(ExStringPattern.string("")),
                ExTuple.tuple(ExString.string(""), ExString.string(""))),
            ExClause.blockClause(
                List.of(ExVarPattern.var("base_url")),
                ExCapturedBlock.capturedBlock(
                    "case URI.parse(base_url) do\n"
                        + "  %URI{scheme: scheme, host: host} = uri when is_binary(host) ->\n"
                        + "    port_suffix =\n"
                        + "      case {uri.scheme, uri.port} do\n"
                        + "        {\"https\", 443} -> \"\"\n"
                        + "        {\"http\", 80} -> \"\"\n"
                        + "        {_, nil} -> \"\"\n"
                        + "        {_, port} -> \":#{port}\"\n"
                        + "      end\n"
                        + "\n"
                        + "    {scheme <> \"://\", host <> port_suffix}\n"
                        + "\n"
                        + "  _ ->\n"
                        + "    {\"\", base_url}\n"
                        + "end"))));
  }

  private static ExFunction buildHostFunction(
      Model model, OperationShape op, List<MemberShape> hostLabels, SymbolProvider sp) {
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputStruct = sp.toSymbol(input).getName();
    SmithyPattern hostPrefix = op.expectTrait(EndpointTrait.class).getHostPrefix();

    List<ExStructFieldPattern> fields = new ArrayList<>();
    for (MemberShape member : hostLabels) {
      String field = fieldName(sp, member);
      fields.add(ExStructFieldPattern.fieldPattern(field, ExVarPattern.var(field)));
    }

    io.smithy.beam.ir.elixir.ExExpr prefixExpr = buildHostPrefixExpression(hostPrefix, hostLabels, sp);

    return ExFunction.defpFunction(
        "build_host",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExStructPattern.struct("Types." + inputStruct, fields),
                    ExVarPattern.var("config")),
                ExExprBlock.block(
                    ExMatch.match(
                        ExVarPattern.var("base_url"),
                        ExCall.call(
                            "Map",
                            "get",
                            ExVar.var("config"),
                            ExAtom.atom("base_url"),
                            ExString.string(""))),
                    ExMatch.match(
                        ExTuplePattern.tuple(
                            ExVarPattern.var("_scheme"),
                            ExVarPattern.var("authority")),
                        ExCallLocal.callLocal("split_base_url", ExVar.var("base_url"))),
                    ExMatch.match(ExVarPattern.var("prefix"), prefixExpr),
                    ExOp.op("<>", ExVar.var("prefix"), ExVar.var("authority"))))));
  }

  private static io.smithy.beam.ir.elixir.ExExpr buildHostPrefixExpression(
      SmithyPattern hostPrefix, List<MemberShape> hostLabels, SymbolProvider sp) {
    if (hostPrefix.getSegments().isEmpty()) {
      return ExString.string("");
    }
    Map<String, String> labelFields = new HashMap<>();
    for (MemberShape member : hostLabels) {
      labelFields.put(member.getMemberName(), fieldName(sp, member));
    }
    io.smithy.beam.ir.elixir.ExExpr expr = null;
    List<SmithyPattern.Segment> segments = hostPrefix.getSegments();
    for (SmithyPattern.Segment segment : segments) {
      io.smithy.beam.ir.elixir.ExExpr part;
      if (segment.isLabel()) {
        String field =
            labelFields.getOrDefault(
                segment.getContent(), BeamNameUtils.toSnakeCase(segment.getContent()));
        part =
            ExCall.call(
                "URI",
                "encode",
                ExCall.call("Kernel", "to_string", ExVar.var(field)));
      } else {
        part = ExString.string(segment.getContent());
      }
      expr = expr == null ? part : ExOp.op("<>", expr, part);
    }
    return expr;
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    return BeamNameUtils.toSnakeCase(member.getMemberName());
  }
}
