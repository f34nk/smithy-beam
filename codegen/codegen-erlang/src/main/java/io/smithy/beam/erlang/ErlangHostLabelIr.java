package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapPattern;
import io.beam.ir.erlang.MapPatternEntry;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.MatchPattern;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.RecordPatternField;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EndpointTrait;

final class ErlangHostLabelIr {
  private ErlangHostLabelIr() {}

  static List<Function> buildHostFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ErlangTopDown.containedOperationsSorted(model, service)) {
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
    String inputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(input));
    SmithyPattern hostPrefix = op.expectTrait(EndpointTrait.class).getHostPrefix();

    List<RecordPatternField> fields = new ArrayList<>();
    for (MemberShape m : hostLabels) {
      String field = BeamNameUtils.toSnakeCase(m.getMemberName());
      fields.add(
          RecordPatternField.of(
              field, VariablePattern.of(ErlangRestXmlSupport.toBindingVar(field))));
    }

    BinaryExpr result =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(Variable.of("Prefix"), "binary"),
                BinarySegmentExpr.of(Variable.of("Authority"), "binary")));

    return Function.of(
        "build_host",
        List.of(
            FunctionClause.of(
                List.of(RecordPattern.of(inputRecord, fields), VariablePattern.of("Config")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "BaseUrl",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    AtomExpr.of("base_url"),
                                    Variable.of("Config"),
                                    BinaryExpr.of("")))),
                        MatchExpr.of(
                            TuplePattern.of(
                                List.of(
                                    VariablePattern.of("_Scheme"),
                                    VariablePattern.of("Authority"))),
                            RemoteCallExpr.of(
                                "utils",
                                "split_base_url",
                                List.of(Variable.of("BaseUrl"))),
                            null),
                        MatchExpr.bindValue("Prefix", buildHostPrefixExpression(hostPrefix)),
                        result),
                    false))));
  }

  private static BinaryExpr buildHostPrefixExpression(SmithyPattern hostPrefix) {
    if (hostPrefix.getSegments().isEmpty()) {
      return BinaryExpr.of("");
    }
    List<BinarySegmentExpr> segments = new ArrayList<>();
    for (SmithyPattern.Segment segment : hostPrefix.getSegments()) {
      if (segment.isLabel()) {
        String fieldName = BeamNameUtils.toSnakeCase(segment.getContent());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);
        segments.add(
            BinarySegmentExpr.of(
                LocalCallExpr.of(
                    "uri_encode",
                    List.of(LocalCallExpr.of("to_binary", List.of(Variable.of(bindingVar))))),
                "binary"));
      } else {
        segments.add(BinarySegmentExpr.literal(segment.getContent()));
      }
    }
    return BinaryExpr.of(segments);
  }
}
