defmodule AwsJson11Service.Server do
  @behaviour SmithyHandler
  @spec handle_describe_item(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_describe_item(_input, _context) do
    {:error, :not_implemented}
  end

  @callback describe_item(input :: DescribeItemInput.t(), context :: term()) ::
    {:ok, DescribeItemOutput.t()} | {:error, term()}

  @impl SmithyHandler
  def handle_request(operation, input, context) do
    case operation do
      :describe_item -> handle_describe_item(input, context)
      _ -> {:error, :not_found}
    end
  end
end
