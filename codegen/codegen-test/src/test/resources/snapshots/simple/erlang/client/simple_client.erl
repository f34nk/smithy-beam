-module(simple_client).
-export([get_item/2, errors/0, is_error/1, error_to_atom/1]).

-spec get_item(Client :: map(), Input :: get_item_input()) ->
    {ok, get_item_output()} | {error, term()}.
get_item(Config, Input) ->
    {error, not_implemented}.


errors() ->
    [].

is_error({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->
    lists:member(element(1, Err), errors());
is_error(_) ->
    false.

error_to_atom({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->
    case lists:member(element(1, Err), errors()) of
        true -> element(1, Err);
        false -> unknown_error
    end;
error_to_atom(_) ->
    unknown_error.
