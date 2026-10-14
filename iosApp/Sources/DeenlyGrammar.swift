
/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

import Foundation

/// GBNF grammars for the Deenly tasks, generated from Android's exact JSON
/// schemas with llama.cpp v0.6.0's json_schema_to_grammar converter
/// (tools/build_llama_xcframework.sh builds the converter from the same
/// pinned checkout).
enum DeenlyGrammar {

    static func forTask(_ task: String) -> String {
        switch task {
        case "answer": return answer
        case "question": return question
        default: return knowledge
        }
    }

    // The schema-generated free-form string allowed greedy sampling to collapse
    // into punctuation. Requiring two to six short words keeps the same title
    // contract while making that degenerate path grammatically impossible.
    static let knowledge = "contentType ::= \"\\\"knowledge\\\"\"\ncontentType-kv ::= \"\\\"contentType\\\"\" space \":\" space contentType\nroot ::= \"{\" space contentType-kv \",\" space title-kv space \"}\"\nspace ::= | \" \" | \"\\n\"{1,2} [ \\t]{0,20}\ntitle ::= \"\\\"\" word (\" \" word){1,5} \"\\\"\"\ntitle-char ::= [A-Za-z0-9'-]\ntitle-kv ::= \"\\\"title\\\"\" space \":\" space title\nword ::= [A-Za-z0-9] title-char{1,11}\n"

    static let question = "answer ::= \"\\\"\" char{1,96} \"\\\"\"\nanswer-kv ::= \"\\\"answer\\\"\" space \":\" space answer\nchar ::= [^\"\\\\\\x7F\\x00-\\x1F] | [\\\\] ([\"\\\\bfnrt] | \"u\" [0-9a-fA-F]{4})\ncontentType ::= \"\\\"question\\\"\"\ncontentType-kv ::= \"\\\"contentType\\\"\" space \":\" space contentType\nevidence ::= \"\\\"\" char{1,420} \"\\\"\"\nevidence-kv ::= \"\\\"evidence\\\"\" space \":\" space evidence\noptions ::= \"[\" space options-item (\",\" space options-item){3,3} space \"]\"\noptions-item ::= \"\\\"\" char{1,96} \"\\\"\"\noptions-kv ::= \"\\\"options\\\"\" space \":\" space options\nquestion ::= \"\\\"\" char{8,180} \"\\\"\"\nquestion-kv ::= \"\\\"question\\\"\" space \":\" space question\nquestionKind ::= (\"\\\"person\\\"\" | \"\\\"place\\\"\" | \"\\\"food\\\"\" | \"\\\"color\\\"\" | \"\\\"action\\\"\" | \"\\\"number\\\"\" | \"\\\"description\\\"\" | \"\\\"teaching\\\"\" | \"\\\"outcome\\\"\" | \"\\\"object\\\"\" | \"\\\"time\\\"\")\nquestionKind-kv ::= \"\\\"questionKind\\\"\" space \":\" space questionKind\nroot ::= \"{\" space contentType-kv \",\" space questionKind-kv \",\" space question-kv \",\" space answer-kv \",\" space evidence-kv \",\" space options-kv space \"}\"\nspace ::= | \" \" | \"\\n\"{1,2} [ \\t]{0,20}\n"

    static let answer = "answer ::= \"\\\"\" char{0,96} \"\\\"\"\nanswer-kv ::= \"\\\"answer\\\"\" space \":\" space answer\nchar ::= [^\"\\\\\\x7F\\x00-\\x1F] | [\\\\] ([\"\\\\bfnrt] | \"u\" [0-9a-fA-F]{4})\ncontentType ::= (\"\\\"answer\\\"\" | \"\\\"unanswered\\\"\")\ncontentType-kv ::= \"\\\"contentType\\\"\" space \":\" space contentType\nevidence ::= \"\\\"\" char{0,420} \"\\\"\"\nevidence-kv ::= \"\\\"evidence\\\"\" space \":\" space evidence\nroot ::= \"{\" space contentType-kv \",\" space answer-kv \",\" space evidence-kv space \"}\"\nspace ::= | \" \" | \"\\n\"{1,2} [ \\t]{0,20}\n"
}
