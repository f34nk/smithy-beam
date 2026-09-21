defmodule RuntimeHttpClient.Request do
  @moduledoc false

  @type t :: %__MODULE__{
          method: atom(),
          url: String.t(),
          headers: [{String.t(), String.t()}],
          body: iodata()
        }

  defstruct method: nil,
            url: "",
            headers: [],
            body: ""
end
