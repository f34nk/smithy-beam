package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlString;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Emits {@code aws_endpoint_rules.erl} when the model defines {@code @endpointRuleSet}.
 */
public final class ErlangAwsEndpointRulesEmitter {

    private ErlangAwsEndpointRulesEmitter() {}

    public static void emitIfNeeded(ErlangContext ctx, ServiceShape service) {
        if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
            return;
        }

        ctx.writerDelegator().useFileWriter("aws_endpoint_rules.erl", writer -> {
            writer.write("$L", awsEndpointRulesModule().asString());
        });
    }

    private static ErlModule awsEndpointRulesModule() {
        return new ErlModule(
                "aws_endpoint_rules",
                List.of(
                        ErlComment.comment(
                                "@doc Temporary stub endpoint rules evaluator emitted by smithy-beam codegen."),
                        ErlComment.comment(
                                "The rule set argument is ignored for now. Endpoint resolution uses a minimal placeholder"),
                        ErlComment.comment(
                                "until a full AWS rules engine runtime is available.")),
                List.of(ErlExportAttribute.export(List.of("evaluate/2"))),
                List.of(evaluate()));
    }

    private static ErlFunction evaluate() {
        return ErlFunction.functionWithSpec(
                "evaluate",
                2,
                "map(), map()",
                "{ok, #{url := binary(), headers := map()}} | {error, term()}",
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("_RuleSet"),
                                ErlVarPattern.varPattern("Params")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Region"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlBinary.binary("Region"),
                                                ErlVar.var("Params"),
                                                ErlCall.call(
                                                        "maps",
                                                        "get",
                                                        ErlAtom.atom("Region"),
                                                        ErlVar.var("Params"),
                                                        ErlAtom.atom("undefined")))),
                                ErlCase.caseExpr(
                                        ErlVar.var("Region"),
                                        ErlClause.clause(
                                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                                ErlTuple.tuple(
                                                        ErlAtom.atom("error"),
                                                        ErlString.string("Invalid Configuration: Missing Region"))),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("Value")),
                                                ErlTuple.tuple(
                                                        ErlAtom.atom("ok"),
                                                        ErlMap.map(
                                                                ErlMapEntry.entry(
                                                                        ErlAtom.atom("url"),
                                                                        ErlBinaryTemplate.binaryTemplate(
                                                                                ErlBinaryText.text("https://ec2."),
                                                                                ErlBinaryExpr.expr(
                                                                                        ErlVar.var("Value"),
                                                                                        "binary"),
                                                                                ErlBinaryText.text(".amazonaws.com"))),
                                                                ErlMapEntry.entry(
                                                                        ErlAtom.atom("headers"),
                                                                        ErlMap.map())))))))));
    }
}
