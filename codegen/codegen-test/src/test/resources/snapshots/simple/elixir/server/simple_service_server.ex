defmodule SimpleService.Server do
  @behaviour SmithyHandler
  @spec handle_get_item(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_get_item(_input, _context) do
    {:error, :not_implemented}
  end

  @callback get_item(input :: GetItemInput.t(), context :: term()) ::
    {:ok, GetItemOutput.t()} | {:error, term()}

  @impl SmithyHandler
  def handle_request(operation, input, context) do
    case operation do
      :get_item -> handle_get_item(input, context)
      _ -> {:error, :not_found}
    end
  end
end
