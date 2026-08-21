# Backlog

Working list. Check off as done; commit each. Ordered roughly by dependency.

## Subsystem 02 — interpreter (in progress)
- [x] integer/control-flow subset
- [x] invokestatic + recursion (shared step/depth budget)
- [x] ldc/ldc_w (pooled int/float constants)
- [x] long/double/float values (category-2 two-slot model): consts, loads/stores, arithmetic, conversions, lcmp/fcmp/dcmp, l/f/d returns, ldc2_w; exec returns ?Value; invokestatic passes args by descriptor kind.
      arithmetic, conversions (i2l/l2i/i2f/f2i/...), lcmp/fcmp/dcmp, l/f/d returns,
      ldc2_w; exec returns ?Value; invokestatic passes args by descriptor kind.
- [ ] more stack ops: pop2, dup_x1, dup_x2, dup2, dup2_x1, dup2_x2
- [ ] tableswitch/lookupswitch execution
- [ ] wide-prefixed loads/stores/iinc execution

## Object model / heap (couples to GC, subsystem 03)
- [ ] object header + value model for references
- [ ] Phase-0 collector (semispace or mark-sweep) behind a GC interface
- [ ] new / newarray / anewarray, arraylength, array load/store
- [ ] getfield/putfield, getstatic/putstatic
- [ ] invokevirtual / invokespecial / invokeinterface (needs class hierarchy)
- [ ] string constants (ldc of String), <init> chains

## Subsystem 01 — verification
- [ ] Pass 3: StackMapTable type-checking verifier
- [ ] structural bytecode checks wired into verify (branch targets, etc.)

## Class loading / linking
- [ ] a ClassLoader-ish registry; resolve other classes (not just same-class)
- [ ] lazy resolution + quickening (per 02 design)

## Infra
- [ ] wire std.Io file API so `jebena parse <file>` works
- [ ] kcov coverage in CI (currently coverage is by construction)
- [ ] a run harness that finds and runs a real `public static void main`
