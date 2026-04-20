defmodule RestJson1Service.Server.Types.EchoMessageInput do
  defstruct [:message]

  @type t() :: %__MODULE__{
    message: String.t()
  }
end


defmodule RestJson1Service.Server.Types.EchoMessageOutput do
  defstruct [:message]

  @type t() :: %__MODULE__{
    message: String.t()
  }
end
