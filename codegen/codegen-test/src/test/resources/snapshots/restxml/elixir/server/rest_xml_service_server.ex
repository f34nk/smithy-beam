defmodule RestXmlService.Server do
  @behaviour SmithyBeam.Handler
  @spec handle_list_buckets(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_list_buckets(request, state) do
    {:error, :not_implemented}
  end

  @callback list_buckets(input :: ListBucketsInput.t(), context :: term()) ::
    {:ok, ListBucketsOutput.t()} | {:error, term()}

end
