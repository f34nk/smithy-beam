defmodule RestJson1Service.Server do
  @behaviour SmithyBeam.Handler
  @spec handle_echo_message(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_echo_message(request, state) do
    {:error, :not_implemented}
  end

  @callback echo_message(input :: EchoMessageInput.t(), context :: term()) ::
    {:ok, EchoMessageOutput.t()} | {:error, term()}

end
