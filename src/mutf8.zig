//! Modified UTF-8 (JVMS 4.4.7) — the encoding used for Utf8 constants. It differs
//! from standard UTF-8 in two ways:
//!   * U+0000 is encoded as the two bytes 0xC0 0x80 (never a bare 0x00).
//!   * Supplementary characters (> U+FFFF) are encoded as a UTF-16 surrogate pair,
//!     each surrogate written in the 3-byte form (6 bytes total; CESU-8 style).
//! `decodeAlloc` converts modified UTF-8 to standard UTF-8.

const std = @import("std");
const Allocator = std.mem.Allocator;

pub const DecodeError = error{ InvalidMutf8, Truncated } || Allocator.Error;

/// Decode modified UTF-8 `m` into freshly allocated standard UTF-8.
/// Standard-UTF-8 output is never longer than the modified-UTF-8 input.
pub fn decodeAlloc(a: Allocator, m: []const u8) DecodeError![]u8 {
    var out = try a.alloc(u8, m.len + 1); // +1 keeps the empty-input case non-zero
    errdefer a.free(out);
    var oi: usize = 0;
    var i: usize = 0;
    while (i < m.len) {
        const b0 = m[i];
        var cp: u21 = undefined;
        if (b0 & 0x80 == 0) {
            // 1-byte form: 0x01..0x7F. A bare 0x00 is illegal in modified UTF-8.
            if (b0 == 0) return error.InvalidMutf8;
            cp = b0;
            i += 1;
        } else if (b0 & 0xE0 == 0xC0) {
            // 2-byte form (includes the 0xC0 0x80 encoding of U+0000).
            if (i + 1 >= m.len) return error.Truncated;
            const b1 = m[i + 1];
            if (b1 & 0xC0 != 0x80) return error.InvalidMutf8;
            cp = (@as(u21, b0 & 0x1F) << 6) | (b1 & 0x3F);
            i += 2;
        } else if (b0 & 0xF0 == 0xE0) {
            // 3-byte form: a BMP char, or half of a surrogate pair.
            if (i + 2 >= m.len) return error.Truncated;
            const b1 = m[i + 1];
            const b2 = m[i + 2];
            if (b1 & 0xC0 != 0x80 or b2 & 0xC0 != 0x80) return error.InvalidMutf8;
            const v: u21 = (@as(u21, b0 & 0x0F) << 12) | (@as(u21, b1 & 0x3F) << 6) | (b2 & 0x3F);
            if (v >= 0xD800 and v <= 0xDBFF) {
                // high surrogate: a low-surrogate 3-byte form must follow.
                if (i + 5 >= m.len) return error.Truncated;
                const c0 = m[i + 3];
                const c1 = m[i + 4];
                const c2 = m[i + 5];
                if (c0 & 0xF0 != 0xE0 or c1 & 0xC0 != 0x80 or c2 & 0xC0 != 0x80) return error.InvalidMutf8;
                const lo: u21 = (@as(u21, c0 & 0x0F) << 12) | (@as(u21, c1 & 0x3F) << 6) | (c2 & 0x3F);
                if (lo < 0xDC00 or lo > 0xDFFF) return error.InvalidMutf8;
                cp = 0x10000 + ((v - 0xD800) << 10) + (lo - 0xDC00);
                i += 6;
            } else if (v >= 0xDC00 and v <= 0xDFFF) {
                return error.InvalidMutf8; // lone low surrogate
            } else {
                cp = v;
                i += 3;
            }
        } else {
            // 4-byte standard-UTF-8 form is not permitted in modified UTF-8.
            return error.InvalidMutf8;
        }
        const n = std.unicode.utf8Encode(cp, out[oi..]) catch return error.InvalidMutf8;
        oi += n;
    }
    return a.realloc(out, oi);
}

const testing = std.testing;

test "ascii" {
    const out = try decodeAlloc(testing.allocator, "Hello");
    defer testing.allocator.free(out);
    try testing.expectEqualStrings("Hello", out);
}

test "empty" {
    const out = try decodeAlloc(testing.allocator, "");
    defer testing.allocator.free(out);
    try testing.expectEqual(@as(usize, 0), out.len);
}

test "embedded null 0xC0 0x80 decodes to a single 0x00" {
    const out = try decodeAlloc(testing.allocator, &.{ 0xC0, 0x80 });
    defer testing.allocator.free(out);
    try testing.expectEqualSlices(u8, &.{0x00}, out);
}

test "two-byte BMP char (U+00E9 e-acute)" {
    // é = U+00E9 -> modified UTF-8 C3 A9 -> standard UTF-8 C3 A9
    const out = try decodeAlloc(testing.allocator, &.{ 0xC3, 0xA9 });
    defer testing.allocator.free(out);
    try testing.expectEqualSlices(u8, &.{ 0xC3, 0xA9 }, out);
}

test "three-byte BMP char (U+20AC euro)" {
    const out = try decodeAlloc(testing.allocator, &.{ 0xE2, 0x82, 0xAC });
    defer testing.allocator.free(out);
    try testing.expectEqualSlices(u8, &.{ 0xE2, 0x82, 0xAC }, out);
}

test "supplementary char via surrogate pair (U+1F600) -> 4-byte standard UTF-8" {
    // U+1F600: surrogates D83D DE00; modified UTF-8 = ED A0 BD  ED B8 80
    const out = try decodeAlloc(testing.allocator, &.{ 0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80 });
    defer testing.allocator.free(out);
    try testing.expectEqualSlices(u8, &.{ 0xF0, 0x9F, 0x98, 0x80 }, out);
}

test "rejects bare null" {
    try testing.expectError(error.InvalidMutf8, decodeAlloc(testing.allocator, &.{0x00}));
}

test "rejects truncated multibyte" {
    try testing.expectError(error.Truncated, decodeAlloc(testing.allocator, &.{0xC3}));
    try testing.expectError(error.Truncated, decodeAlloc(testing.allocator, &.{ 0xE2, 0x82 }));
}

test "rejects bad continuation byte" {
    try testing.expectError(error.InvalidMutf8, decodeAlloc(testing.allocator, &.{ 0xC3, 0x00 }));
    try testing.expectError(error.InvalidMutf8, decodeAlloc(testing.allocator, &.{ 0xE2, 0x82, 0x00 }));
}

test "rejects lone low surrogate" {
    // ED B8 80 = low surrogate DC00 with no preceding high surrogate
    try testing.expectError(error.InvalidMutf8, decodeAlloc(testing.allocator, &.{ 0xED, 0xB8, 0x80 }));
}

test "rejects high surrogate not followed by low surrogate" {
    // high surrogate D83D followed by a normal BMP 3-byte char (euro)
    try testing.expectError(error.InvalidMutf8, decodeAlloc(testing.allocator, &.{ 0xED, 0xA0, 0xBD, 0xE2, 0x82, 0xAC }));
}

test "rejects 4-byte standard utf8 form" {
    try testing.expectError(error.InvalidMutf8, decodeAlloc(testing.allocator, &.{ 0xF0, 0x9F, 0x98, 0x80 }));
}
