package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Emits {@code runtime_helpers.ex} with HTTP path label parsing helpers.
 * Emitted once per service when any operation binds {@code @httpLabel} members.
 */
public final class ElixirRuntimeHelpersEmitter {

    private ElixirRuntimeHelpersEmitter() {}

    public static void emitIfNeeded(ElixirContext ctx, ServiceShape service) {
        if (!serviceHasLabelBindings(ctx.model(), service)) {
            return;
        }
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), service.getId().getNamespace());
        String helpersMod = ElixirSymbolProvider.toModuleName(layout.runtimeHelpersModuleName());

        ctx.writerDelegator().useFileWriter(layout.runtimeHelpersModuleFile(), writer -> {
            writer.write("defmodule $L do", helpersMod);
            writer.indent();
            writer.write("@moduledoc \"Generated runtime helpers for $L. Do not edit.\"",
                    service.getId());
            writer.write("");
            writer.write("@spec parse_labels(String.t(), String.t()) ::");
            writer.write("        {:ok, map()} | {:error, :path_mismatch}");
            writer.write("def parse_labels(path, template) do");
            writer.indent();
            writer.write("case match_segments(segments(path), segments(template), %{}) do");
            writer.indent();
            writer.write("{:ok, labels} -> {:ok, labels}");
            writer.write("_ -> {:error, :path_mismatch}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp segments(path) do");
            writer.indent();
            writer.write("path");
            writer.write("|> String.split(\"/\", trim: true)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp match_segments([], [], acc), do: {:ok, acc}");
            writer.write("");
            writer.write("defp match_segments([seg | rest_path], [tpl_seg | rest_tpl], acc) do");
            writer.indent();
            writer.write("case label_name(tpl_seg) do");
            writer.indent();
            writer.write("{:ok, key} ->");
            writer.indent();
            writer.write("val = URI.decode(seg)");
            writer.write("match_segments(rest_path, rest_tpl, Map.put(acc, key, val))");
            writer.dedent();
            writer.write("_ when seg == tpl_seg ->");
            writer.indent();
            writer.write("match_segments(rest_path, rest_tpl, acc)");
            writer.dedent();
            writer.write("_ ->");
            writer.indent();
            writer.write(":error");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp match_segments(_, _, _), do: :error");
            writer.write("");
            writer.write("defp label_name(\"{\" <> rest) do");
            writer.indent();
            writer.write("case String.split(rest, \"}\", parts: 2) do");
            writer.indent();
            writer.write("[label, \"\"] -> {:ok, label}");
            writer.write("_ -> :error");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp label_name(_), do: :error");
            writer.dedent();
            writer.write("end");
        });
    }

    static boolean serviceHasLabelBindings(Model model, ServiceShape service) {
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        for (OperationShape op : operations) {
            if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
