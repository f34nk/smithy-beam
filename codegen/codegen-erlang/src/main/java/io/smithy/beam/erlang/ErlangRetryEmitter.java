package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamRetryIndex;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Generates a {@code <service>_retry.erl} helper that optionally retries operation calls
 * when the returned error matches a modeled {@code @retryable} structure.
 */
public final class ErlangRetryEmitter {

    private ErlangRetryEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        List<StructureShape> retryableErrors = retryableErrors(ctx.model(), service);
        List<StructureShape> modeledErrors = modeledErrors(ctx.model(), service);
        if (retryableErrors.isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service.getId().getName());
        String retryMod = layout.retryModuleName();
        SymbolProvider sp = ctx.symbolProvider();

        ctx.writerDelegator().useFileWriter(layout.retryModuleFile(), writer -> {
            writer.write("%% Generated retry helpers for $L.", service.getId());
            writer.write("-module($L).", retryMod);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            writer.write("-export([with_retry/2, retryable/1, throttling/1, should_retry/1]).");
            writer.write("");
            writer.write("%% @doc Invokes {@code Fun} with exponential backoff when a modeled retryable error is returned.");
            writer.write("-spec with_retry(fun(() -> term()), map()) -> term().");
            writer.write("with_retry(Fun, Opts) ->");
            writer.indent();
            writer.write("Max = maps:get(max_attempts, Opts, 3),");
            writer.write("Base = maps:get(base_delay_ms, Opts, 100),");
            writer.write("with_retry(Fun, Max, Base, 1).");
            writer.dedent();
            writer.write("");
            writer.write("with_retry(Fun, 0, _, _) ->");
            writer.indent();
            writer.write("Fun();");
            writer.dedent();
            writer.write("with_retry(Fun, Attempts, Base, N) ->");
            writer.indent();
            writer.write("case Fun() of");
            writer.indent();
            writer.write("{ok, _} = Ok ->");
            writer.indent();
            writer.write("Ok;");
            writer.dedent();
            writer.write("{error, _} = Err ->");
            writer.indent();
            writer.write("case should_retry(Err) of");
            writer.indent();
            writer.write("true when Attempts > 1 ->");
            writer.indent();
            writer.write("timer:sleep(trunc(Base * math:pow(2, N - 1))),");
            writer.write("with_retry(Fun, Attempts - 1, Base, N + 1);");
            writer.dedent();
            writer.write("_ ->");
            writer.indent();
            writer.write("Err");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end.");
            writer.dedent();
            writer.write("");
            writer.write("-spec retryable(term()) -> boolean().");
            for (StructureShape error : modeledErrors) {
                String recordName = recordName(sp.toSymbol(error));
                Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
                if (info.isPresent() && info.get().retryable()) {
                    writer.write("retryable(#$L{}) -> true;", recordName);
                }
            }
            writer.write("retryable(_) -> false.");
            writer.write("");
            writer.write("-spec throttling(term()) -> boolean().");
            for (StructureShape error : modeledErrors) {
                String recordName = recordName(sp.toSymbol(error));
                Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
                if (info.isPresent() && info.get().throttling()) {
                    writer.write("throttling(#$L{}) -> true;", recordName);
                }
            }
            writer.write("throttling(_) -> false.");
            writer.write("");
            writer.write("-spec should_retry(term()) -> boolean().");
            for (StructureShape error : retryableErrors) {
                String recordName = recordName(sp.toSymbol(error));
                writer.write("should_retry({error, #$L{}}) -> true;", recordName);
            }
            writer.write("should_retry(_) -> false.");
        });
    }

    private static List<StructureShape> modeledErrors(Model model, ServiceShape service) {
        List<StructureShape> errors = new ArrayList<>();
        for (Shape shape : new Walker(model).walkShapes(service)) {
            if (shape instanceof StructureShape structure
                    && BeamRetryIndex.forError(structure).isPresent()) {
                errors.add(structure);
            }
        }
        errors.sort(Comparator.comparing(s -> s.getId().toString()));
        return errors;
    }

    private static List<StructureShape> retryableErrors(Model model, ServiceShape service) {
        List<StructureShape> errors = new ArrayList<>();
        for (StructureShape error : modeledErrors(model, service)) {
            if (BeamRetryIndex.forError(error).orElseThrow().retryable()) {
                errors.add(error);
            }
        }
        return errors;
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }
}
