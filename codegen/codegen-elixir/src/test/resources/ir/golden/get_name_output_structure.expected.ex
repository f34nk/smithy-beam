
defmodule GetNameOutput do
  @moduledoc "structure GetNameOutput"

  @type t :: %__MODULE__{
          name: HttpServiceClient.name() | nil
  }

  defstruct [:name]
end
