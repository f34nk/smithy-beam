defmodule AwsJson11Service.Client.Types.DescribeItemInput do
  defstruct [:item_id]

  @type t() :: %__MODULE__{
    item_id: String.t()
  }
end


defmodule AwsJson11Service.Client.Types.DescribeItemOutput do
  defstruct [:name, :description]

  @type t() :: %__MODULE__{
    name: String.t(),
    description: String.t()
  }
end
