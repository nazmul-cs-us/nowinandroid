%% Test double for account existence tests only; never installed in MongooseIM.
-module(jid).
-export([from_binary/1, to_binary/1]).
-export_type([luser/0, lserver/0, lresource/0]).
-type luser() :: binary().
-type lserver() :: binary().
-type lresource() :: binary().
-include_lib("jid/include/jid.hrl").
from_binary(B) ->
    [Bare | Resource] = binary:split(B, <<"/">>),
    {User, Server} = case binary:split(Bare, <<"@">>) of
        [S] -> {<<>>, S};
        [U, S] -> {U, S}
    end,
    R = case Resource of [] -> <<>>; [X] -> X end,
    #jid{luser = User, lserver = Server, lresource = R}.
to_binary(#jid{luser = U, lserver = S, lresource = R}) ->
    Bare = case U of <<>> -> S; _ -> <<U/binary, "@", S/binary>> end,
    case R of <<>> -> Bare; _ -> <<Bare/binary, "/", R/binary>> end.
