-module(deenly_signup_tests).
-include_lib("eunit/include/eunit.hrl").
-include("jlib.hrl").
-include("mongoose_config_spec.hrl").
signup_test_() -> {setup, fun() ->
    ets:new(deenly_family_signup_rate, [named_table, public, set]),
    ets:new(signup_test_accounts, [named_table, public, set])
end, fun(_) -> ets:delete(deenly_family_signup_rate), ets:delete(signup_test_accounts) end,
[?_test(validation()), ?_test(create_and_duplicate()), ?_test(tls_required()),
 ?_test(rate_is_atomic()), ?_test(global_rate()), ?_test(query_and_response_privacy()),
 ?_test(legacy_configuration_defaults_to_enabled()), ?_test(explicit_disable_is_preserved()),
 ?_test(custom_module_toml_schema_is_empty())]}.
legacy_configuration_defaults_to_enabled() ->
    Previous = os:getenv("FAMILY_SIGNUP_ENABLED"), os:unsetenv("FAMILY_SIGNUP_ENABLED"),
    mongoose_config:set_opts(#{{modules, <<"xmpp.test">>} => #{mod_deenly_family => #{}}}),
    try
        {ok, Features} = mod_deenly_family_signup:features([], #{}, #{host_type => <<"xmpp.test">>}),
        ?assertMatch([#xmlel{name = <<"register">>}], Features)
    after mongoose_config:erase_opts(), restore_env(Previous) end.
explicit_disable_is_preserved() ->
    Previous = os:getenv("FAMILY_SIGNUP_ENABLED"), os:putenv("FAMILY_SIGNUP_ENABLED", "false"),
    mongoose_config:set_opts(#{{modules, <<"xmpp.test">>} => #{mod_deenly_family => #{}}}),
    try
        ?assertEqual({ok, []}, mod_deenly_family_signup:features([], #{}, #{host_type => <<"xmpp.test">>}))
    after mongoose_config:erase_opts(), restore_env(Previous) end.
restore_env(false) -> os:unsetenv("FAMILY_SIGNUP_ENABLED");
restore_env(Value) -> os:putenv("FAMILY_SIGNUP_ENABLED", Value).
custom_module_toml_schema_is_empty() ->
    ?assertNot(lists:member(mod_deenly_family, mongoose_config_spec:configurable_modules())),
    ?assertEqual([], mongoose_config_parser_toml:parse_section([], #{}, #section{})),
    ?assertError(#{what := unexpected_key}, mongoose_config_parser_toml:parse_section([], #{<<"signup_enabled">> => true}, #section{})),
    ?assertEqual(#section{}, mod_deenly_family:config_spec()).
reset() -> ets:delete_all_objects(deenly_family_signup_rate), ets:delete_all_objects(signup_test_accounts).
password() -> <<"Signup password 123!">>.
validation() ->
    ?assertEqual({ok, <<"alice-1">>}, mod_deenly_family_signup:validate(<<"Alice-1">>, password())),
    ?assertEqual({error, <<"forbidden">>}, mod_deenly_family_signup:validate(<<"admin">>, password())),
    ?assertEqual({error, <<"bad-request">>}, mod_deenly_family_signup:validate(<<"someone@foreign.test">>, password())),
    ?assertEqual({error, <<"bad-request">>}, mod_deenly_family_signup:validate(<<255,255,255>>, password())),
    ?assertEqual({error, <<"not-acceptable">>}, mod_deenly_family_signup:validate(<<"alice">>, <<"short">>)),
    ?assertEqual({error, <<"not-acceptable">>}, mod_deenly_family_signup:validate(<<"alice">>, <<"long-password",10>>)),
    ?assertEqual({error, <<"not-acceptable">>}, mod_deenly_family_signup:validate(<<"alice">>, binary:copy(<<" ">>,12))),
    ?assertEqual({error, <<"not-acceptable">>}, mod_deenly_family_signup:validate(<<"alice">>, binary:copy(<<"a">>,129))).
create_and_duplicate() -> reset(),
    ?assertEqual(ok, mod_deenly_family_signup:register(<<"xmpp.test">>, <<"alice">>, password(), ip1, true)),
    ?assertEqual({error, <<"conflict">>}, mod_deenly_family_signup:register(<<"xmpp.test">>, <<"Alice">>, <<"different password">>, ip1, true)),
    ?assertEqual([{<<"alice">>, password()}], ets:lookup(signup_test_accounts, <<"alice">>)),
    ?assertEqual({error, <<"service-unavailable">>}, mod_deenly_family_signup:register(<<"xmpp.test">>, <<"backend-down">>, password(), ip1, true)).
tls_required() -> reset(),
    ?assertEqual({error, <<"not-allowed">>}, mod_deenly_family_signup:register(<<"xmpp.test">>, <<"alice">>, password(), ip1, false)),
    ?assertEqual([], ets:tab2list(signup_test_accounts)).
rate_is_atomic() -> reset(), Parent=self(),
    [spawn(fun() -> Parent ! {allowed, mod_deenly_family_signup:allow(<<"xmpp.test">>, ip1, 10)} end) || _ <- lists:seq(1,20)],
    Results = [receive {allowed, R} -> R after 3000 -> error(timeout) end || _ <- lists:seq(1,20)],
    ?assertEqual(5, length([ok || true <- Results])),
    ?assertEqual(true, mod_deenly_family_signup:allow(<<"xmpp.test">>, ip1, 11)),
    ?assert(ets:info(deenly_family_signup_rate, size) =< 2).
global_rate() -> reset(),
    [mod_deenly_family_signup:allow(<<"xmpp.test">>, N, 10) || N <- lists:seq(1,100)],
    ?assertEqual(false, mod_deenly_family_signup:allow(<<"xmpp.test">>, another_ip, 10)).
query_and_response_privacy() -> reset(),
    Get = mod_deenly_family_signup:handle(#iq{type=get}, <<"xmpp.test">>, ip1),
    ?assertEqual(result, Get#iq.type),
    Query = #xmlel{name= <<"query">>, children=[
        #xmlel{name= <<"username">>, children=[#xmlcdata{content= <<"alice">>}]},
        #xmlel{name= <<"password">>, children=[#xmlcdata{content=password()}]}]},
    First = mod_deenly_family_signup:handle(#iq{type=set,sub_el=Query}, <<"xmpp.test">>, ip1),
    ?assertEqual(result, First#iq.type), ?assertEqual([], First#iq.sub_el),
    Duplicate = mod_deenly_family_signup:handle(#iq{type=set,sub_el=Query}, <<"xmpp.test">>, ip1),
    ?assertEqual(error, Duplicate#iq.type),
    ?assertEqual(nomatch, binary:match(term_to_binary(Duplicate), password())),
    Remove = mod_deenly_family_signup:handle(#iq{type=set,sub_el=#xmlel{name= <<"query">>,children=[#xmlel{name= <<"remove">>}]}}, <<"xmpp.test">>, ip1),
    ?assertEqual(error, Remove#iq.type),
    ?assertEqual([{<<"alice">>, password()}], ets:lookup(signup_test_accounts, <<"alice">>)).
