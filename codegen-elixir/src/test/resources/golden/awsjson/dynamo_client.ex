defmodule DynamoClient do
  @moduledoc "Generated Smithy client for dynamo_client"

  @type get_item_input :: %{table_name: String.t() | nil, key: String.t() | nil}
  @type get_item_output :: %{item: String.t() | nil}

  @spec new(map()) :: {:ok, map()}
  def new(config), do: {:ok, config}

  @doc "Calls the GetItem operation"
  @spec get_item(map(), map(), map()) :: {:ok, map()} | {:error, term()}
  def get_item(client, input, opts \\ %{}) do
    SmithyClient.request(client, get_item_op(input), opts)
  end

  defp get_item_op(input) do
    %SmithyClient.Operation{
      name: :get_item,
      action: "GetItem",
      http: %{method: "POST", uri: "/"},
      input: input,
      output_shape: :get_item_output,
      auth: :sigv4,
      static_headers: [{"X-Amz-Target", "DynamoService.GetItem"}],
      content_type: "application/x-amz-json-1.0",
      encoding: :json,
      decoding: :json,
      parse_error_fn: &parse_error/2
    }
  end
  defp parse_error(_, body),
    do: {:error, %{error_type: :unknown, body: body}}
end
