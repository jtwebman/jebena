# Specifications

Jebena targets **Java SE 25** (LTS, Sept 2025).

These documents are the *only* input to the implementation. We implement from
them, not from OpenJDK source.

The spec files themselves are Oracle-copyrighted and are NOT committed to this
repo. Run ./fetch.sh to pull local copies for reference. They are gitignored.

## The three pillars

1. JLS  — Java Language Specification (the language: syntax, types, semantics).
          Chapter 17 is the Java Memory Model.
   https://docs.oracle.com/javase/specs/jls/se25/html/index.html

2. JVMS — Java Virtual Machine Specification (class file format, bytecode,
          verification, linking, execution).
   https://docs.oracle.com/javase/specs/jvms/se25/html/index.html

3. API  — the java.base (and friends) Javadoc: the observable contracts of the
          class libraries, including java.lang.ref reachability semantics.
   https://docs.oracle.com/en/java/javase/25/docs/api/index.html

## Note on the TCK/JCK

The official conformance kit is license-gated by Oracle and cannot be used
freely (this is what stopped Apache Harmony). Differential testing against
reference JVMs is our substitute. See ../TESTING.md.
