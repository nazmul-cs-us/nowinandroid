%% Ephemeral, indexed presence. Membership/call data remains in the durable Family table.
-module(mod_deenly_family_presence).
-export([init/0, epoch/0, state/2, update/5, remove/3, accept/3, revoke/3, purge/2, sweep/0]).
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
state(D, U) -> maps:remove(last_active_at, account(D, U)).
account(D, U) ->
    case mnesia:read(?T, {account, D, U}) of
        [{?T, _, _, Value}] -> Value;
        [] -> case mnesia:read(deenly_family, {last_seen, D, U}) of
            [{_, _, #{at := At}}] -> #{<<"presence">> => <<"offline">>, <<"presenceVersion">> => 1, <<"lastSeenAt">> => At};
            [] -> #{<<"presence">> => <<"unknown">>, <<"presenceVersion">> => 0}
        end
    end.

accept(D, A, B) ->
    mnesia:write({?T, {family, D, A, B}, {D, A}, B}),
    mnesia:write({?T, {family, D, B, A}, {D, B}, A}).
revoke(D, A, B) ->
    mnesia:delete({?T, {family, D, A, B}}),
    mnesia:delete({?T, {family, D, B, A}}).
purge(D, U) ->
    [mnesia:delete({?T, K}) || {?T, K, _, _} <- mnesia:index_read(?T, {D, U}, user)],
    mnesia:delete({deenly_family, {last_seen, D, U}}).

%% Called inside the same domain transaction as family eligibility changes.
%% Identity is always taken from the authenticated full JID, never a client-supplied peer.
update(D, U, F, Active, Sequence) when is_boolean(Active), is_integer(Sequence), Sequence >= 0 ->
    K = {session, D, F},
    Current = mnesia:read(?T, K, write),
    %% An in-flight heartbeat must not put a backgrounded device back online.
    case Current of
        [{?T, _, _, #{sequence := Old}}] when Old > Sequence -> [];
        _ ->
            Now = now_ms(),
            Exit = case {Active, Current} of
                {false, [{?T, _, _, #{active := true}}]} -> Now;
                _ -> undefined
            end,
            mnesia:write({?T, K, {D, U}, #{active => Active, sequence => Sequence,
                                           until => Now + ?LEASE_MS, observed_at => Now}}),
            aggregate(D, U, Exit)
    end.
remove(D, U, F) ->
    case mnesia:read(?T, {session, D, F}) of
        [] -> [];
        [{?T, _, _, Value}] ->
            Exit = case maps:get(active, Value) of true -> now_ms(); false -> undefined end,
            mnesia:delete({?T, {session, D, F}}), aggregate(D, U, Exit)
    end.
aggregate(D, U) -> aggregate(D, U, undefined).
aggregate(D, U, Exit) ->
    Rows = mnesia:index_read(?T, {D, U}, user),
    Now = now_ms(),
    Online = lists:any(fun
        ({?T, {session, _, _}, _, #{active := Active, until := Until}}) -> Active andalso Until > Now;
        (_) -> false
    end, Rows),
    Status = case Online of true -> <<"online">>; false -> <<"offline">> end,
    Before = account(D, U),
    Changed = maps:get(<<"presence">>, Before) =/= Status,
    ObservedExit = case Exit of E when is_integer(E) -> E; _ -> 0 end,
    Seen = case Online of
        true -> lists:max([maps:get(last_active_at, Before, 0), ObservedExit | [maps:get(observed_at, V, maps:get(until, V) - ?LEASE_MS)
            || {?T, {session, _, _}, _, #{active := true, until := Until} = V} <- Rows, Until > Now]]);
        false -> case Exit of T when is_integer(T) -> T;
            _ -> maps:get(last_active_at, Before, maps:get(<<"lastSeenAt">>, Before, 0)) end
    end,
    Value0 = #{<<"presence">> => Status, <<"presenceVersion">> => maps:get(<<"presenceVersion">>, Before) + case Changed of true -> 1; false -> 0 end},
    Value = case Online of
        true -> Value0#{last_active_at => Seen};
        false when Seen > 0 -> Value0#{<<"lastSeenAt">> => Seen};
        false -> Value0
    end,
    case Online orelse Changed of
        true ->
            mnesia:write({?T, {account, D, U}, {D, U}, Value}),
            persist_seen(D, U, Seen, Status, Changed);
        false -> ok
    end,
    case Changed of
        false -> [];
        true ->
            Event = (maps:remove(last_active_at, Value))#{<<"event">> => <<"presence">>, <<"jid">> => U,
                           <<"presenceEpoch">> => epoch()},
            [{Peer, Event} || {?T, {family, _, _, _}, _, Peer} <- Rows]
    end.

persist_seen(_, _, At, _, _) when At =< 0 -> ok;
persist_seen(D, U, At, Status, Changed) ->
    Key = {last_seen, D, U},
    Previous = case mnesia:read(deenly_family, Key, write) of [{_, _, #{at := Old}}] -> Old; [] -> 0 end,
    %% Active heartbeats update disk at most once per 20 seconds; exact offline
    %% transitions persist immediately. No timestamp comes from the client.
    case Changed orelse At - Previous >= 20000 of
        true -> mnesia:write({deenly_family, Key, #{at => At, status => Status}});
        false -> ok
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
