package java.util.regex;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Clean-room, minimal {@code java.util.regex.Pattern}.
 *
 * <p>This is a deterministic backtracking regular-expression engine that mirrors
 * the behavior of the real {@code java.util.regex} on a supported subset:
 * literals and {@code .}; character classes with ranges and negation; the
 * predefined classes {@code \d \D \w \W \s \S}; the anchors {@code ^} and
 * {@code $} (default, non-multiline mode); the greedy quantifiers {@code * + ?}
 * and the bounded forms {@code {n} {n,} {n,m}}; alternation {@code |}; capturing
 * groups {@code (...)}; and non-capturing groups {@code (?:...)}. Escapes of the
 * form {@code \\x} produce the literal metacharacter {@code x}.
 *
 * <p>The regular expression is parsed into a small abstract syntax tree and then
 * compiled, using continuation passing, into a chain of {@link Node} objects.
 * Each node knows how to match at a position and then hand control to its
 * successor; greedy quantifiers and alternation drive the backtracking by trying
 * their alternatives in order and returning the first that lets the rest of the
 * pattern succeed.
 */
public final class Pattern {

    /** Sentinel used for "unbounded" quantifier maxima (avoids Integer.MAX_VALUE). */
    static final int MAX = 2147483647;

    /** Enables case-insensitive matching (ASCII folding). */
    public static final int CASE_INSENSITIVE = 0x02;

    /** Enables multiline mode, so {@code ^} and {@code $} match at line bounds. */
    public static final int MULTILINE = 0x08;

    /** Enables dotall mode, so {@code .} matches any character including line terminators. */
    public static final int DOTALL = 0x20;

    private final String patternString;
    private final int flags;
    Node root;
    int groupCount;

    /** Maps a named-group name (from {@code (?<name>...)}) to its group number. */
    final Map namedGroups;

    private Pattern(String patternString, int flags) {
        this.patternString = patternString;
        this.flags = flags;
        this.groupCount = 0;
        this.namedGroups = new HashMap();
    }

    /** Case-insensitive ASCII character comparison. */
    static boolean ciEquals(char a, char b) {
        if (a == b) {
            return true;
        }
        char la = a;
        char lb = b;
        if (la >= 'A' && la <= 'Z') { la += 32; }
        if (lb >= 'A' && lb <= 'Z') { lb += 32; }
        return la == lb;
    }

    /**
     * Returns the group number recorded for the given {@code (?<name>...)} name,
     * throwing {@link IllegalArgumentException} if no such name exists.
     */
    int groupNumberForName(String name) {
        Object v = namedGroups.get(name);
        if (v == null) {
            throw new IllegalArgumentException("No group with name <" + name + ">");
        }
        return ((Integer) v).intValue();
    }

    /** Compiles the given regular expression into a pattern. */
    public static Pattern compile(String regex) {
        return compile(regex, 0);
    }

    /** Compiles the given regular expression with the given match flags. */
    public static Pattern compile(String regex, int flags) {
        if (regex == null) {
            throw new NullPointerException("regex");
        }
        Pattern p = new Pattern(regex, flags);
        Parser parser = new Parser(regex, p);
        Ast ast = parser.parse();
        p.root = ast.compile(new LastNode());
        return p;
    }

    /** Returns this pattern's match flags. */
    public int flags() {
        return flags;
    }

    /** Returns the source regular expression. */
    public String pattern() {
        return patternString;
    }

    public String toString() {
        return patternString;
    }

    /** Creates a matcher that will match the given input against this pattern. */
    public Matcher matcher(CharSequence input) {
        if (input == null) {
            throw new NullPointerException("input");
        }
        return new Matcher(this, input);
    }

    /** The number of capturing groups in this pattern (group 0 excluded). */
    public int groupCount() {
        return groupCount;
    }

    /** Compiles {@code regex} and tests whether {@code input} matches entirely. */
    public static boolean matches(String regex, CharSequence input) {
        return compile(regex).matcher(input).matches();
    }

    /**
     * Splits {@code input} around matches of this pattern, with no limit; this
     * is {@code split(input, 0)}: trailing empty strings are removed.
     */
    public String[] split(CharSequence input) {
        return split(input, 0);
    }

    /**
     * Splits {@code input} around matches of this pattern.
     *
     * <p>The {@code limit} controls the number of pieces and the treatment of
     * trailing empty strings, matching the real engine: a positive limit yields
     * at most {@code limit} pieces (the last one holding the unsplit remainder);
     * a negative limit imposes no bound and keeps trailing empty strings; a zero
     * limit imposes no bound but discards trailing empty strings. A zero-width
     * match at the very start of the input never produces a leading empty piece.
     */
    public String[] split(CharSequence input, int limit) {
        String in = input.toString();
        int index = 0;
        boolean matchLimited = limit > 0;
        List matchList = new ArrayList();
        Matcher m = matcher(input);

        while (m.find()) {
            if (!matchLimited || matchList.size() < limit - 1) {
                if (index == 0 && index == m.start() && m.start() == m.end()) {
                    // No empty leading piece for a zero-width match at the start.
                    continue;
                }
                matchList.add(in.substring(index, m.start()));
                index = m.end();
            } else if (matchList.size() == limit - 1) {
                // Last allowed piece: absorb the rest of the input.
                matchList.add(in.substring(index, in.length()));
                index = m.end();
            }
        }

        // No match at all: the input is returned unchanged as a single piece.
        if (index == 0) {
            return new String[] { in };
        }

        if (!matchLimited || matchList.size() < limit) {
            matchList.add(in.substring(index, in.length()));
        }

        int resultSize = matchList.size();
        if (limit == 0) {
            while (resultSize > 0
                && ((String) matchList.get(resultSize - 1)).equals("")) {
                resultSize--;
            }
        }
        String[] result = new String[resultSize];
        for (int k = 0; k < resultSize; k++) {
            result[k] = (String) matchList.get(k);
        }
        return result;
    }

    /**
     * Returns a literal pattern string for the given string. The result compiles
     * to a pattern that matches {@code s} exactly, with every metacharacter
     * treated as an ordinary character. (Non-alphanumeric characters are escaped
     * with a backslash; this satisfies the public contract without relying on
     * {@code \Q...\E} quoting, which this engine does not implement.)
     */
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean alnum = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9');
            if (!alnum) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Character predicates
    // ------------------------------------------------------------------

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isWord(char c) {
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9')
            || c == '_';
    }

    static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B'
            || c == '\f' || c == '\r';
    }

    static boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u0085'
            || c == '\u2028' || c == '\u2029';
    }

    /** A predicate over a single character; the basis of every class node. */
    abstract static class CharPredicate {
        abstract boolean test(char c);
    }

    static final class Single extends CharPredicate {
        final char ch;
        Single(char ch) { this.ch = ch; }
        boolean test(char c) { return c == ch; }
    }

    static final class Range extends CharPredicate {
        final char lo;
        final char hi;
        Range(char lo, char hi) { this.lo = lo; this.hi = hi; }
        boolean test(char c) { return c >= lo && c <= hi; }
    }

    static final class Digit extends CharPredicate {
        boolean test(char c) { return isDigit(c); }
    }

    static final class Word extends CharPredicate {
        boolean test(char c) { return isWord(c); }
    }

    static final class Space extends CharPredicate {
        boolean test(char c) { return isSpace(c); }
    }

    static final class Negate extends CharPredicate {
        final CharPredicate inner;
        Negate(CharPredicate inner) { this.inner = inner; }
        boolean test(char c) { return !inner.test(c); }
    }

    static final class Union extends CharPredicate {
        final CharPredicate[] parts;
        Union(CharPredicate[] parts) { this.parts = parts; }
        boolean test(char c) {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].test(c)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Compiled node chain
    // ------------------------------------------------------------------

    /** A single step in the compiled pattern; matches at {@code i}, then chains. */
    abstract static class Node {
        abstract boolean match(Matcher m, int i);
    }

    /** Terminal node: accepts (and, for {@code matches()}, requires end of input). */
    static final class LastNode extends Node {
        boolean match(Matcher m, int i) {
            if (m.requireEndAnchor && i != m.to) {
                return false;
            }
            m.groupEnds[0] = i;
            return true;
        }
    }

    /** Matches one specific character. */
    static final class CharNode extends Node {
        final char ch;
        final Node next;
        final boolean ci;
        CharNode(char ch, Node next, boolean ci) { this.ch = ch; this.next = next; this.ci = ci; }
        boolean match(Matcher m, int i) {
            if (i < m.to) {
                char t = m.text.charAt(i);
                if (ci ? ciEquals(t, ch) : t == ch) {
                    return next.match(m, i + 1);
                }
            }
            return false;
        }
    }

    /** The {@code .} metacharacter: any character except a line terminator (unless dotall). */
    static final class DotNode extends Node {
        final Node next;
        final boolean dotall;
        DotNode(Node next, boolean dotall) { this.next = next; this.dotall = dotall; }
        boolean match(Matcher m, int i) {
            if (i < m.to && (dotall || !isLineTerminator(m.text.charAt(i)))) {
                return next.match(m, i + 1);
            }
            return false;
        }
    }

    /** Matches one character accepted by a {@link CharPredicate}. */
    static final class ClassNode extends Node {
        final CharPredicate pred;
        final Node next;
        final boolean ci;
        ClassNode(CharPredicate pred, Node next, boolean ci) {
            this.pred = pred; this.next = next; this.ci = ci;
        }
        boolean match(Matcher m, int i) {
            if (i < m.to) {
                char t = m.text.charAt(i);
                boolean ok = pred.test(t);
                if (!ok && ci) {
                    char lo = (t >= 'A' && t <= 'Z') ? (char) (t + 32) : t;
                    char up = (t >= 'a' && t <= 'z') ? (char) (t - 32) : t;
                    ok = pred.test(lo) || pred.test(up);
                }
                if (ok) {
                    return next.match(m, i + 1);
                }
            }
            return false;
        }
    }

    /** {@code ^}: matches at the start of input, or after a line terminator in multiline mode. */
    static final class BeginNode extends Node {
        final Node next;
        final boolean multiline;
        BeginNode(Node next, boolean multiline) { this.next = next; this.multiline = multiline; }
        boolean match(Matcher m, int i) {
            if (i == m.from) {
                return next.match(m, i);
            }
            if (multiline && i > m.from && i <= m.to) {
                char prev = m.text.charAt(i - 1);
                if (prev == '\n') {
                    return next.match(m, i);
                }
                if (prev == '\r' && !(i < m.to && m.text.charAt(i) == '\n')) {
                    return next.match(m, i);
                }
                if (prev == '\u0085' || prev == '\u2028' || prev == '\u2029') {
                    return next.match(m, i);
                }
            }
            return false;
        }
    }

    /**
     * {@code $} in default mode: matches at the end of input, or just before a
     * line terminator that ends the input. In multiline mode it also matches at
     * the end of each line. Mirrors the real engine's Dollar node.
     */
    static final class EndNode extends Node {
        final Node next;
        final boolean multiline;
        EndNode(Node next, boolean multiline) { this.next = next; this.multiline = multiline; }
        boolean match(Matcher m, int i) {
            if (multiline) {
                if (i == m.to) {
                    return next.match(m, i);
                }
                char c = m.text.charAt(i);
                if (isLineTerminator(c)) {
                    if (c == '\n' && i > m.from && m.text.charAt(i - 1) == '\r') {
                        return false;
                    }
                    return next.match(m, i);
                }
                return false;
            }
            return matchDefault(m, i);
        }
        boolean matchDefault(Matcher m, int i) {
            int endIndex = m.to;
            if (i < endIndex - 2) {
                return false;
            }
            if (i == endIndex - 2) {
                if (m.text.charAt(i) != '\r') {
                    return false;
                }
                if (m.text.charAt(i + 1) != '\n') {
                    return false;
                }
            }
            if (i < endIndex) {
                char ch = m.text.charAt(i);
                if (ch == '\n') {
                    if (i > 0 && m.text.charAt(i - 1) == '\r') {
                        return false;
                    }
                } else if (ch == '\r') {
                    // matches just before a trailing carriage return
                } else {
                    return false;
                }
            }
            return next.match(m, i);
        }
    }

    /** Records the start of a capturing group, restoring it on backtrack. */
    static final class GroupStart extends Node {
        final int g;
        final Node next;
        GroupStart(int g, Node next) { this.g = g; this.next = next; }
        boolean match(Matcher m, int i) {
            int saved = m.groupStarts[g];
            m.groupStarts[g] = i;
            if (next.match(m, i)) {
                return true;
            }
            m.groupStarts[g] = saved;
            return false;
        }
    }

    /** Records the end of a capturing group, restoring it on backtrack. */
    static final class GroupEnd extends Node {
        final int g;
        final Node next;
        GroupEnd(int g, Node next) { this.g = g; this.next = next; }
        boolean match(Matcher m, int i) {
            int saved = m.groupEnds[g];
            m.groupEnds[g] = i;
            if (next.match(m, i)) {
                return true;
            }
            m.groupEnds[g] = saved;
            return false;
        }
    }

    /** Alternation: tries each branch in order, first success wins. */
    static final class Branch extends Node {
        final Node[] alts;
        Branch(Node[] alts) { this.alts = alts; }
        boolean match(Matcher m, int i) {
            for (int k = 0; k < alts.length; k++) {
                if (alts[k].match(m, i)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A greedy bounded loop implementing {@code * + ? {n} {n,} {n,m}}. Iteration
     * count and iteration-start position are threaded through the depth-first
     * search as instance fields; a {@link Prolog} initializes them and restores
     * them afterward so nested contexts are unaffected.
     */
    static final class Loop extends Node {
        Node body;   // the quantified atom, whose continuation loops back here
        Node next;   // what follows the whole quantifier
        int cmin;
        int cmax;
        int count;
        int posBegin;

        /** Entry point from {@link Prolog}: run the loop from a clean state. */
        boolean matchInit(Matcher m, int i) {
            int saveCount = count;
            int savePos = posBegin;
            boolean ret;
            if (cmin > 0) {
                count = 1;
                ret = enterBody(m, i);
            } else if (cmax > 0) {
                count = 1;
                ret = enterBody(m, i);
                if (!ret) {
                    ret = next.match(m, i);
                }
            } else {
                ret = next.match(m, i);
            }
            count = saveCount;
            posBegin = savePos;
            return ret;
        }

        /** Run one iteration of the body, tracking its start position. */
        boolean enterBody(Matcher m, int i) {
            int savePos = posBegin;
            posBegin = i;
            boolean b = body.match(m, i);
            if (!b) {
                posBegin = savePos;
            }
            return b;
        }

        /** Re-entry point from the body's continuation after each iteration. */
        boolean match(Matcher m, int i) {
            // Guard against looping forever on a zero-length body.
            if (i > posBegin) {
                int c = count;
                if (c < cmin) {
                    count = c + 1;
                    boolean b = enterBody(m, i);
                    if (!b) {
                        count = c;
                    }
                    return b;
                }
                if (c < cmax) {
                    count = c + 1;
                    boolean b = enterBody(m, i);
                    if (!b) {
                        count = c;
                    } else {
                        return true;
                    }
                }
            }
            return next.match(m, i);
        }
    }

    /** Initializes a {@link Loop} on first entry. */
    static final class Prolog extends Node {
        final Loop loop;
        Prolog(Loop loop) { this.loop = loop; }
        boolean match(Matcher m, int i) {
            return loop.matchInit(m, i);
        }
    }

    /** The continuation placed after a loop body; returns control to the loop. */
    static final class LoopBack extends Node {
        final Loop loop;
        LoopBack(Loop loop) { this.loop = loop; }
        boolean match(Matcher m, int i) {
            return loop.match(m, i);
        }
    }

    // ------------------------------------------------------------------
    // Abstract syntax tree
    // ------------------------------------------------------------------

    /** A parsed regex fragment that can compile itself given a continuation. */
    abstract static class Ast {
        abstract Node compile(Node next);
    }

    static final class LitAst extends Ast {
        final char ch;
        final boolean ci;
        LitAst(char ch, boolean ci) { this.ch = ch; this.ci = ci; }
        Node compile(Node next) { return new CharNode(ch, next, ci); }
    }

    /** An empty fragment (e.g. a standalone inline-flag group {@code (?i)}). */
    static final class EmptyAst extends Ast {
        Node compile(Node next) { return next; }
    }

    static final class DotAst extends Ast {
        final boolean dotall;
        DotAst(boolean dotall) { this.dotall = dotall; }
        Node compile(Node next) { return new DotNode(next, dotall); }
    }

    static final class ClazzAst extends Ast {
        final CharPredicate pred;
        final boolean ci;
        ClazzAst(CharPredicate pred, boolean ci) { this.pred = pred; this.ci = ci; }
        Node compile(Node next) { return new ClassNode(pred, next, ci); }
    }

    static final class BeginAst extends Ast {
        final boolean multiline;
        BeginAst(boolean multiline) { this.multiline = multiline; }
        Node compile(Node next) { return new BeginNode(next, multiline); }
    }

    static final class EndAst extends Ast {
        final boolean multiline;
        EndAst(boolean multiline) { this.multiline = multiline; }
        Node compile(Node next) { return new EndNode(next, multiline); }
    }

    static final class ConcatAst extends Ast {
        final List children; // of Ast
        ConcatAst(List children) { this.children = children; }
        Node compile(Node next) {
            Node acc = next;
            for (int k = children.size() - 1; k >= 0; k--) {
                acc = ((Ast) children.get(k)).compile(acc);
            }
            return acc;
        }
    }

    static final class AltAst extends Ast {
        final List alts; // of Ast
        AltAst(List alts) { this.alts = alts; }
        Node compile(Node next) {
            Node[] arr = new Node[alts.size()];
            for (int k = 0; k < alts.size(); k++) {
                arr[k] = ((Ast) alts.get(k)).compile(next);
            }
            return new Branch(arr);
        }
    }

    static final class GroupAst extends Ast {
        final int num; // -1 for non-capturing
        final Ast child;
        GroupAst(int num, Ast child) { this.num = num; this.child = child; }
        Node compile(Node next) {
            if (num < 0) {
                return child.compile(next);
            }
            return new GroupStart(num, child.compile(new GroupEnd(num, next)));
        }
    }

    static final class QuantAst extends Ast {
        final Ast child;
        final int min;
        final int max;
        QuantAst(Ast child, int min, int max) {
            this.child = child;
            this.min = min;
            this.max = max;
        }
        Node compile(Node next) {
            Loop loop = new Loop();
            loop.cmin = min;
            loop.cmax = max;
            loop.next = next;
            loop.body = child.compile(new LoopBack(loop));
            return new Prolog(loop);
        }
    }

    // ------------------------------------------------------------------
    // Parser (recursive descent)
    // ------------------------------------------------------------------

    /** One returned character-class element: either a literal char or a predicate. */
    static final class ClassElem {
        final boolean isPredicate;
        final char ch;
        final CharPredicate pred;
        ClassElem(char ch) { this.isPredicate = false; this.ch = ch; this.pred = null; }
        ClassElem(CharPredicate pred) { this.isPredicate = true; this.ch = 0; this.pred = pred; }
    }

    static final class Parser {
        final String re;
        final int len;
        int pos;
        final Pattern pat;
        int flags;

        Parser(String re, Pattern pat) {
            this.re = re;
            this.len = re.length();
            this.pos = 0;
            this.pat = pat;
            this.flags = pat.flags;
        }

        private boolean ci() { return (flags & CASE_INSENSITIVE) != 0; }
        private boolean multiline() { return (flags & MULTILINE) != 0; }
        private boolean dotall() { return (flags & DOTALL) != 0; }

        private char peek() {
            return re.charAt(pos);
        }

        private char peekAt(int k) {
            return re.charAt(k);
        }

        private void error(String msg) {
            throw new IllegalArgumentException(
                "Invalid regular expression near index " + pos + ": " + msg);
        }

        Ast parse() {
            Ast a = parseAlt();
            if (pos < len) {
                error("unexpected character '" + peek() + "'");
            }
            return a;
        }

        private Ast parseAlt() {
            List alts = new ArrayList();
            alts.add(parseConcat());
            while (pos < len && peek() == '|') {
                pos++;
                alts.add(parseConcat());
            }
            if (alts.size() == 1) {
                return (Ast) alts.get(0);
            }
            return new AltAst(alts);
        }

        private Ast parseConcat() {
            List seq = new ArrayList();
            while (pos < len && peek() != '|' && peek() != ')') {
                seq.add(parseQuant());
            }
            if (seq.size() == 1) {
                return (Ast) seq.get(0);
            }
            return new ConcatAst(seq);
        }

        private Ast parseQuant() {
            Ast atom = parseAtom();
            if (pos < len) {
                char c = peek();
                if (c == '*') {
                    pos++;
                    return finishQuant(atom, 0, MAX);
                }
                if (c == '+') {
                    pos++;
                    return finishQuant(atom, 1, MAX);
                }
                if (c == '?') {
                    pos++;
                    return finishQuant(atom, 0, 1);
                }
                if (c == '{') {
                    return parseBrace(atom);
                }
            }
            return atom;
        }

        private Ast finishQuant(Ast atom, int min, int max) {
            // Reluctant (*?) and possessive (*+) quantifiers are outside the
            // supported subset; reject rather than silently disagree.
            if (pos < len) {
                char c = peek();
                if (c == '?' || c == '+' || c == '*') {
                    error("unsupported reluctant/possessive quantifier");
                }
            }
            return new QuantAst(atom, min, max);
        }

        private Ast parseBrace(Ast atom) {
            pos++; // consume '{'
            int min = parseInt();
            int max;
            if (pos < len && peek() == ',') {
                pos++;
                if (pos < len && peek() == '}') {
                    max = MAX;
                } else {
                    max = parseInt();
                }
            } else {
                max = min;
            }
            if (pos >= len || peek() != '}') {
                error("expected '}' in quantifier");
            }
            pos++; // consume '}'
            return finishQuant(atom, min, max);
        }

        private int parseInt() {
            int start = pos;
            int v = 0;
            while (pos < len && isDigit(peek())) {
                v = v * 10 + (peek() - '0');
                pos++;
            }
            if (pos == start) {
                error("expected a number in quantifier");
            }
            return v;
        }

        private Ast parseAtom() {
            if (pos >= len) {
                error("unexpected end of pattern");
            }
            char c = peek();
            if (c == '(') {
                return parseGroup();
            }
            if (c == '[') {
                return parseClass();
            }
            if (c == '.') {
                pos++;
                return new DotAst(dotall());
            }
            if (c == '^') {
                pos++;
                return new BeginAst(multiline());
            }
            if (c == '$') {
                pos++;
                return new EndAst(multiline());
            }
            if (c == '\\') {
                return parseEscapeAtom();
            }
            if (c == '*' || c == '+' || c == '?') {
                error("dangling metacharacter '" + c + "'");
            }
            if (c == '{' || c == '}' || c == ']') {
                // A bare quantifier/brace/bracket without a preceding atom.
                error("unexpected metacharacter '" + c + "'");
            }
            pos++;
            return new LitAst(c, ci());
        }

        private Ast parseGroup() {
            pos++; // consume '('
            int groupNum = -1;
            if (pos < len && peek() == '?') {
                pos++;
                if (pos < len && isInlineFlagStart(peek())) {
                    return parseInlineFlagGroup();
                } else if (pos < len && peek() == ':') {
                    pos++; // non-capturing
                } else if (pos < len && peek() == '<') {
                    pos++; // consume '<'
                    // (?<= ) and (?<! ) lookbehind are outside the supported
                    // subset; only (?<name>...) named capturing groups are.
                    if (pos < len && (peek() == '=' || peek() == '!')) {
                        error("unsupported lookbehind construct");
                    }
                    String name = parseGroupName();
                    groupNum = ++pat.groupCount;
                    if (pat.namedGroups.get(name) != null) {
                        error("duplicate group name '" + name + "'");
                    }
                    pat.namedGroups.put(name, Integer.valueOf(groupNum));
                } else {
                    error("unsupported group construct");
                }
            } else {
                groupNum = ++pat.groupCount;
            }
            Ast body = parseAlt();
            if (pos >= len || peek() != ')') {
                error("unclosed group");
            }
            pos++; // consume ')'
            return new GroupAst(groupNum, body);
        }

        /** Whether {@code c} can begin an inline-flag construct {@code (?imsU-imsU...)}. */
        private boolean isInlineFlagStart(char c) {
            return c == 'i' || c == 'm' || c == 's' || c == '-';
        }

        /** Maps an inline-flag letter to its flag bit, or 0 if unsupported. */
        private int flagBit(char c) {
            if (c == 'i') { return CASE_INSENSITIVE; }
            if (c == 'm') { return MULTILINE; }
            if (c == 's') { return DOTALL; }
            return 0;
        }

        /**
         * Parses an inline-flag construct: either {@code (?flags)} which changes
         * the flags for the remainder of the enclosing expression, or
         * {@code (?flags:...)} which applies the changed flags only to a scoped
         * non-capturing group.
         */
        private Ast parseInlineFlagGroup() {
            int savedFlags = flags;
            boolean clearing = false;
            while (pos < len) {
                char c = peek();
                if (c == ')' || c == ':') {
                    break;
                }
                if (c == '-') {
                    clearing = true;
                    pos++;
                    continue;
                }
                int bit = flagBit(c);
                if (bit == 0) {
                    error("unsupported inline flag '" + c + "'");
                }
                if (clearing) {
                    flags &= ~bit;
                } else {
                    flags |= bit;
                }
                pos++;
            }
            if (pos >= len) {
                error("unclosed inline-flag group");
            }
            if (peek() == ':') {
                pos++; // consume ':'
                Ast body = parseAlt();
                if (pos >= len || peek() != ')') {
                    error("unclosed group");
                }
                pos++; // consume ')'
                flags = savedFlags; // scoped: flags revert outside the group
                return new GroupAst(-1, body);
            }
            pos++; // consume ')'; the flag change persists for the rest of the pattern
            return new EmptyAst();
        }

        /**
         * Parses the name of a {@code (?<name>...)} group: a letter followed by
         * letters or digits, terminated by {@code '>'} (which is consumed).
         */
        private String parseGroupName() {
            if (pos >= len) {
                error("named capturing group has invalid name");
            }
            char c = peek();
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                error("named capturing group has invalid name");
            }
            StringBuilder sb = new StringBuilder();
            sb.append(c);
            pos++;
            while (pos < len) {
                c = peek();
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')) {
                    sb.append(c);
                    pos++;
                } else {
                    break;
                }
            }
            if (pos >= len || peek() != '>') {
                error("named capturing group is missing trailing '>'");
            }
            pos++; // consume '>'
            return sb.toString();
        }

        /** Parses an escape appearing outside a character class. */
        private Ast parseEscapeAtom() {
            pos++; // consume backslash
            if (pos >= len) {
                error("trailing backslash");
            }
            char c = peek();
            pos++;
            CharPredicate p = predefined(c);
            if (p != null) {
                return new ClazzAst(p, ci());
            }
            return new LitAst(escapedLiteral(c), ci());
        }

        /** Returns the predefined class for d/D/w/W/s/S, or null otherwise. */
        private CharPredicate predefined(char c) {
            if (c == 'd') { return new Digit(); }
            if (c == 'D') { return new Negate(new Digit()); }
            if (c == 'w') { return new Word(); }
            if (c == 'W') { return new Negate(new Word()); }
            if (c == 's') { return new Space(); }
            if (c == 'S') { return new Negate(new Space()); }
            return null;
        }

        /** Resolves an escaped literal character (\n, \t, or a metacharacter). */
        private char escapedLiteral(char c) {
            if (c == 'n') { return '\n'; }
            if (c == 't') { return '\t'; }
            if (c == 'r') { return '\r'; }
            if (c == 'f') { return '\f'; }
            if (c == '\\' || c == '.' || c == '*' || c == '+' || c == '?'
                || c == '(' || c == ')' || c == '[' || c == ']'
                || c == '{' || c == '}' || c == '^' || c == '$'
                || c == '|' || c == '-' || c == '/') {
                return c;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')) {
                error("unsupported escape '\\" + c + "'");
            }
            // A backslash before other punctuation yields that punctuation.
            return c;
        }

        private Ast parseClass() {
            pos++; // consume '['
            boolean neg = false;
            if (pos < len && peek() == '^') {
                neg = true;
                pos++;
            }
            List parts = new ArrayList(); // of CharPredicate
            boolean first = true;
            while (true) {
                if (pos >= len) {
                    error("unclosed character class");
                }
                char c = peek();
                if (c == ']' && !first) {
                    pos++;
                    break;
                }
                first = false;

                ClassElem elem = readClassElem();
                if (elem.isPredicate) {
                    parts.add(elem.pred);
                    continue;
                }
                char lo = elem.ch;
                // Range: lo '-' hi, but only when '-' is not the class terminator.
                if (pos + 1 < len && peek() == '-' && peekAt(pos + 1) != ']') {
                    pos++; // consume '-'
                    ClassElem hiElem = readClassElem();
                    if (hiElem.isPredicate) {
                        error("bad character range");
                    }
                    parts.add(new Range(lo, hiElem.ch));
                } else {
                    parts.add(new Single(lo));
                }
            }
            CharPredicate[] arr = new CharPredicate[parts.size()];
            for (int k = 0; k < parts.size(); k++) {
                arr[k] = (CharPredicate) parts.get(k);
            }
            CharPredicate union = new Union(arr);
            return new ClazzAst(neg ? new Negate(union) : union, ci());
        }

        /** Reads one element inside a character class: a char or a predicate. */
        private ClassElem readClassElem() {
            char c = peek();
            if (c == '\\') {
                pos++; // consume backslash
                if (pos >= len) {
                    error("trailing backslash");
                }
                char e = peek();
                pos++;
                CharPredicate p = predefined(e);
                if (p != null) {
                    return new ClassElem(p);
                }
                return new ClassElem(escapedLiteral(e));
            }
            pos++;
            return new ClassElem(c);
        }
    }
}
