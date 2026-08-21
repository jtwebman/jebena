# 01 — Class File Parsing & Verification

Decision doc for Jebena's front door. Target: Java SE 25 (major version 69),
Zig 0.16. Two stages: (A) parse the binary class file, (B) verify it.

## 1. What the spec fixes

JVMS ch. 4 specifies the class file format EXACTLY — magic 0xCAFEBABE, versions,
the constant pool (17 tag kinds), access flags, fields, methods, attributes. There
is no design freedom in the format; only in representation and speed.

Binding constraints:
- major_version: Java SE N -> 44+N. Java 25 = 69. Accept 45..69.
  For major >= 56, minor must be 0 or 65535 (preview).
- Constant pool is 1-indexed; count = entries+1. Long and Double each take TWO
  entries (the second is unusable). This is the classic parser gotcha.
- Modified UTF-8 (JVMS 4.4.7): NUL encoded as 0xC0 0x80; supplementary chars as
  surrogate pairs (6 bytes). NOT standard UTF-8. Decode carefully.
- Structural constraints (JVMS 4.8) and format-checking (4.9) must reject malformed
  input with the specified errors, never crash.

## 2. Verification (JVMS 4.10) — modern approach

Two verifier designs exist:
- Type-checking by StackMapTable (4.10.1): since Java 6 (major 50), class files
  carry StackMapTable attributes precomputed by javac. Verification is then a
  LINEAR type-check against the supplied frames. This is the modern, fast, blessed
  approach.
- Type-inference (4.10.2): the old iterative dataflow fixpoint, needed only for
  class files < major 50 or lacking stackmaps.

Decision: implement the TYPE-CHECKING verifier (StackMapTable-driven). Since we
target Java 25, stackmaps are mandatory for the classes we care about. Treat the
legacy inference verifier as out of scope / best-effort-later. This is a case where
"modern" is also dramatically simpler: linear check vs iterative fixpoint.

The four traditional passes, mapped:
1. Pass 1 — format/structural (during/after parse): magic, versions, well-formed
   constant pool, truncation. This is the security boundary (below).
2. Pass 2 — semantic constraints on the constant pool and members (indices point at
   the right constant kinds; final classes not subclassed; etc.).
3. Pass 3 — bytecode verification per method via StackMapTable type-checking.
4. Pass 4 — lazy runtime checks on first reference (resolution); belongs with the
   interpreter/linker (02), not here.

## 3. Security: the parser is the attack surface

A class file may come from an untrusted source. The parser must be bulletproof:
every read bounds-checked, every malformed input rejected with an error, never a
crash, never UB, never an OOB read. Treat it exactly like a network protocol
parser. This is fuzz target #1 (see ../TESTING.md, bytecode fuzzing) and the reason
Zig (with safety-checked builds + explicit bounds) is a good fit.

## 4. Interfaces

- To interpreter (02): verified methods let the interpreter trust operand types and
  skip per-op checks. The constant pool + resolved members drive quickening.
- To GC (03): StackMapTable frames give, per bytecode offset, which stack/local
  slots hold references -> the source of PRECISE oop maps.
- Descriptor parsing (JVMS 4.3): field and method descriptors decode here; shared by
  linking, reflection, and the interpreter.

## 5. Design

- `Reader`: big-endian, fully bounds-checked byte cursor. The safety primitive.
- `ClassFile`: owns all parsed data via an internal arena (single free on deinit);
  does not borrow the input buffer (utf8 + attribute bytes are copied in).
- `ConstantPool`: 1-indexed; typed accessors that validate index + kind (errors,
  never panics) — used by both parse-time checks and the verifier.
- Pipeline: parse (structural) -> verify (semantic + type-check) -> link (lazy, 02).

## 6. Phased plan

- Phase 0 (tonight/now): full structural PARSER — reader, constant pool (all tags),
  class/field/method/attribute structure, access flags. 100% test coverage incl.
  every malformed-input path. Real .class smoke test via @embedFile.
- Phase 1: modified-UTF-8 decode; descriptor parser; specific attributes (Code,
  StackMapTable, Exceptions, LineNumberTable, BootstrapMethods).
- Phase 2: semantic constant-pool + member validation (Pass 2).
- Phase 3: StackMapTable type-checking verifier (Pass 3).

## 7. Test hooks (see ../TESTING.md)

- Hand-crafted malformed inputs for EVERY error path (truncation at each field,
  bad magic, bad version, bad cp tag, cp index 0/overflow, long/double at end).
- Real class files via @embedFile (javac output) — round-trip structural facts.
- Differential: does Jebena accept/reject the same class files as a reference JVM?
- Fuzzing: mutated .class bytes must only ever return an error, never crash.

## References
- JVMS 25 ch. 4 (format), 4.4 (constant pool), 4.7 (attributes), 4.10 (verification),
  4.3 (descriptors), 4.4.7 (modified UTF-8) — see ../specs
