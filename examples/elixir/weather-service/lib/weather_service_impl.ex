defmodule WeatherService.Impl do
  @moduledoc false
  @behaviour WeatherService.Handler

  # This file will NOT be overwritten. Add your business logic here.

  @spec get_weather(map(), map()) :: {:ok, map()} | {:error, term()}
  def get_weather(%{city: _city}, _ctx) do
    {:ok, %{"temperature" => 22.5, "unit" => "Celsius"}}
  end

  @spec create_weather_report(map(), map()) :: {:ok, map()} | {:error, term()}
  def create_weather_report(%{city: city, temperature: temp, unit: unit}, _ctx) do
    report_id = "rpt-#{city}-#{temp}-#{unit}"
    {:ok, %{"reportId" => report_id}}
  end
end
