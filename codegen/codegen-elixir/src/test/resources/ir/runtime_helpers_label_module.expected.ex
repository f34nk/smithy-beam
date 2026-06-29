defmodule RuntimeHelpers do
  @moduledoc "Generated runtime helpers for smithy.beam.demo.labels#LabelService. Do not edit."

  @spec parse_labels(String.t(), String.t()) :: {:ok, map()} | {:error, :path_mismatch}
  def parse_labels(path, template) do
    case match_segments(segments(path), segments(template), %{}) do
      {:ok, labels} -> {:ok, labels}

      _ -> {:error, :path_mismatch}
    end
  end

  defp segments(path) do
    path
    |> String.split("/", trim: true)
  end

  defp match_segments([], [], acc), do: {:ok, acc}
  defp match_segments([seg | rest_path], [tpl_seg | rest_tpl], acc) do
    case label_name(tpl_seg) do
      {:ok, key} ->
        val = URI.decode(seg)
        match_segments(rest_path, rest_tpl, Map.put(acc, key, val))

      _ when seg == tpl_seg -> match_segments(rest_path, rest_tpl, acc)

      _ -> :error
    end
  end
  defp match_segments(_, _, _), do: :error

  defp label_name("{" <> rest) do
    case String.split(rest, "}", parts: 2) do
      [label, ""] -> {:ok, label}
      _ -> :error
    end
  end
  defp label_name(_), do: :error
end
