defmodule Weather.Server do
  @behaviour SmithyHandler
  @spec handle_get_current_time(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_get_current_time(_input, _context) do
    {:error, :not_implemented}
  end

  @callback get_current_time(input :: GetCurrentTimeInput.t(), context :: term()) ::
    {:ok, GetCurrentTimeOutput.t()} | {:error, term()}

  @spec handle_get_forecast(request :: term(), state :: term()) :: {:ok, term()} | {:error, term()}
  def handle_get_forecast(_input, _context) do
    {:error, :not_implemented}
  end

  @callback get_forecast(input :: GetForecastInput.t(), context :: term()) ::
    {:ok, GetForecastOutput.t()} | {:error, NoSuchResourceError.t()}

  @impl SmithyHandler
  def handle_request(operation, input, context) do
    case operation do
      :get_current_time -> handle_get_current_time(input, context)
      :get_forecast -> handle_get_forecast(input, context)
      _ -> {:error, :not_found}
    end
  end
end
