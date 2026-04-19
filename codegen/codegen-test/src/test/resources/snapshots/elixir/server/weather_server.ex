defmodule Weather.Server do
  @behaviour SmithyBeam.Handler
  def handle_get_current_time(request, state) do
    {:error, :not_implemented}
  end

  def handle_get_forecast(request, state) do
    {:error, :not_implemented}
  end

end
