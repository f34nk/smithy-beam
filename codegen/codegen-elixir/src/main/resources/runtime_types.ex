@type endpoint_rule_set :: map()
@type http_request :: %__MODULE__.HttpRequest{}
defmodule HttpRequest do
  defstruct method: "GET", path: "/", query: %{}, headers: [], body: "", host: nil, stream: nil
end
defmodule HttpResponse do
  defstruct status: 200, headers: [], body: "", stream: nil
end
