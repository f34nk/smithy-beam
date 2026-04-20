-module(smithy_event_stream).

%% Bidirectional event stream helper for Smithy `@streaming' unions.
%%
%% This is a Phase 2 stub that exposes the public surface required by the
%% generated `<op>_stream/3' wrappers; the actual transport (HTTP/2 framing,
%% AWS event-stream binary encoding, websocket fallback) lands in Phase 3
%% together with the in-process language runners under
%% `codegen-test/runner/'.
%%
%% Generated client code calls into this module as follows:
%%
%%   smithy_event_stream:start_stream(Client, Input, #{
%%       initial_message    => InitialMessage,
%%       encode_event       => fun(Event) -> Frame end,
%%       decode_event       => fun(Frame) -> Event end,
%%       on_event           => fun(Event) -> ok end,
%%       on_error           => fun(Reason) -> ok end
%%   }).
%%
%% On success the function returns `{ok, StreamRef}', where `StreamRef' is
%% an opaque term that the caller can later pass to `send_event/2',
%% `recv_event/1' or `close_stream/1'. Until the runtime is implemented all
%% four functions return `{error, not_implemented}'.

-export([
    start_stream/3,
    send_event/2,
    recv_event/1,
    close_stream/1
]).

-export_type([
    stream_ref/0,
    stream_opts/0
]).

-opaque stream_ref() :: reference().
-type stream_opts() :: map().

%% @doc Opens a new bidirectional event stream against the operation
%% described by `Input' on the connection carried by `Client'.
%%
%% The runtime is not yet implemented; the function exists so the
%% generated code links and so smoke tests can confirm the shape of the
%% wrapper. Returns `{error, not_implemented}' until Phase 3.
-spec start_stream(Client :: map(), Input :: term(), Opts :: stream_opts()) ->
    {ok, stream_ref()} | {error, term()}.
start_stream(_Client, _Input, _Opts) ->
    {error, not_implemented}.

%% @doc Sends an event on the input side of an open bidirectional stream.
%% Returns `{error, not_implemented}' until Phase 3.
-spec send_event(stream_ref(), term()) -> ok | {error, term()}.
send_event(_StreamRef, _Event) ->
    {error, not_implemented}.

%% @doc Receives the next event from the output side of an open stream.
%% Returns `{ok, end_of_stream}' when the remote half has closed.
%% Returns `{error, not_implemented}' until Phase 3.
-spec recv_event(stream_ref()) -> {ok, term()} | {ok, end_of_stream} | {error, term()}.
recv_event(_StreamRef) ->
    {error, not_implemented}.

%% @doc Closes both halves of an open stream.
%% Returns `{error, not_implemented}' until Phase 3.
-spec close_stream(stream_ref()) -> ok | {error, term()}.
close_stream(_StreamRef) ->
    {error, not_implemented}.
