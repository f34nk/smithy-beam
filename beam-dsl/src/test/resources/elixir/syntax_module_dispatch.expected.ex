defmodule SyntaxFixtureDispatch do
  @moduledoc "Dispatch coverage for Elixir IR rendering."

  @default_handler :default

  alias SyntaxTypes.Request, as: Request

  def dispatch(config, request) do
    handler = Map.get(config, :handler, @default_handler)
    dispatch(handler, config, request)
  end

  def dispatch(handler, _config, %Request{method: method, path: path}) when is_atom(handler) do
    {handler, method, path}
  end
end
