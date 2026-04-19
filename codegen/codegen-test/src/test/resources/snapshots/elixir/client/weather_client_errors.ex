defmodule Weather.Client.Errors.NoSuchResourceError do
    defexception [:resource_type]

    @type t() :: %__MODULE__{
        resource_type: String.t()
    }
end
