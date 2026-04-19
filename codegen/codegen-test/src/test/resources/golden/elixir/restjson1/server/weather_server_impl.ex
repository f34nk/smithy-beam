defmodule Weather.Server.Impl do
  @moduledoc false
  @behaviour Weather.Server.Handler

  # This file will NOT be overwritten. Add your business logic here.

  alias Weather.Server.Types.GetWeatherInput
  alias Weather.Server.Types.GetWeatherOutput
  alias Weather.Server.Types.ListCitiesInput
  alias Weather.Server.Types.ListCitiesOutput

  @impl true
  def get_weather(%GetWeatherInput{} = _input, _ctx), do: {:error, :not_implemented}

  @impl true
  def list_cities(%ListCitiesInput{} = _input, _ctx), do: {:error, :not_implemented}
end
