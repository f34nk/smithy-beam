defmodule Weather.Server.Types.GetWeatherInput do
  defstruct [:city]

  @type t() :: %__MODULE__{
    city: String.t()
  }
end


defmodule Weather.Server.Types.GetWeatherOutput do
  defstruct [:temperature]

  @type t() :: %__MODULE__{
    temperature: float()
  }
end


defmodule Weather.Server.Types.ListCitiesInput do
  defstruct [:filter, :custom_header, :next_token, :max_results]

  @type t() :: %__MODULE__{
    filter: String.t(),
    custom_header: String.t(),
    next_token: String.t(),
    max_results: integer()
  }
end


defmodule Weather.Server.Types.ListCitiesOutput do
  defstruct [:next_token, :cities]

  @type t() :: %__MODULE__{
    next_token: String.t(),
    cities: [String.t()]
  }
end
