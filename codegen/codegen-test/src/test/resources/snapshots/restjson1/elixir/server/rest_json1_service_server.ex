defmodule RestJson1Service.Server do
  @behaviour SmithyHandler
  @spec handle_echo_message(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_echo_message(_input, _context) do
    {:error, :not_implemented}
  end

  @callback echo_message(input :: EchoMessageInput.t(), context :: term()) ::
    {:ok, EchoMessageOutput.t()} | {:error, term()}

  @impl SmithyHandler
  def handle_request(operation, input, context) do
    case operation do
      :echo_message -> handle_echo_message(input, context)
      _ -> {:error, :not_found}
    end
  end
end
