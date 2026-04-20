defmodule SimpleService.Server.Types.GetItemInput do
  defstruct [:id]

  @type t() :: %__MODULE__{
    id: String.t()
  }
end


defmodule SimpleService.Server.Types.GetItemOutput do
  defstruct [:name, :value]

  @type t() :: %__MODULE__{
    name: String.t(),
    value: String.t()
  }
end
