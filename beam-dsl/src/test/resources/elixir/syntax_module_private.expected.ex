defmodule SyntaxFixturePrivate do
  @moduledoc "Private helper coverage for Elixir IR rendering."

  @handlers_key :handlers

  alias SyntaxTypes.Request, as: Request

  defp parse_header("[" <> _ = line), do: String.trim(line)
  defp parse_header(line), do: String.trim(line)

  defp flatten_pairs(map) when is_map(map) do
    map
    |> Map.to_list()
    |> Enum.with_index(1)
    |> Enum.flat_map(fn {i, {key, v}} ->
      if is_nil(v) do
        []
      else
        flatten_entry("#{key}.#{i}", v)
      end
    end)
  end

  defp flatten_entry(_key, nil), do: []

  defp flatten_entry(key, value) when is_map(value) do
    [{key, value}]
  end

  defp flatten_entry(key, value), do: [{key, value}]

  defp content_type_matches(headers, expected) do
    case List.keyfind(headers, "content-type", 0) do
      {_, ^expected} ->
        true

      {_, ct} when is_binary(ct) ->
        ct_base(ct) == ct_base(expected)

      _ ->
        false
    end
  end

  defp ct_base(ct) do
    case String.split(ct, ";") do
      [base | _] -> base
      _ -> ct
    end
  end

  defp update_request(req, host), do: %Request{req | host: host}

  defp dispatch_handler(fun, ctx, input, meta) do
    handlers = Map.get(%{}, @handlers_key, %{})

    case Map.get(handlers, fun) do
      handler when is_function(handler, 3) -> handler.(ctx, input, meta)
      _ -> {:error, :not_implemented}
    end
  end

  defp decode_sparse_map(nil), do: nil

  defp decode_sparse_map(map) when is_map(map) do
    Map.new(map, fn
      {k, nil} -> {k, nil}
      {k, v} -> {k, v}
    end)
  end

  defp decode_list(nil), do: nil
  defp decode_list(list) when is_list(list), do: Enum.reject(list, &is_nil/1)

  defp decode_json_body(""), do: %{}

  defp decode_json_body(body) do
    case Jason.decode(body) do
      {:ok, map} when is_map(map) -> map
      _ -> %{}
    end
  end

  defp merge_params(config, params) do
    config_params = config_to_params(config)
    client_params = client_params(config)
    Map.merge(Map.merge(config_params, client_params), params)
  end

  defp config_to_params(config) do
    case Map.get(config, :region) do
      nil -> %{}
      value -> %{"Region" => value}
    end
  end

  defp client_params(config) do
    Map.merge(
      optional_param(config, :region, "Region"),
      optional_param(config, :bucket, "Bucket")
    )
  end

  defp optional_param(config, key, param_key) do
    case Map.get(config, key) do
      nil -> %{}
      value -> %{param_key => value}
    end
  end

  defp prefix_headers_to_list(_prefix, nil), do: []

  defp prefix_headers_to_list(prefix, map) when is_map(map) do
    Enum.map(map, fn {h, v} -> {prefix <> h, to_binary(v)} end)
  end

  defp prefix_headers_from_list(headers, prefix) do
    headers
    |> Enum.filter(fn {name, _} -> byte_size(name) > byte_size(prefix) end)
    |> Enum.filter(fn {name, _} -> binary_part(name, 0, byte_size(prefix)) == prefix end)
    |> Map.new(fn {name, val} ->
      {binary_part(name, byte_size(prefix), byte_size(name) - byte_size(prefix)), val}
    end)
    |> case do
      map when map == %{} -> nil
      map -> map
    end
  end

  defp to_binary(v) when is_binary(v), do: v
  defp to_binary(v) when is_atom(v), do: Atom.to_string(v)
  defp to_binary(v) when is_integer(v), do: Integer.to_string(v)
end
