-module(user_server_probe).

-include("user_types.hrl").

-export([handle_get_user/3]).

%% Test double: echoes decoded input user id into the output user map.
handle_get_user(_Ctx, #get_user_input{user_id = UserId}, _Meta) ->
    {ok, #get_user_output{
        user = #{<<"userId">> => UserId}
    }}.
