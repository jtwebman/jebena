//! Big-endian, fully bounds-checked byte cursor over a class file.
//! Every read is bounds-checked; malformed/truncated input yields error.Truncated,
//! never an out-of-bounds read or panic. This is the security boundary (see
//! docs/research/01-classfile-verifier.md section 3).

const std = @import("std");

pub const Reader = struct {
    data: []const u8,
    pos: usize = 0,

    pub const Error = error{Truncated};

    pub fn init(data: []const u8) Reader {
        return .{ .data = data };
    }

    /// Bytes not yet consumed.
    pub fn remaining(self: *const Reader) usize {
        return self.data.len - self.pos;
    }

    pub fn atEnd(self: *const Reader) bool {
        return self.pos >= self.data.len;
    }

    /// Take exactly `n` bytes, advancing the cursor. The one place bounds are checked.
    pub fn take(self: *Reader, n: usize) Error![]const u8 {
        if (n > self.remaining()) return error.Truncated;
        const s = self.data[self.pos .. self.pos + n];
        self.pos += n;
        return s;
    }

    pub fn readU8(self: *Reader) Error!u8 {
        const s = try self.take(1);
        return s[0];
    }

    pub fn readU16(self: *Reader) Error!u16 {
        const s = try self.take(2);
        return std.mem.readInt(u16, s[0..2], .big);
    }

    pub fn readU32(self: *Reader) Error!u32 {
        const s = try self.take(4);
        return std.mem.readInt(u32, s[0..4], .big);
    }

    pub fn readU64(self: *Reader) Error!u64 {
        const s = try self.take(8);
        return std.mem.readInt(u64, s[0..8], .big);
    }
};

const testing = std.testing;

test "reads big-endian integers and advances" {
    const bytes = [_]u8{ 0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x10 };
    var r = Reader.init(&bytes);
    try testing.expectEqual(@as(usize, 6), r.remaining());
    try testing.expectEqual(@as(u32, 0xCAFEBABE), try r.readU32());
    try testing.expectEqual(@as(u16, 0x0010), try r.readU16());
    try testing.expect(r.atEnd());
    try testing.expectEqual(@as(usize, 0), r.remaining());
}

test "readU8 sequences" {
    const bytes = [_]u8{ 1, 2, 3 };
    var r = Reader.init(&bytes);
    try testing.expectEqual(@as(u8, 1), try r.readU8());
    try testing.expectEqual(@as(u8, 2), try r.readU8());
    try testing.expectEqual(@as(u8, 3), try r.readU8());
    try testing.expectError(error.Truncated, r.readU8());
}

test "readU64" {
    const bytes = [_]u8{ 0, 0, 0, 0, 0, 0, 0, 42 };
    var r = Reader.init(&bytes);
    try testing.expectEqual(@as(u64, 42), try r.readU64());
}

test "truncation is reported, never over-read" {
    const bytes = [_]u8{0x00};
    var r = Reader.init(&bytes);
    try testing.expectError(error.Truncated, r.readU16());
    // cursor unchanged after a failed read
    try testing.expectEqual(@as(usize, 0), r.pos);
    try testing.expectError(error.Truncated, r.readU32());
    try testing.expectError(error.Truncated, r.readU64());
}

test "take zero bytes is valid" {
    const bytes = [_]u8{42};
    var r = Reader.init(&bytes);
    const s = try r.take(0);
    try testing.expectEqual(@as(usize, 0), s.len);
    try testing.expectEqual(@as(usize, 0), r.pos);
}

test "take exactly to the end, then truncates" {
    const bytes = [_]u8{ 1, 2 };
    var r = Reader.init(&bytes);
    const s = try r.take(2);
    try testing.expectEqual(@as(usize, 2), s.len);
    try testing.expectError(error.Truncated, r.take(1));
}
