defmodule RestXmlService.Server.Types.ListBucketsInput do
  defstruct [:prefix]

  @type t() :: %__MODULE__{
    prefix: String.t()
  }
end


defmodule RestXmlService.Server.Types.ListBucketsOutput do
  defstruct [:buckets]

  @type t() :: %__MODULE__{
    buckets: [String.t()]
  }
end
