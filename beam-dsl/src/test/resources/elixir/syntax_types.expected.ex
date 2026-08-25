defmodule SyntaxTypes do
  @moduledoc false

  @default_handler :default
  @handlers_key :handlers

  defmodule Item do
    @type t :: %__MODULE__{
            name: binary() | nil,
            count: integer() | nil
          }

    defstruct [:name, :count]
  end

  defmodule Request do
    @type t :: %__MODULE__{
            method: binary(),
            path: binary(),
            query: %{binary() => binary()},
            headers: [{binary(), binary()}],
            body: iodata(),
            host: binary() | nil
          }

    defstruct method: "GET", path: "/", query: %{}, headers: [], body: "", host: nil
  end

  defmodule Response do
    @type t :: %__MODULE__{
            status: non_neg_integer(),
            headers: [{binary(), binary()}],
            body: iodata()
          }

    defstruct status: 200, headers: [], body: ""
  end

  defmodule TaggedError do
    @type t :: %__MODULE__{
            code: atom(),
            message: binary() | nil
          }

    defstruct [:code, :message]
  end

  @type client_config :: %{binary() => term()}

  @type credentials :: %{
          required(:access_key) => binary(),
          required(:secret_key) => binary(),
          optional(:token) => binary() | nil
        }
end
