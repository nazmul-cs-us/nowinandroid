%% Capture the actual generated stanza without starting a MongooseIM cluster.
-module(mongoose_router).
-export([route/1]).
route(Acc) -> self() ! {family_test_route, Acc}, Acc.
