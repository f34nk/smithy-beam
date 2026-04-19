defmodule Weather.Client.Types.GetCurrentTimeInput do
    defstruct []

    @type t() :: %__MODULE__{}
end


defmodule Weather.Client.Types.GetCurrentTimeOutput do
    defstruct [:time]

    @type t() :: %__MODULE__{
        time: DateTime.t()
    }
end


defmodule Weather.Client.Types.GetForecastInput do
    defstruct [:city_id]

    @type t() :: %__MODULE__{
        city_id: String.t()
    }
end


defmodule Weather.Client.Types.GetForecastOutput do
    defstruct [:chance_of_rain]

    @type t() :: %__MODULE__{
        chance_of_rain: float()
    }
end
