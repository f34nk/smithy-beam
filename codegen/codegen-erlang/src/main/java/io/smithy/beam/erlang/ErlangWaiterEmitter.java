package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.core.BeamWaiterPaths;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.neighbor.Walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a {@code <service>_waiters.erl} helper for {@code @waitable} operations.
 */
public final class ErlangWaiterEmitter {

    private ErlangWaiterEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        BeamWaiterIndex index = BeamWaiterIndex.of(ctx.model(), service);
        if (index.isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
        String waitersMod = layout.waitersModuleName();
        String clientMod = layout.clientModuleName();
        SymbolProvider sp = ctx.symbolProvider();

        List<String> exports = new ArrayList<>();
        for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
            exports.add(waitFunctionName(binding.name()) + "/3");
        }

        ctx.writerDelegator().useFileWriter(layout.waitersModuleFile(), writer -> {
            writer.write("%% Generated waiters for $L.", service.getId());
            writer.write("-module($L).", waitersMod);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            writer.write("$L", ErlExportAttribute.export(exports).asString());
            writer.write("");

            for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
                emitWaiterFunction(writer, index, binding, clientMod, sp);
            }

            emitWaitUntilHelper(writer, ctx.model(), service, sp);
        });
    }

    private static void emitWaiterFunction(
            ErlangWriter writer,
            BeamWaiterIndex index,
            BeamWaiterIndex.WaiterBinding binding,
            String clientMod,
            SymbolProvider sp) {
        OperationShape operation = binding.operation();
        Symbol opSym = sp.toSymbol(operation);
        String fn = waitFunctionName(binding.name());
        List<BeamWaiterIndex.AcceptorInfo> acceptors = index.acceptors(binding);

        writer.write("%% @doc Waits using the $L waiter on $L.", binding.name(), operation.getId());
        writer.write("$L(Client, Input, Opts) ->", fn);
        writer.indent();
        writer.write("Acceptors = [");
        writer.indent();
        for (int i = 0; i < acceptors.size(); i++) {
            emitAcceptorMap(writer, acceptors.get(i), sp);
            if (i < acceptors.size() - 1) {
                writer.write(",");
            }
        }
        writer.dedent();
        writer.write("],");
        writer.write("WaitOpts = maps:merge(");
        writer.indent();
        writer.write("#{");
        writer.indent();
        writer.write("min_delay_ms => $L,", binding.minDelaySeconds() * 1000);
        writer.write("max_delay_ms => $L", binding.maxDelaySeconds() * 1000);
        writer.dedent();
        writer.write("}, Opts),");
        writer.dedent();
        writer.write("wait_until(");
        writer.indent();
        writer.write("fun() -> $L:$L(Client, Input) end,", clientMod, opSym.getName());
        writer.write("Acceptors,");
        writer.write("WaitOpts");
        writer.dedent();
        writer.write(").");
        writer.dedent();
        writer.write("");
    }

    private static void emitAcceptorMap(
            ErlangWriter writer, BeamWaiterIndex.AcceptorInfo acceptor, SymbolProvider sp) {
        writer.write("#{");
        writer.indent();
        writer.write("state => $L,", acceptor.state());
        acceptor.successExpected().ifPresentOrElse(
                expected -> writer.write("matcher => success, expected => $L", expected),
                () -> acceptor.errorTypeName().ifPresentOrElse(
                        errorType -> {
                            if (acceptor.resolvedError().isPresent()) {
                                String record = recordName(sp.toSymbol(acceptor.resolvedError().get()));
                                writer.write("matcher => errorType, expected => #$L{}", record);
                            } else {
                                writer.write(
                                        "matcher => errorType, expected => <<\"$L\">>",
                                        escapeBinary(errorType));
                            }
                        },
                        () -> acceptor.pathMatcher().ifPresentOrElse(
                                pathMatcher -> emitPathMatcher(writer, acceptor.matcherKind(), pathMatcher),
                                () -> writer.write("matcher => $L", acceptor.matcherKind()))));
        writer.dedent();
        writer.write("}");
    }

    private static void emitPathMatcher(
            ErlangWriter writer, String memberName, BeamWaiterIndex.PathMatcherInfo pathMatcher) {
        writer.write("matcher => $L,", memberName);
        writer.write("path => $L,", BeamWaiterPaths.emitErlangPath(pathMatcher.path()));
        writer.write("comparator => $L,", pathMatcher.comparator());
        writer.write("expected => <<\"$L\">>", escapeBinary(pathMatcher.expected()));
    }

    private static void emitWaitUntilHelper(
            ErlangWriter writer, Model model, ServiceShape service, SymbolProvider sp) {
        writer.write("wait_until(Fun, Acceptors, Opts) ->");
        writer.indent();
        writer.write("MaxAttempts = maps:get(max_attempts, Opts, 25),");
        writer.write("MinDelay = maps:get(min_delay_ms, Opts, 2000),");
        writer.write("MaxDelay = maps:get(max_delay_ms, Opts, 120000),");
        writer.write("wait_until(Fun, Acceptors, MaxAttempts, MinDelay, MaxDelay).");
        writer.dedent();
        writer.write("");
        writer.write("wait_until(_Fun, _Acceptors, 0, _Delay, _MaxDelay) ->");
        writer.indent();
        writer.write("{error, max_attempts_exceeded};");
        writer.dedent();
        writer.write("wait_until(Fun, Acceptors, Attempts, Delay, MaxDelay) ->");
        writer.indent();
        writer.write("Result = Fun(),");
        writer.write("case classify(Acceptors, Result) of");
        writer.indent();
        writer.write("success -> {ok, Result};");
        writer.write("failure -> {error, Result};");
        writer.write("retry when Attempts =< 1 -> {error, max_attempts_exceeded};");
        writer.write("retry ->");
        writer.indent();
        writer.write("timer:sleep(Delay),");
        writer.write("NextDelay = min(Delay * 2, MaxDelay),");
        writer.write("wait_until(Fun, Acceptors, Attempts - 1, NextDelay, MaxDelay)");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("classify([], _Result) -> retry;");
        writer.write("classify([Acceptor | Rest], Result) ->");
        writer.indent();
        writer.write("case matches_acceptor(Acceptor, Result) of");
        writer.indent();
        writer.write("true -> maps:get(state, Acceptor);");
        writer.write("false -> classify(Rest, Result)");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("matches_acceptor(#{matcher := success, expected := true}, {ok, _}) -> true;");
        writer.write("matches_acceptor(#{matcher := success, expected := false}, {error, _}) -> true;");
        writer.write("matches_acceptor(#{matcher := errorType, expected := Expected}, {error, Got}) ->");
        writer.indent();
        writer.write("error_types_match(Expected, Got);");
        writer.dedent();
        writer.write("matches_acceptor(");
        writer.indent();
        writer.write("#{matcher := output, path := Path, comparator := stringEquals, expected := Expected},");
        writer.write("{ok, Output}) ->");
        writer.dedent();
        writer.indent();
        writer.write("path_string_equals(Path, Expected, Output);");
        writer.dedent();
        writer.write("matches_acceptor(");
        writer.indent();
        writer.write("#{matcher := inputOutput, path := Path, comparator := stringEquals, expected := Expected},");
        writer.write("{ok, Output}) ->");
        writer.dedent();
        writer.indent();
        writer.write("path_string_equals(Path, Expected, Output);");
        writer.dedent();
        writer.write("matches_acceptor(_, _) -> false.");
        writer.write("");
        writer.write("error_types_match(Expected, _Got) when is_binary(Expected) -> true;");
        writer.write("error_types_match(Expected, Got) when is_tuple(Expected), is_tuple(Got) ->");
        writer.indent();
        writer.write("element(1, Expected) =:= element(1, Got);");
        writer.dedent();
        writer.write("error_types_match(Expected, Got) ->");
        writer.indent();
        writer.write("Expected =:= Got.");
        writer.dedent();
        writer.write("");
        writer.write("path_string_equals(Path, Expected, Output) ->");
        writer.indent();
        writer.write("case path_value(Path, Output) of");
        writer.indent();
        writer.write("undefined -> false;");
        writer.write("Value -> string_equals(Value, Expected)");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("path_value(Path, _Value) when is_binary(Path) ->");
        writer.indent();
        writer.write("undefined;");
        writer.dedent();
        writer.write("path_value([], Value) ->");
        writer.indent();
        writer.write("Value;");
        writer.dedent();
        writer.write("path_value([Key | Rest], Value) when is_map(Value) ->");
        writer.indent();
        writer.write("case maps:get(Key, Value, undefined) of");
        writer.indent();
        writer.write("undefined -> undefined;");
        writer.write("Next -> path_value(Rest, Next)");
        writer.dedent();
        writer.write("end;");
        writer.dedent();
        writer.write("path_value([Key | Rest], Value) when is_tuple(Value), tuple_size(Value) >= 1 ->");
        writer.indent();
        writer.write("case record_field(Value, Key) of");
        writer.indent();
        writer.write("undefined -> undefined;");
        writer.write("Next -> path_value(Rest, Next)");
        writer.dedent();
        writer.write("end;");
        writer.dedent();
        writer.write("path_value(_Path, _Value) ->");
        writer.indent();
        writer.write("undefined.");
        writer.dedent();
        writer.write("");
        emitRecordFieldsHelper(writer, model, service, sp);
        writer.write("record_field(Record, Field) when is_tuple(Record), tuple_size(Record) >= 1 ->");
        writer.indent();
        writer.write("Tag = element(1, Record),");
        writer.write("case record_fields(Tag) of");
        writer.indent();
        writer.write("Fields when is_list(Fields) ->");
        writer.indent();
        writer.write("Values = tl(tuple_to_list(Record)),");
        writer.write("case lists:keyfind(Field, 1, lists:zip(Fields, Values)) of");
        writer.indent();
        writer.write("{Field, V} -> V;");
        writer.write("false -> undefined");
        writer.dedent();
        writer.write("end;");
        writer.dedent();
        writer.write("undefined -> undefined");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("string_equals(V, Expected) when is_atom(V), is_binary(Expected) ->");
        writer.indent();
        writer.write("string:uppercase(atom_to_binary(V, utf8)) =:= string:uppercase(Expected);");
        writer.dedent();
        writer.write("string_equals(V, Expected) when is_binary(V), is_binary(Expected) ->");
        writer.indent();
        writer.write("string:uppercase(V) =:= string:uppercase(Expected);");
        writer.dedent();
        writer.write("string_equals(V, Expected) ->");
        writer.indent();
        writer.write("V =:= Expected.");
        writer.dedent();
    }

    private static void emitRecordFieldsHelper(
            ErlangWriter writer, Model model, ServiceShape service, SymbolProvider sp) {
        Set<Shape> closure = new Walker(model).walkShapes(service);
        List<StructureShape> structures = closure.stream()
                .filter(shape -> shape instanceof StructureShape)
                .map(shape -> (StructureShape) shape)
                .filter(shape -> !shape.getId().getNamespace().equals("smithy.api"))
                .sorted(Comparator.comparing(s -> s.getId().toString()))
                .collect(Collectors.toList());
        for (StructureShape structure : structures) {
            String record = recordName(sp.toSymbol(structure));
            writer.write("record_fields($L) ->", record);
            writer.indent();
            writer.write("record_info(fields, $L);", record);
            writer.dedent();
        }
        writer.write("record_fields(_) ->");
        writer.indent();
        writer.write("undefined.");
        writer.dedent();
        writer.write("");
    }

    private static String waitFunctionName(String waiterName) {
        return "wait_" + BeamNameUtils.toSnakeCase(waiterName);
    }

    private static String escapeBinary(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }
}
