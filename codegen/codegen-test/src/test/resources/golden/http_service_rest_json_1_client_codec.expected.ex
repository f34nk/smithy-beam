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

  defp uri_encode(value), do: URI.encode(Kernel.to_string(value))

  defp uri_decode(nil), do: nil
  defp uri_decode(value), do: URI.decode(value)
end
