defmodule RestXmlService.Server do
  @behaviour SmithyHandler
  @spec handle_list_buckets(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_list_buckets(_input, _context) do
    {:error, :not_implemented}
  end

  @callback list_buckets(input :: ListBucketsInput.t(), context :: term()) ::
    {:ok, ListBucketsOutput.t()} | {:error, term()}

  @impl SmithyHandler
  def handle_request(operation, input, context) do
    case operation do
      :list_buckets -> handle_list_buckets(input, context)
      _ -> {:error, :not_found}
    end
  end
end
