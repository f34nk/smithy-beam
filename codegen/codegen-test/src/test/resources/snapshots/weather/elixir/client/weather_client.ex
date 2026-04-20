defmodule Weather.Client do
  @spec get_current_time(map(), GetCurrentTimeInput.t()) ::
    {:ok, GetCurrentTimeOutput.t()} | {:error, term()}
  def get_current_time(config, input) do
    {:error, :not_implemented}
  end


  @spec get_forecast(map(), GetForecastInput.t()) ::
    {:ok, GetForecastOutput.t()} | {:error, NoSuchResourceError.t()}
  def get_forecast(config, input) do
    {:error, :not_implemented}
  end


end
