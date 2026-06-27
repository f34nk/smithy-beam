package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinarySegment;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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

  static List<ErlFunction> buildHostFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ErlFunction> functions = new ArrayList<>();
    functions.add(ErlangHttpDispatchIr.splitBaseUrl());
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

  private static ErlFunction buildHostFunction(
      Model model, OperationShape op, List<MemberShape> hostLabels, SymbolProvider sp) {
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(input));
    SmithyPattern hostPrefix = op.expectTrait(EndpointTrait.class).getHostPrefix();

    List<ErlRecordFieldPattern> fields = new ArrayList<>();
    for (MemberShape m : hostLabels) {
      String field = BeamNameUtils.toSnakeCase(m.getMemberName());
      fields.add(
          ErlRecordFieldPattern.fieldPattern(
              field, ErlVarPattern.varPattern(ErlangRestXmlSupport.toBindingVar(field))));
    }

    ErlBinaryTemplate result =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryExpr.expr(ErlVar.var("Prefix"), true),
            ErlBinaryExpr.expr(ErlVar.var("Authority"), true));

    return ErlFunction.function(
        "build_host",
        2,
        List.of(
            ErlClause.clause(
                List.of(
                    new ErlRecordPattern(inputRecord, fields), ErlVarPattern.varPattern("Config")),
                ErlExprBlock.block(
                    ErlMatch.match(
                        ErlVarPattern.varPattern("BaseUrl"),
                        ErlCallLocal.callLocal(
                            "maps:get",
                            ErlAtom.atom("base_url"),
                            ErlVar.var("Config"),
                            ErlBinary.binary(""))),
                    ErlMatch.match(
                        ErlTuplePattern.tuplePattern(
                            ErlVarPattern.varPattern("_Scheme"),
                            ErlVarPattern.varPattern("Authority")),
                        ErlCallLocal.callLocal("split_base_url", ErlVar.var("BaseUrl"))),
                    ErlMatch.match(
                        ErlVarPattern.varPattern("Prefix"), buildHostPrefixExpression(hostPrefix)),
                    result))));
  }

  private static ErlBinaryTemplate buildHostPrefixExpression(SmithyPattern hostPrefix) {
    if (hostPrefix.getSegments().isEmpty()) {
      return ErlBinaryTemplate.binaryTemplate(ErlBinaryText.text(""));
    }
    List<ErlBinarySegment> segments = new ArrayList<>();
    for (SmithyPattern.Segment segment : hostPrefix.getSegments()) {
      if (segment.isLabel()) {
        String fieldName = BeamNameUtils.toSnakeCase(segment.getContent());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);
        segments.add(
            ErlBinaryExpr.expr(
                ErlCallLocal.callLocal(
                    "uri_encode", ErlCallLocal.callLocal("to_binary", ErlVar.var(bindingVar))),
                "binary"));
      } else {
        segments.add(ErlBinaryText.text(segment.getContent()));
      }
    }
    return ErlBinaryTemplate.binaryTemplate(segments.toArray(ErlBinarySegment[]::new));
  }
}
