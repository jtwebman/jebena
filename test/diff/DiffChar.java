/**
 * Differential coverage for java.lang.Character classification on the eager jbase
 * path (previously intrinsic-only, missing on the real loaded class): isDigit/
 * isLetter/isLetterOrDigit/isUpperCase/isLowerCase/isWhitespace, toUpperCase/
 * toLowerCase, digit(c,radix), getNumericValue. Each returns a deterministic int.
 */
public class DiffChar {
    private static int b(boolean v) {
        return v ? 1 : 0;
    }

    static int classify() {
        return b(Character.isDigit('7')) * 100000
                + b(Character.isDigit('x')) * 10000
                + b(Character.isLetter('x')) * 1000
                + b(Character.isLetter('9')) * 100
                + b(Character.isLetterOrDigit('_')) * 10
                + b(Character.isLetterOrDigit('Q')); // 1 0 1 0 0 1 -> 101001
    }

    static int caseTests() {
        return b(Character.isUpperCase('A')) * 1000
                + b(Character.isLowerCase('a')) * 100
                + Character.toUpperCase('m') // 'M' = 77
                + Character.toLowerCase('M') * 0; // keep it int-y: 77
        // 1000 + 100 + 77 = 1177
    }

    static int caseConvert() {
        int acc = 0;
        String s = "Hello, World! 123";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char u = Character.toUpperCase(c);
            char l = Character.toLowerCase(c);
            acc = acc * 31 + u + l;
        }
        return acc;
    }

    static int whitespace() {
        return b(Character.isWhitespace(' ')) * 1000
                + b(Character.isWhitespace('\t')) * 100
                + b(Character.isWhitespace('\n')) * 10
                + b(Character.isWhitespace('x')); // 1110
    }

    static int digitRadix() {
        return Character.digit('f', 16) * 1000 // 15
                + Character.digit('7', 8) * 10 // 7
                + (Character.digit('8', 8) + 1); // -1+1=0 -> 15000 + 70 + 0 = 15070
    }

    static int numeric() {
        return Character.getNumericValue('9') * 1000 // 9
                + Character.getNumericValue('a') * 10 // 10
                + (Character.getNumericValue('!') + 5); // -1+5=4 -> 9000 + 100 + 4 = 9104
    }
}
