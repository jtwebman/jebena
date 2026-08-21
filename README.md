# Jebena

A clean-room, spec-driven reimplementation of the Java platform, written in Zig.

The name is the clay pot at the center of the Ethiopian coffee ceremony — from
where coffee comes from. It is the vessel that turns raw beans into coffee, which
is roughly what this project does: turn the specification into a working runtime.

## What this is

Jebena implements the Java Language Specification (JLS) and Java Virtual Machine
Specification (JVMS) from scratch. It does not copy, translate, or refactor
OpenJDK source. The specs are the input; the code is independent work.

The code is written with AI assistance and reviewed by humans. That is a
deliberate stance, not a shortcut. See "Provenance" and "Review" below.

## Why

Some projects ban AI-authored contributions. The usual reasons are real:
unclear provenance, license risk, and reviewer burden. Jebena is an argument that
those problems are solvable rather than fatal — build a legitimate Java runtime
where AI does the writing and humans do the standing-behind, with the provenance
and testing discipline to prove it.

## Language

Zig. A JVM is mostly safety-critical plumbing (parser, verifier, class loading,
interpreter, class libraries) plus a small, hard core (JIT, garbage collector).
Zig fits the hard core better than Rust — no borrow checker to fight when the
managed heap moves objects and mutates concurrently — at the cost of giving up
Rust's guarantees on the easy 80%. We buy that back with sanitizers, safety-check
builds, and dense runtime assertions.

Tradeoff to watch: Zig is pre-1.0. Language churn is a real project risk over a
multi-year effort.

## Provenance

The infringement question is about output, not exposure. Independent creation is
the defense; substantial similarity to OpenJDK's expression is the risk. So:

- Implement from the JLS/JVMS, not from OpenJDK source.
- The concrete risk is accidental reproduction of memorized code. Guard for it
  with a one-way, code-level similarity gate: flag -> discard -> re-derive from
  the spec in a fresh context. Never edit copied code to "look different" — that
  manufactures a derivative work instead of removing one.
- OpenJDK and other JVMs may be used as behavioral test oracles. Observing
  behavior is fine; behavior is not copyrightable. Copying their test source is
  not — write our own.

This is not legal advice. A real IP lawyer should sign off on the wall and the
gate before anything ships.

## Review: two lanes

Human review does two different jobs at two different altitudes.

1. Correctness. Own the spec and the tests. Line-by-line understanding of every
   implementation file is not required for the libraries — the test oracle does
   that work. The exception is the JIT, GC, and memory model, where tests miss
   the worst bugs and humans need deep understanding plus fuzzing and
   differential testing.
2. Provenance. A code-level similarity check. Test review does not cover this —
   two implementations can pass identical tests while one is a copy. Behavior
   equivalence is not expression independence.

## Status

Pre-everything. Design and charter stage. First real milestone: parse, verify,
and interpret real bytecode.

See docs/TESTING.md for the testing philosophy.

## Building

Requires **Zig 0.16.0** (pinned; see build.zig.zon `minimum_zig_version`).

```
zig build test          # run the full test suite (unit + fuzz)
zig build               # build the CLI to zig-out/bin/jebena
zig build run           # parse the embedded demo class
```

### Layout
- `src/reader.zig`         — bounds-checked big-endian byte reader (the safety boundary)
- `src/constant_pool.zig`  — constant pool (all 17 tag kinds) + typed accessors
- `src/access_flags.zig`   — class/field/method access flags
- `src/attribute.zig`      — generic attribute envelope
- `src/class_file.zig`     — top-level class file parser (arena-owned)
- `src/fuzz.zig`           — deterministic parser fuzzing (never-crash invariant)
- `docs/research/`         — per-subsystem design docs (01 parser/verifier, 02 interpreter, 03 GC)
