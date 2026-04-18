# Package `ai.koryki.antlr`

ANTLR4 infrastructure library for the koryki.ai platform. Provides foundational building blocks for implementing ANTLR4-based parsers: error handling, source 
position tracking, and a generic parser session base class. 
This library contains no grammar files.

## Parser base

`AbstractReader<L, P, C>` is the generic base class for a parser session. Subclasses hold a concrete ANTLR `Lexer`, `Parser`, `BufferedTokenStream`, parse tree root, and panic interval list. 


## Error handling

`MsgErrorListener` is an ANTLR `BaseErrorListener` with two modes, selected at construction time:

| Mode        | Behavior                                                                                              |
|-------------|-------------------------------------------------------------------------------------------------------|
| **abort**:  | Throws `GrammarException` immediately on the first parser error, including the rule invocation stack. |
| **panic**:  | Records parser error in a list of `Interval` objects. Retrieve with `getPanic()`.                     |

Lexer errors always throw `GrammarException` regardless of mode.

## Source position and range

| Class | Purpose |
|---|---|
| `Position` | A `(line, col)` point in source. Comparable. Factory methods `Position.start(Token)`, `Position.stop(Token)`, `Position.start(ParserRuleContext)`. |
| `Range` | A source span from one `Position` to another. Comparable. Supports `overlaps(Range)`. Factory methods `Range.range(ParseTree)`, `Range.range(Token)`. |
| `Interval` | A token-index range `[start, stop]` with an associated error message, used to record panic-mode parse errors. |
| `PositionedTreeWalker` | Extends ANTLR's `ParseTreeWalker`. Automatically attaches the source position of the current rule node to any `RuntimeException` thrown during a walk, wrapping it in `PositionException`. |

## Exception hierarchy

```
KorykiaiException             (unchecked)
├── PositionException       — carries (line, pos)
│   └── GrammarException    — thrown on lexer/parser syntax errors
├── RangeException          — carries a full source Range
└── PanicException          — carries a list of Interval (panic-mode errors)
```

