-module(smithy_validator).

%% Input validation helpers for generated Smithy server dispatchers.
%% Validates required fields and formats human-readable error messages.

-export([
    validate/2,
    format/1
]).

%%%===================================================================
%%% API Functions
%%%===================================================================

%% @doc Validate that all required fields are present in an input map.
%% RequiredFields is a list of binary key names.
%% Returns ok when all required fields are present, or
%% {error, {missing_required_fields, Missing}} listing the absent keys.
-spec validate(Input :: map(), RequiredFields :: [binary()]) ->
    ok | {error, {missing_required_fields, [binary()]}}.
validate(Input, RequiredFields) ->
    Missing = [F || F <- RequiredFields, not maps:is_key(F, Input)],
    case Missing of
        []    -> ok;
        [_|_] -> {error, {missing_required_fields, Missing}}
    end.

%% @doc Format a validation error term into a human-readable binary message.
-spec format({missing_required_fields, [binary()]}) -> binary().
format({missing_required_fields, Fields}) ->
    FieldList = iolist_to_binary(lists:join(<<", ">>, Fields)),
    <<"Missing required fields: ", FieldList/binary>>.
