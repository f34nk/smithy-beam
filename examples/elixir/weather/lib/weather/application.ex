defmodule Weather.Application do
  @moduledoc """
  OTP application entry point for the Weather example.

  Referenced from `mix.exs` via `mod: {Weather.Application, []}` so that
  `mix run`, `iex -S mix`, and `mix test` boot a supervision tree for us.

  The supervisor currently starts no children — wire your HTTP endpoint
  (e.g. Bandit serving a Plug that calls `Weather.Server` and dispatches
  to `Weather.Handler`) by adding entries to the `children` list below.
  """

  use Application

  @impl Application
  def start(_type, _args) do
    children = []

    opts = [strategy: :one_for_one, name: Weather.Supervisor]
    Supervisor.start_link(children, opts)
  end
end
