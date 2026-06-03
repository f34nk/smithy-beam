defmodule UserServiceServerProbe do
  @moduledoc false

  @spec handle_get_user(term(), UserServiceTypes.GetUserInput.t(), term()) ::
          {:ok, UserServiceTypes.GetUserOutput.t()} | {:error, term()}
  def handle_get_user(_ctx, input, _meta) do
    {:ok, %UserServiceTypes.GetUserOutput{user: %{"userId" => input.user_id}}}
  end
end
