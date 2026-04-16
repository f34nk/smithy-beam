defmodule SmithyQuery do
  @moduledoc """
  AWS Query-protocol encoding for Smithy-generated Elixir clients.

  Ported from `aws_query.erl`. Used by services like EC2, SQS, SNS, and
  CloudFormation. Encodes request parameters as form-urlencoded bodies with
  flattened dot-notation keys and 1-based list indexing.
  """

  @doc """
  Encode an AWS Query request body.

  Returns a URL-encoded binary ready for the HTTP body.
  """
  @spec encode(String.t(), map()) :: binary()
  def encode(action, params) when is_map(params), do: encode(action, params, nil)

  @spec encode(String.t(), map(), String.t() | nil) :: binary()
  def encode(action, params, version) when is_map(params) do
    flat = flatten_params(params)
    base = [{"Action", to_string(action)}]

    with_version =
      if version, do: [{"Version", to_string(version)} | base], else: base

    encode_query_string(with_version ++ flat)
  end

  @doc """
  Strip the outer `<XyzResponse>` and optional inner `<XyzResult>` wrappers
  that the AWS Query protocol always adds around actual response data.
  """
  @spec unwrap_response(map()) :: {:ok, map()}
  def unwrap_response(outer) when is_map(outer) and map_size(outer) == 1 do
    [{_key, inner}] = Map.to_list(outer)

    if is_map(inner) do
      result_keys = Enum.filter(Map.keys(inner), fn k ->
        is_binary(k) and byte_size(k) >= 6 and binary_part(k, byte_size(k) - 6, 6) == "Result"
      end)

      case result_keys do
        [result_key] -> {:ok, Map.fetch!(inner, result_key)}
        _            -> {:ok, inner}
      end
    else
      {:ok, %{}}
    end
  end

  def unwrap_response(map), do: {:ok, map}

  @doc false
  def flatten_params(params) when is_map(params), do: flatten_params(params, "")

  @doc false
  def flatten_params(params, prefix) when is_map(params) do
    Enum.flat_map(params, fn {key, value} ->
      key_str  = to_string(key)
      full_key = if prefix == "", do: key_str, else: "#{prefix}.#{key_str}"
      flatten_value(full_key, value)
    end)
  end

  @doc false
  def flatten_value(key, value) when is_map(value) do
    flatten_params(value, key)
  end

  def flatten_value(_key, []), do: []

  def flatten_value(key, value) when is_list(value) do
    if string_charlist?(value) do
      [{key, List.to_string(value)}]
    else
      value
      |> Enum.with_index(1)
      |> Enum.flat_map(fn {item, idx} ->
        flatten_value("#{key}.#{idx}", item)
      end)
    end
  end

  def flatten_value(key, true),  do: [{key, "true"}]
  def flatten_value(key, false), do: [{key, "false"}]
  def flatten_value(key, nil),   do: [{key, ""}]
  def flatten_value(key, value) when is_atom(value),    do: [{key, Atom.to_string(value)}]
  def flatten_value(key, value) when is_binary(value),  do: [{key, value}]
  def flatten_value(key, value) when is_integer(value), do: [{key, Integer.to_string(value)}]
  def flatten_value(key, value) when is_float(value),   do: [{key, Float.to_string(value)}]

  # ---------------------------------------------------------------------------
  # Private helpers
  # ---------------------------------------------------------------------------

  defp encode_query_string(pairs) do
    pairs
    |> Enum.map(fn {k, v} ->
      URI.encode(to_string(k), &URI.char_unreserved?/1) <>
        "=" <>
        URI.encode(to_string(v), &URI.char_unreserved?/1)
    end)
    |> Enum.join("&")
  end

  defp string_charlist?([]), do: true
  defp string_charlist?([h | t]) when is_integer(h) and h >= 0 and h <= 0x10FFFF, do: string_charlist?(t)
  defp string_charlist?(_), do: false
end
