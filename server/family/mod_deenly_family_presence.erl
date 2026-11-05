%% Ephemeral, indexed presence. Membership/call data remains in the durable Family table.
-module(mod_deenly_family_presence).
-export([init/0, epoch/0, state/2, update/5, remove/3, accept/3, revoke/3, sweep/0]).
-define(T, deenly_family_presence).
-define(LEASE_MS, 75000).

init() ->
    case mnesia:create_table(?T, [{attributes, [key, user, value]},
                                 {ram_copies, [node()]}, {index, [user]}]) of
        {atomic, ok} -> ok;
        {aborted, {already_exists, ?T}} -> ok;
        Error -> error({family_presence_unavailable, Error})
    end,
    ok = mnesia:wait_for_tables([?T], 10000),
    {atomic, ok} = mnesia:transaction(fun() ->
        case mnesia:read(?T, epoch, write) of
            [] -> mnesia:write({?T, epoch, metadata, base64:encode(crypto:strong_rand_bytes(18))});
            _ -> ok
        end,
        mnesia:foldl(fun
            ({deenly_family, {family, D, _}, #{a := A, b := B, status := <<"accepted">>}}, _) -> accept(D, A, B);
            (_, _) -> ok
        end, ok, deenly_family)
    end), ok.

epoch() -> [{?T, epoch, _, Value}] = mnesia:read(?T, epoch), Value.
state(D, U) ->
    case mnesia:read(?T, {account, D, U}) of
        [{?T, _, _, Value}] -> Value;
        [] -> #{<<"presence">> => <<"unknown">>, <<"presenceVersion">> => 0}
    end.

accept(D, A, B) ->
    mnesia:write({?T, {family, D, A, B}, {D, A}, B}),
    mnesia:write({?T, {family, D, B, A}, {D, B}, A}).
revoke(D, A, B) ->
    mnesia:delete({?T, {family, D, A, B}}),
    mnesia:delete({?T, {family, D, B, A}}).

%% Called inside the same domain transaction as family eligibility changes.
%% Identity is always taken from the authenticated full JID, never a client-supplied peer.
update(D, U, F, Active, Sequence) when is_boolean(Active), is_integer(Sequence), Sequence >= 0 ->
    K = {session, D, F},
    Current = mnesia:read(?T, K, write),
    %% An in-flight heartbeat must not put a backgrounded device back online.
    case Current of
        [{?T, _, _, #{sequence := Old}}] when Old > Sequence -> [];
        _ ->
            mnesia:write({?T, K, {D, U}, #{active => Active, sequence => Sequence,
                                          until => now_ms() + ?LEASE_MS}}),
            aggregate(D, U)
    end.
remove(D, U, F) ->
    case mnesia:read(?T, {session, D, F}) of
        [] -> [];
        _ -> mnesia:delete({?T, {session, D, F}}), aggregate(D, U)
    end.
aggregate(D, U) ->
    Rows = mnesia:index_read(?T, {D, U}, user),
    Now = now_ms(),
    Online = lists:any(fun
        ({?T, {session, _, _}, _, #{active := Active, until := Until}}) -> Active andalso Until > Now;
        (_) -> false
    end, Rows),
    Status = case Online of true -> <<"online">>; false -> <<"offline">> end,
    Before = state(D, U),
    case maps:get(<<"presence">>, Before) =:= Status of
        true -> [];
        false ->
            Value = #{<<"presence">> => Status,
                      <<"presenceVersion">> => maps:get(<<"presenceVersion">>, Before) + 1},
            mnesia:write({?T, {account, D, U}, {D, U}, Value}),
            Event = Value#{<<"event">> => <<"presence">>, <<"jid">> => U,
                           <<"presenceEpoch">> => epoch()},
            [{Peer, Event} || {?T, {family, _, _, _}, _, Peer} <- Rows]
    end.

%% The existing five-second server sweep bounds ghost availability after a broken network.
%% Normal disconnects use the MongooseIM hooks and do not wait for expiry.
sweep() ->
    Now = now_ms(),
    Expired = mnesia:activity(async_dirty, fun() ->
        mnesia:foldl(fun
            ({?T, {session, D, _}, {D, U}, #{until := Until}}, Acc) when Until =< Now -> [{D, U} | Acc];
            (_, Acc) -> Acc
        end, [], ?T)
    end),
    lists:flatmap(fun({D, U}) ->
        case mnesia:transaction(fun() ->
            mnesia:read(deenly_family, {lock, D}, write),
            lists:foreach(fun
                ({?T, {session, _, _} = K, _, #{until := Until}}) when Until =< Now -> mnesia:delete({?T, K});
                (_) -> ok
            end, mnesia:index_read(?T, {D, U}, user)),
            aggregate(D, U)
        end) of
            {atomic, Events} -> [{D, Events}];
            _ -> []
        end
    end, lists:usort(Expired)).
now_ms() -> erlang:system_time(millisecond).
