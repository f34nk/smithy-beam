package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
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

/**
 * Generates a {@code <Service>Retry} helper that optionally retries operation calls when the
 * returned error matches a modeled {@code @retryable} exception.
 */
public final class ElixirRetryEmitter {

    private ElixirRetryEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        List<StructureShape> retryableErrors = retryableErrors(ctx.model(), service);
        if (retryableErrors.isEmpty()) {
            return;
        }

        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service.getId().getName());
        String retryMod = ElixirSymbolProvider.toModuleName(layout.retryModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        SymbolProvider sp = ctx.symbolProvider();

        ctx.writerDelegator().useFileWriter(layout.retryModuleFile(), writer -> {
            writer.write("defmodule $L do", retryMod);
            writer.indent();
            writer.write("@moduledoc \"Generated retry helpers for $L (generated).\"", service.getId());
            writer.write("");
            writer.write("@doc \"Invokes fun with exponential backoff when a modeled retryable error is returned.\"");
            writer.write("def with_retry(fun, opts \\\\ []) do");
            writer.indent();
            writer.write("max_attempts = Keyword.get(opts, :max_attempts, 3)");
            writer.write("base_delay_ms = Keyword.get(opts, :base_delay_ms, 100)");
            writer.write("with_retry(fun, max_attempts, base_delay_ms, 1)");
            writer.dedent();
            writer.write("");
            writer.write("defp with_retry(fun, 0, _base, _n), do: fun.()");
            writer.write("defp with_retry(fun, attempts, base, n) do");
            writer.indent();
            writer.write("case fun.() do");
            writer.indent();
            writer.write("{:ok, _} = ok ->");
            writer.indent();
            writer.write("ok");
            writer.dedent();
            writer.write("{:error, _} = err ->");
            writer.indent();
            writer.write("if should_retry?(err) and attempts > 1 do");
            writer.indent();
            writer.write("Process.sleep(trunc(base * :math.pow(2, n - 1)))");
            writer.write("with_retry(fun, attempts - 1, base, n + 1)");
            writer.dedent();
            writer.write("else");
            writer.indent();
            writer.write("err");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("");
            for (StructureShape error : retryableErrors) {
                String exceptionMod = sp.toSymbol(error).getName();
                writer.write("def should_retry?({:error, %$L.$L{}}), do: true", typesMod, exceptionMod);
            }
            writer.write("def should_retry?(_), do: false");
            writer.dedent();
            writer.write("end");
        });
    }

    private static List<StructureShape> retryableErrors(Model model, ServiceShape service) {
        List<StructureShape> errors = new ArrayList<>();
        for (Shape shape : new Walker(model).walkShapes(service)) {
            if (shape instanceof StructureShape structure) {
                BeamRetryIndex.forError(structure)
                        .filter(BeamRetryIndex.RetryInfo::retryable)
                        .ifPresent(info -> errors.add(structure));
            }
        }
        errors.sort(Comparator.comparing(s -> s.getId().toString()));
        return errors;
    }
}
