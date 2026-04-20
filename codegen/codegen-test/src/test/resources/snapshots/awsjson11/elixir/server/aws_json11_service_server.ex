defmodule AwsJson11Service.Server do
  @behaviour SmithyBeam.Handler
  @spec handle_describe_item(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_describe_item(request, state) do
    {:error, :not_implemented}
  end

  @callback describe_item(input :: DescribeItemInput.t(), context :: term()) ::
    {:ok, DescribeItemOutput.t()} | {:error, term()}

end
