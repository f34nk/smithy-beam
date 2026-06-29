defmodule ComplianceServiceComplianceTests do
  use ExUnit.Case, async: true

  alias ComplianceServiceTypes, as: Types
  alias ComplianceServiceRestJson1
  alias ComplianceServiceRestJson1, as: ServerCodec
  alias RuntimeTypes, as: RuntimeTypes

  test "GetItemRequest" do
    request = ComplianceServiceRestJson1.encode_get_item_request(%ComplianceServiceTypes.GetItemInput.t(){id: "abc"})
    assert request.method == "GET"
    assert request.path == "/items/abc"
    assert_headers(%{"X-Test" => "1"}, request.headers)
  end

  test "GetItemResponse" do
    response = %RuntimeTypes.HttpResponse{
      status: 200,
      headers: headers_to_list(%{"Content-Type" => "application/json"}),
      body: "{\"name\": \"widget\"}"
    }

    {:ok, output} = ComplianceServiceRestJson1.decode_get_item_response(response)
    assert output.name == "widget"
  end

  defp headers_to_list(headers) do
    Enum.map(headers, fn {k, v} -> {k, v} end)
  end

  defp query_params_to_map([]), do: %{}
  defp query_params_to_map([param | rest]) do
    Map.merge(query_param(param), query_params_to_map(rest))
  end

  defp query_param(param) do
    case String.split(param, "=", parts: 2) do
      [key, value] -> %{key => value}
      [key] -> %{key => ""}
    end
  end

  defp assert_headers(expected, actual) do
    Enum.each(expected, fn {key, value} ->
      assert Keyword.get(actual, key) == value
    end)
  end

  defp assert_query_params(expected, query) do
    Enum.each(expected, fn param ->
      case String.split(param, "=", parts: 2) do
        [key, value] -> assert Map.fetch!(query, key) == value
        [key] -> assert Map.has_key?(query, key)
      end
    end)
  end
end
