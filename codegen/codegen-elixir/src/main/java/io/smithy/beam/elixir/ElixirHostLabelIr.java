package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StructPattern;
import io.beam.dsl.elixir.StructPatternField;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
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

  static List<Function> buildHostFunctions(Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    io.smithy.beam.core.BeamHostLabelIndex hostLabelIndex =
        io.smithy.beam.core.BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      List<MemberShape> hostLabels = hostLabelIndex.hostLabelMembers(op);
      if (hostLabels.isEmpty() || !op.hasTrait(EndpointTrait.class)) {
        continue;
      }
      functions.add(buildHostFunction(model, op, hostLabels, sp));
    }
    return functions;
  }

  private static Function buildHostFunction(
      Model model, OperationShape op, List<MemberShape> hostLabels, SymbolProvider sp) {
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputStruct = sp.toSymbol(input).getName();
    SmithyPattern hostPrefix = op.expectTrait(EndpointTrait.class).getHostPrefix();

    List<StructPatternField> fields = new ArrayList<>();
    for (MemberShape member : hostLabels) {
      String field = fieldName(sp, member);
      fields.add(StructPatternField.of(field, VariablePattern.of(field)));
    }

    Expression prefixExpr = buildHostPrefixExpression(hostPrefix, hostLabels, sp);

    return new Function(
        "build_host",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    StructPattern.of("Types." + inputStruct, fields),
                    VariablePattern.of("config")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "base_url",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            Variable.of("config"), AtomExpr.of("base_url"), StringExpr.of("")))),
                MatchExpr.bind(
                    TuplePattern.of(
                        List.of(VariablePattern.of("_scheme"), VariablePattern.of("authority"))),
                    RemoteCallExpr.of("Utils", "split_base_url", List.of(Variable.of("base_url")))),
                MatchExpr.bind("prefix", prefixExpr),
                new InfixExpr(Variable.of("prefix"), "<>", Variable.of("authority")))),
        null,
        null,
        false);
  }

  private static Expression buildHostPrefixExpression(
      SmithyPattern hostPrefix, List<MemberShape> hostLabels, SymbolProvider sp) {
    if (hostPrefix.getSegments().isEmpty()) {
      return StringExpr.of("");
    }
    Map<String, String> labelFields = new HashMap<>();
    for (MemberShape member : hostLabels) {
      labelFields.put(member.getMemberName(), fieldName(sp, member));
    }
    Expression expr = null;
    List<SmithyPattern.Segment> segments = hostPrefix.getSegments();
    for (SmithyPattern.Segment segment : segments) {
      Expression part;
      if (segment.isLabel()) {
        String field =
            labelFields.getOrDefault(
                segment.getContent(),
                io.smithy.beam.core.BeamNameUtils.toSnakeCase(segment.getContent()));
        part =
            RemoteCallExpr.of(
                "URI",
                "encode",
                List.of(RemoteCallExpr.of("Kernel", "to_string", List.of(Variable.of(field)))));
      } else {
        part = StringExpr.of(segment.getContent());
      }
      expr = expr == null ? part : new InfixExpr(expr, "<>", part);
    }
    return expr;
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    return io.smithy.beam.core.BeamNameUtils.toSnakeCase(member.getMemberName());
  }
}
