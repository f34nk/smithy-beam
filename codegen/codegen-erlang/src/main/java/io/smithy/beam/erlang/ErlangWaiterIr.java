package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.core.BeamWaiterPaths;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMapFieldPattern;
import io.smithy.beam.ir.erlang.ErlMapPattern;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRemoteCall;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ErlangWaiterIr {
    private ErlangWaiterIr() {}

    static ErlModule waitersModule(
            String waitersMod,
            String typesHeaderFile,
            String clientMod,
            BeamWaiterIndex index,
            SymbolProvider sp,
            Model model,
            ServiceShape service) {
        List<String> exports = new ArrayList<>();
        for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
            exports.add(waitFunctionName(binding.name()) + "/3");
        }

        List<ErlFunction> functions = new ArrayList<>();
        for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
            functions.add(waiterFunction(index, binding, clientMod, sp));
        }
        functions.addAll(waitUntilHelperFunctions(model, service, sp));

        return new ErlModule(
                waitersMod,
                List.of(ErlComment.comment("Generated waiters for " + service.getId() + ".")),
                List.of(
                        new ErlAttribute("include", "\"" + typesHeaderFile + "\""),
                        ErlExportAttribute.export(exports)),
                functions);
    }

    static ErlFunction waiterFunction(
            BeamWaiterIndex index,
            BeamWaiterIndex.WaiterBinding binding,
            String clientMod,
            SymbolProvider sp) {
        OperationShape operation = binding.operation();
        Symbol opSym = sp.toSymbol(operation);
        String fn = waitFunctionName(binding.name());
        List<BeamWaiterIndex.AcceptorInfo> acceptors = index.acceptors(binding);

        List<ErlExpr> acceptorMaps = new ArrayList<>();
        for (BeamWaiterIndex.AcceptorInfo acceptor : acceptors) {
            acceptorMaps.add(acceptorMap(acceptor, sp));
        }

        return new ErlFunction(
                fn,
                3,
                ErlFunctionDoc.functionDoc(
                        "Waits using the " + binding.name() + " waiter on " + operation.getId() + "."),
                null,
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Client"),
                                ErlVarPattern.varPattern("Input"),
                                ErlVarPattern.varPattern("Opts")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Acceptors"),
                                        ErlList.list(acceptorMaps.toArray(ErlExpr[]::new))),
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("WaitOpts"),
                                        ErlCall.call(
                                                "maps",
                                                "merge",
                                                ErlMap.map(
                                                        ErlMapEntry.entry(
                                                                ErlAtom.atom("min_delay_ms"),
                                                                ErlInteger.integer(binding.minDelaySeconds() * 1000L)),
                                                        ErlMapEntry.entry(
                                                                ErlAtom.atom("max_delay_ms"),
                                                                ErlInteger.integer(binding.maxDelaySeconds() * 1000L))),
                                                ErlVar.var("Opts"))),
                                ErlCallLocal.callLocal(
                                        "wait_until",
                                        ErlFun.fun(ErlClause.clause(
                                                List.of(),
                                                ErlRemoteCall.call(
                                                        ErlAtom.atom(clientMod),
                                                        opSym.getName(),
                                                        ErlVar.var("Client"),
                                                        ErlVar.var("Input")))),
                                        ErlVar.var("Acceptors"),
                                        ErlVar.var("WaitOpts"))))));
    }

    static ErlMap acceptorMap(BeamWaiterIndex.AcceptorInfo acceptor, SymbolProvider sp) {
        List<ErlMapEntry> entries = new ArrayList<>();
        entries.add(ErlMapEntry.entry(ErlAtom.atom("state"), ErlAtom.atom(acceptor.state())));
        if (acceptor.successExpected().isPresent()) {
            boolean expected = acceptor.successExpected().get();
            entries.add(ErlMapEntry.entry(ErlAtom.atom("matcher"), ErlAtom.atom("success")));
            entries.add(ErlMapEntry.entry(
                    ErlAtom.atom("expected"),
                    expected ? ErlAtom.atom("true") : ErlAtom.atom("false")));
        } else if (acceptor.errorTypeName().isPresent()) {
            String errorType = acceptor.errorTypeName().get();
            entries.add(ErlMapEntry.entry(ErlAtom.atom("matcher"), ErlAtom.atom("errorType")));
            if (acceptor.resolvedError().isPresent()) {
                String record = recordName(sp.toSymbol(acceptor.resolvedError().get()));
                entries.add(ErlMapEntry.entry(ErlAtom.atom("expected"), ErlRecord.record(record)));
            } else {
                entries.add(ErlMapEntry.entry(
                        ErlAtom.atom("expected"),
                        ErlBinary.binary(errorType)));
            }
        } else if (acceptor.pathMatcher().isPresent()) {
            BeamWaiterIndex.PathMatcherInfo pathMatcher = acceptor.pathMatcher().get();
            entries.add(ErlMapEntry.entry(ErlAtom.atom("matcher"), ErlAtom.atom(acceptor.matcherKind())));
            entries.add(ErlMapEntry.entry(ErlAtom.atom("path"), pathExpr(pathMatcher.path())));
            entries.add(ErlMapEntry.entry(ErlAtom.atom("comparator"), ErlAtom.atom(pathMatcher.comparator())));
            entries.add(ErlMapEntry.entry(
                    ErlAtom.atom("expected"),
                    ErlBinary.binary(pathMatcher.expected())));
        } else {
            entries.add(ErlMapEntry.entry(ErlAtom.atom("matcher"), ErlAtom.atom(acceptor.matcherKind())));
        }
        return ErlMap.map(entries.toArray(ErlMapEntry[]::new));
    }

    static List<ErlFunction> waitUntilHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(waitUntilArity3());
        functions.add(waitUntilArity5());
        functions.add(classify());
        functions.add(matchesAcceptor());
        functions.add(errorTypesMatch());
        functions.add(pathStringEquals());
        functions.add(pathValue());
        functions.addAll(recordFieldsFunctions(model, service, sp));
        functions.add(recordField());
        functions.add(stringEquals());
        return functions;
    }

    private static ErlFunction waitUntilArity3() {
        return ErlFunction.function(
                "wait_until",
                3,
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Fun"),
                                ErlVarPattern.varPattern("Acceptors"),
                                ErlVarPattern.varPattern("Opts")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("MaxAttempts"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlAtom.atom("max_attempts"),
                                                ErlVar.var("Opts"),
                                                ErlInteger.integer(25))),
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("MinDelay"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlAtom.atom("min_delay_ms"),
                                                ErlVar.var("Opts"),
                                                ErlInteger.integer(2000))),
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("MaxDelay"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlAtom.atom("max_delay_ms"),
                                                ErlVar.var("Opts"),
                                                ErlInteger.integer(120000))),
                                ErlCallLocal.callLocal(
                                        "wait_until",
                                        ErlVar.var("Fun"),
                                        ErlVar.var("Acceptors"),
                                        ErlVar.var("MaxAttempts"),
                                        ErlVar.var("MinDelay"),
                                        ErlVar.var("MaxDelay"))))));
    }

    private static ErlFunction waitUntilArity5() {
        return ErlFunction.function(
                "wait_until",
                5,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_Fun"),
                                        ErlVarPattern.varPattern("_Acceptors"),
                                        ErlVarPattern.varPattern("0"),
                                        ErlVarPattern.varPattern("_Delay"),
                                        ErlVarPattern.varPattern("_MaxDelay")),
                                ErlTuple.tuple(
                                        ErlAtom.atom("error"), ErlAtom.atom("max_attempts_exceeded"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlVarPattern.varPattern("Fun"),
                                        ErlVarPattern.varPattern("Acceptors"),
                                        ErlVarPattern.varPattern("Attempts"),
                                        ErlVarPattern.varPattern("Delay"),
                                        ErlVarPattern.varPattern("MaxDelay")),
                                ErlExprBlock.block(
                                        ErlMatch.match(ErlVarPattern.varPattern("Result"), ErlCallLocal.callLocal("Fun")),
                                        ErlCase.caseExpr(
                                                ErlCallLocal.callLocal(
                                                        "classify", ErlVar.var("Acceptors"), ErlVar.var("Result")),
                                                ErlClause.clause(
                                                        List.of(ErlAtomPattern.atomPattern("success")),
                                                        ErlTuple.tuple(
                                                                ErlAtom.atom("ok"), ErlVar.var("Result"))),
                                                ErlClause.clause(
                                                        List.of(ErlAtomPattern.atomPattern("failure")),
                                                        ErlTuple.tuple(
                                                                ErlAtom.atom("error"), ErlVar.var("Result"))),
                                                ErlClause.clause(
                                                        List.of(ErlAtomPattern.atomPattern("retry")),
                                                        List.of(ErlGuard.exprGuard(
                                                                ErlOp.op("=<", ErlVar.var("Attempts"), ErlInteger.integer(1)))),
                                                        ErlTuple.tuple(
                                                                ErlAtom.atom("error"),
                                                                ErlAtom.atom("max_attempts_exceeded"))),
                                                ErlClause.blockClause(
                                                        List.of(ErlAtomPattern.atomPattern("retry")),
                                                        ErlExprBlock.block(
                                                                ErlCall.call(
                                                                        "timer",
                                                                        "sleep",
                                                                        ErlVar.var("Delay")),
                                                                ErlMatch.match(
                                                                        ErlVarPattern.varPattern("NextDelay"),
                                                                        ErlCall.call(
                                                                                "erlang",
                                                                                "min",
                                                                                ErlOp.op("*", ErlVar.var("Delay"), ErlInteger.integer(2)),
                                                                                ErlVar.var("MaxDelay"))),
                                                                ErlCallLocal.callLocal(
                                                                        "wait_until",
                                                                        ErlVar.var("Fun"),
                                                                        ErlVar.var("Acceptors"),
                                                                        ErlOp.op("-", ErlVar.var("Attempts"), ErlInteger.integer(1)),
                                                                        ErlVar.var("NextDelay"),
                                                                        ErlVar.var("MaxDelay")))))))));
    }

    private static ErlFunction classify() {
        return ErlFunction.function(
                "classify",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlNilPattern.nilPattern(),
                                        ErlVarPattern.varPattern("_Result")),
                                ErlAtom.atom("retry")),
                        ErlClause.blockClause(
                                List.of(
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Acceptor"),
                                                ErlVarPattern.varPattern("Rest")),
                                        ErlVarPattern.varPattern("Result")),
                                ErlCase.caseExpr(
                                        ErlCallLocal.callLocal(
                                                "matches_acceptor",
                                                ErlVar.var("Acceptor"),
                                                ErlVar.var("Result")),
                                        ErlClause.clause(
                                                List.of(ErlAtomPattern.atomPattern("true")),
                                                ErlCall.call(
                                                        "maps",
                                                        "get",
                                                        ErlAtom.atom("state"),
                                                        ErlVar.var("Acceptor"))),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("false")),
                                                ErlCallLocal.callLocal(
                                                        "classify", ErlVar.var("Rest"), ErlVar.var("Result")))))));
    }

    private static ErlFunction matchesAcceptor() {
        return ErlFunction.function(
                "matches_acceptor",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlMapPattern.mapPattern(
                                                ErlMapFieldPattern.fieldPattern(
                                                        "matcher", ErlAtomPattern.atomPattern("success")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "expected", ErlAtomPattern.atomPattern("true"))),
                                        ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("_"))),
                                ErlAtom.atom("true")),
                        ErlClause.clause(
                                List.of(
                                        ErlMapPattern.mapPattern(
                                                ErlMapFieldPattern.fieldPattern(
                                                        "matcher", ErlAtomPattern.atomPattern("success")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "expected", ErlAtomPattern.atomPattern("false"))),
                                        ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("error"),
                                                ErlVarPattern.varPattern("_"))),
                                ErlAtom.atom("true")),
                        ErlClause.blockClause(
                                List.of(
                                        ErlMapPattern.mapPattern(
                                                ErlMapFieldPattern.fieldPattern(
                                                        "matcher", ErlAtomPattern.atomPattern("errorType")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "expected", ErlVarPattern.varPattern("Expected"))),
                                        ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("error"),
                                                ErlVarPattern.varPattern("Got"))),
                                ErlCallLocal.callLocal(
                                        "error_types_match",
                                        ErlVar.var("Expected"),
                                        ErlVar.var("Got"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlMapPattern.mapPattern(
                                                ErlMapFieldPattern.fieldPattern(
                                                        "matcher", ErlAtomPattern.atomPattern("output")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "path", ErlVarPattern.varPattern("Path")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "comparator",
                                                        ErlAtomPattern.atomPattern("stringEquals")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "expected", ErlVarPattern.varPattern("Expected"))),
                                        ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("Output"))),
                                ErlCallLocal.callLocal(
                                        "path_string_equals",
                                        ErlVar.var("Path"),
                                        ErlVar.var("Expected"),
                                        ErlVar.var("Output"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlMapPattern.mapPattern(
                                                ErlMapFieldPattern.fieldPattern(
                                                        "matcher", ErlAtomPattern.atomPattern("inputOutput")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "path", ErlVarPattern.varPattern("Path")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "comparator",
                                                        ErlAtomPattern.atomPattern("stringEquals")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "expected", ErlVarPattern.varPattern("Expected"))),
                                        ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("Output"))),
                                ErlCallLocal.callLocal(
                                        "path_string_equals",
                                        ErlVar.var("Path"),
                                        ErlVar.var("Expected"),
                                        ErlVar.var("Output"))),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_"),
                                        ErlVarPattern.varPattern("_")),
                                ErlAtom.atom("false"))));
    }

    private static ErlFunction errorTypesMatch() {
        return ErlFunction.function(
                "error_types_match",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Expected"),
                                        ErlVarPattern.varPattern("_Got")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Expected"))),
                                ErlAtom.atom("true")),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Expected"),
                                        ErlVarPattern.varPattern("Got")),
                                List.of(
                                        ErlGuard.guard("is_tuple", ErlVar.var("Expected")),
                                        ErlGuard.guard("is_tuple", ErlVar.var("Got"))),
                                ErlOp.op(
                                        "=:=",
                                        ErlCallLocal.callLocal(
                                                "element", ErlInteger.integer(1), ErlVar.var("Expected")),
                                        ErlCallLocal.callLocal(
                                                "element", ErlInteger.integer(1), ErlVar.var("Got")))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlVarPattern.varPattern("Expected"),
                                        ErlVarPattern.varPattern("Got")),
                                ErlOp.op("=:=", ErlVar.var("Expected"), ErlVar.var("Got")))));
    }

    private static ErlFunction pathStringEquals() {
        return ErlFunction.function(
                "path_string_equals",
                3,
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Path"),
                                ErlVarPattern.varPattern("Expected"),
                                ErlVarPattern.varPattern("Output")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                case path_value(Path, Output) of
                                    undefined -> false;
                                    Value -> string_equals(Value, Expected)
                                end"""))));
    }

    private static ErlFunction pathValue() {
        return ErlFunction.function(
                "path_value",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Path"),
                                        ErlVarPattern.varPattern("_Value")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Path"))),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(
                                        ErlNilPattern.nilPattern(),
                                        ErlVarPattern.varPattern("Value")),
                                ErlVar.var("Value")),
                        ErlClause.blockClause(
                                List.of(
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Key"),
                                                ErlVarPattern.varPattern("Rest")),
                                        ErlVarPattern.varPattern("Value")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Value"))),
                                ErlCapturedBlock.capturedBlock(
                                        """
                                        case maps:get(Key, Value, undefined) of
                                            undefined -> undefined;
                                            Next -> path_value(Rest, Next)
                                        end""")),
                        ErlClause.blockClause(
                                List.of(
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Key"),
                                                ErlVarPattern.varPattern("Rest")),
                                        ErlVarPattern.varPattern("Value")),
                                List.of(
                                        ErlGuard.guard("is_tuple", ErlVar.var("Value")),
                                        ErlGuard.exprGuard(ErlOp.op(
                                                ">=",
                                                ErlCallLocal.callLocal("tuple_size", ErlVar.var("Value")),
                                                ErlInteger.integer(1)))),
                                ErlCapturedBlock.capturedBlock(
                                        """
                                        case record_field(Value, Key) of
                                            undefined -> undefined;
                                            Next -> path_value(Rest, Next)
                                        end""")),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_Path"),
                                        ErlVarPattern.varPattern("_Value")),
                                ErlAtom.atom("undefined"))));
    }

    private static List<ErlFunction> recordFieldsFunctions(
            Model model, ServiceShape service, SymbolProvider sp) {
        Set<Shape> closure = new Walker(model).walkShapes(service);
        List<StructureShape> structures = closure.stream()
                .filter(shape -> shape instanceof StructureShape)
                .map(shape -> (StructureShape) shape)
                .filter(shape -> !shape.getId().getNamespace().equals("smithy.api"))
                .sorted(Comparator.comparing(s -> s.getId().toString()))
                .collect(Collectors.toList());

        List<ErlClause> clauses = new ArrayList<>();
        for (StructureShape structure : structures) {
            String record = recordName(sp.toSymbol(structure));
            clauses.add(ErlClause.blockClause(
                    List.of(ErlAtomPattern.atomPattern(record)),
                    ErlCallLocal.callLocal("record_info", ErlAtom.atom("fields"), ErlAtom.atom(record))));
        }
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("_")),
                ErlAtom.atom("undefined")));
        return List.of(ErlFunction.function("record_fields", 1, clauses));
    }

    private static ErlFunction recordField() {
        return ErlFunction.function(
                "record_field",
                2,
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Record"),
                                ErlVarPattern.varPattern("Field")),
                        List.of(
                                ErlGuard.guard("is_tuple", ErlVar.var("Record")),
                                ErlGuard.exprGuard(ErlOp.op(
                                        ">=",
                                        ErlCallLocal.callLocal("tuple_size", ErlVar.var("Record")),
                                        ErlInteger.integer(1)))),
                        ErlCapturedBlock.capturedBlock(
                                """
                                Tag = element(1, Record),
                                case record_fields(Tag) of
                                    Fields when is_list(Fields) ->
                                        Values = tl(tuple_to_list(Record)),
                                        case lists:keyfind(Field, 1, lists:zip(Fields, Values)) of
                                            {Field, V} -> V;
                                            false -> undefined
                                        end;
                                    undefined -> undefined
                                end"""))));
    }

    private static ErlFunction stringEquals() {
        return ErlFunction.function(
                "string_equals",
                2,
                List.of(
                        ErlClause.blockClause(
                                List.of(
                                        ErlVarPattern.varPattern("V"),
                                        ErlVarPattern.varPattern("Expected")),
                                List.of(
                                        ErlGuard.guard("is_atom", ErlVar.var("V")),
                                        ErlGuard.guard("is_binary", ErlVar.var("Expected"))),
                                ErlOp.op(
                                        "=:=",
                                        ErlCall.call(
                                                "string",
                                                "uppercase",
                                                ErlCallLocal.callLocal(
                                                        "atom_to_binary",
                                                        ErlVar.var("V"),
                                                        ErlAtom.atom("utf8"))),
                                        ErlCall.call(
                                                "string",
                                                "uppercase",
                                                ErlVar.var("Expected")))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlVarPattern.varPattern("V"),
                                        ErlVarPattern.varPattern("Expected")),
                                List.of(
                                        ErlGuard.guard("is_binary", ErlVar.var("V")),
                                        ErlGuard.guard("is_binary", ErlVar.var("Expected"))),
                                ErlOp.op(
                                        "=:=",
                                        ErlCall.call("string", "uppercase", ErlVar.var("V")),
                                        ErlCall.call("string", "uppercase", ErlVar.var("Expected")))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlVarPattern.varPattern("V"),
                                        ErlVarPattern.varPattern("Expected")),
                                ErlOp.op("=:=", ErlVar.var("V"), ErlVar.var("Expected")))));
    }

    private static ErlExpr pathExpr(String path) {
        if (BeamWaiterPaths.isSimpleDottedPath(path)) {
            ErlExpr[] segments = Arrays.stream(path.split("\\."))
                    .map(segment -> ErlAtom.atom(BeamNameUtils.toSnakeCase(segment)))
                    .toArray(ErlExpr[]::new);
            return ErlList.list(segments);
        }
        return ErlBinary.binary(path);
    }

    private static String waitFunctionName(String waiterName) {
        return "wait_" + BeamNameUtils.toSnakeCase(waiterName);
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }
}
