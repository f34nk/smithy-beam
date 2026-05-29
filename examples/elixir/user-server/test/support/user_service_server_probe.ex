defmodule UserServiceServerProbe do
  @moduledoc false

  @spec handle_get_user(term(), UserTypes.GetUserInput.t(), term()) ::
          {:ok, UserTypes.GetUserOutput.t()} | {:error, term()}
  def handle_get_user(_ctx, input, _meta) do
    {:ok, %UserTypes.GetUserOutput{user: %{"userId" => input.user_id}}}
  end
end
