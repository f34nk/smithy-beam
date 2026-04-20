defmodule SimpleService.Server do
  @behaviour SmithyBeam.Handler
  @spec handle_get_item(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_get_item(request, state) do
    {:error, :not_implemented}
  end

  @callback get_item(input :: GetItemInput.t(), context :: term()) ::
    {:ok, GetItemOutput.t()} | {:error, term()}

end
