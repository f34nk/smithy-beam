defmodule HttpServiceRestJson1 do
  @moduledoc "REST JSON 1 codecs for smithy.beam.demo.http#HttpService (generated). Do not edit."

  alias RuntimeTypes, as: RuntimeTypes
  alias HttpServiceTypes, as: Types

  @spec encode_get_name_request(HttpServiceTypes.GetNameInput.t()) :: %RuntimeTypes.HttpRequest{}
  def encode_get_name_request(input) do
    path = "/names/" <> uri_encode(input.name)
    query = %{}
    headers = [{"Content-Type", "application/json"}]
    body = ""

    %RuntimeTypes.HttpRequest{
      method: "GET",
      path: path,
      query: query,
      headers: headers,
      body: body
    }
  end

  @doc "Decode HTTP request for smithy.beam.demo.http#GetName."
  @spec decode_get_name_request(%RuntimeTypes.HttpRequest{}, map())
    :: HttpServiceTypes.GetNameInput.t()
  def decode_get_name_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}, label_map) do
    %Types.GetNameInput{name: uri_decode(Map.get(label_map, "name"))}
  end

  def decode_get_name_response(%RuntimeTypes.HttpResponse{status: 200, headers: headers, body: body}) do
    decoded = if body == "" or Kernel.is_nil(body) do
      %{}
    else
      Jason.decode!(body)
    end
    result = {:ok, %Types.GetNameOutput{name: Map.get(decoded, "name")}}
    result
  end

  def decode_get_name_response(%RuntimeTypes.HttpResponse{status: status, headers: headers, body: body}), do: decode_get_name_response_error(status, headers, body)

  defp decode_get_name_response_error(status, _headers, body), do: {:error, {:unknown_error, status, body}}

  defp to_binary(v) when is_binary(v), do: v
  defp to_binary(v) when is_list(v), do: IO.iodata_to_binary(v)
  defp to_binary(true), do: "true"
  defp to_binary(false), do: "false"
  defp to_binary(v) when is_atom(v), do: Atom.to_string(v)
  defp to_binary(v) when is_integer(v), do: Integer.to_string(v)
  defp to_binary(v) when is_float(v), do: Float.to_string(v)

  defp encode_query_value(v) when is_boolean(v), do: Atom.to_string(v)
  defp encode_query_value(v) when is_integer(v), do: Integer.to_string(v)
  defp encode_query_value(v) when is_float(v), do: Float.to_string(v)
  defp encode_query_value(v) when is_binary(v), do: v
  defp encode_query_value(v) when is_atom(v), do: Atom.to_string(v)

  defp uri_encode(value), do: URI.encode(Kernel.to_string(value))

  defp uri_decode(nil), do: nil
  defp uri_decode(value), do: URI.decode(value)

  defp decode_query_param(nil), do: nil
  defp decode_query_param(true), do: true
  defp decode_query_param(false), do: false
  defp decode_query_param("true"), do: true
  defp decode_query_param("false"), do: false
  defp decode_query_param(value), do: value

  defp prefix_headers_to_list(_prefix, nil), do: []

  defp prefix_headers_to_list(prefix, map) when is_map(map) do
    Enum.map(map, fn {k, v} -> {prefix <> k, Kernel.to_string(v)} end)
  end

  defp prefix_headers_from_list(headers, prefix) do
    headers
    |> Enum.filter(fn {name, _} -> String.starts_with?(name, prefix) end)
    |> Map.new(fn {name, val} ->
      {binary_part(name, byte_size(prefix), byte_size(name) - byte_size(prefix)), val}
    end)
    |> case do
      map when map == %{} -> nil
      map -> map
    end
  end

  defp decode_json_body(""), do: %{}

  defp decode_json_body(body) do
    case Jason.decode(body) do
      {:ok, map} when is_map(map) -> map
      _ -> %{}
    end
  end

  defp header_value(headers, name) do
    case List.keyfind(headers, name, 0) do
      {_, v} -> header_value_raw(v)
      nil -> nil
    end
  end

  defp header_value_raw(v) when is_binary(v), do: v
  defp header_value_raw([v | _]) when is_binary(v), do: v
  defp header_value_raw(v), do: v

  defp content_type_matches(headers, expected) do
    case List.keyfind(headers, "Content-Type", 0) do
      {_, ct} when ct == expected ->
        :ok

      {_, ct} when is_binary(ct) ->
        if ct_base(ct) == ct_base(expected) do
          :ok
        else
          {:error, {:invalid_content_type, ct}}
        end

      _ ->
        {:error, {:invalid_content_type, :nil}}
    end
  end

  defp ct_base(ct) do
    case String.split(ct, ";") do
      [base | _] -> base
      _ -> ct
    end
  end

  defp decode_sparse_list(nil), do: nil

  defp decode_sparse_list(list) when is_list(list) do
    Enum.map(list, fn
      nil -> nil
      v -> v
    end)
  end

  defp decode_list(nil), do: nil
  defp decode_list(list) when is_list(list), do: Enum.reject(list, &is_nil/1)

  defp decode_sparse_map(nil), do: nil

  defp decode_sparse_map(map) when is_map(map) do
    Map.new(map, fn
      {k, nil} -> {k, nil}
      {k, v} -> {k, v}
    end)
  end

  defp encode_sparse_list(nil), do: nil

  defp encode_sparse_list(list) when is_list(list) do
    Enum.map(list, fn
      nil -> nil
      v -> v
    end)
  end

  defp encode_sparse_map(nil), do: nil

  defp encode_sparse_map(map) when is_map(map) do
    Map.new(map, fn
      {k, nil} -> {k, nil}
      {k, v} -> {k, v}
    end)
  end

  defp encode_timestamp_epoch_seconds(dt = %DateTime{}), do: DateTime.to_unix(dt)
  defp encode_timestamp_epoch_seconds(nil), do: nil

  defp encode_timestamp_date_time(dt = %DateTime{}), do: DateTime.to_iso8601(dt)
  defp encode_timestamp_date_time(nil), do: nil

  defp decode_timestamp_epoch_seconds(nil), do: nil
  defp decode_timestamp_epoch_seconds(v) when is_number(v), do: DateTime.from_unix!(Kernel.trunc(v))

  defp decode_timestamp_date_time(nil), do: nil
  defp decode_timestamp_date_time(v) when is_number(v), do: DateTime.from_unix!(Kernel.trunc(v))

  defp decode_timestamp_date_time(v) when is_binary(v) do
    case DateTime.from_iso8601(v) do
      {:ok, dt, _} -> dt
      _ -> nil
    end
  end

  defp generate_uuid do
    <<a::32, b::16, _::4, c::12, _::2, d::14, e::48>> = :crypto.strong_rand_bytes(16)
    <<a::32, b::16, 4::4, c::12, 2::2, d::14, e::48>>
    |> Base.encode16(case: :lower)
    |> then(fn hex ->
      <<part_a::8, part_b::4, part_c::4, part_d::4, part_e::12>> = hex
      "#{part_a}-#{part_b}-#{part_c}-#{part_d}-#{part_e}"
    end)
  end
end
