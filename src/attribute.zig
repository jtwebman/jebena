//! Generic attribute structure (JVMS 4.7). We parse the common envelope
//! (name_index + raw info bytes, copied into the arena). Specific attribute
//! bodies (Code, StackMapTable, ...) are decoded in a later phase.

const std = @import("std");
const Reader = @import("reader.zig").Reader;

pub const AttributeInfo = struct {
    name_index: u16,
    /// Raw attribute bytes, owned by the ClassFile arena.
    info: []const u8,
};

/// Parse `count` attributes from `r`.
pub fn parseAttributes(arena: std.mem.Allocator, r: *Reader, count: u16) ![]AttributeInfo {
    const attrs = try arena.alloc(AttributeInfo, count);
    var i: usize = 0;
    while (i < count) : (i += 1) {
        const name_index = try r.readU16();
        const length = try r.readU32();
        const raw = try r.take(length); // bounds-checked: a bogus huge length -> Truncated
        attrs[i] = .{ .name_index = name_index, .info = try arena.dupe(u8, raw) };
    }
    return attrs;
}

const testing = std.testing;

test "parse two attributes" {
    const bytes = [_]u8{
        0, 1, 0, 0, 0, 2, 0xAB, 0xCD, // attr name=1 len=2 [AB CD]
        0, 2, 0, 0, 0, 0, // attr name=2 len=0 []
    };
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    const attrs = try parseAttributes(arena.allocator(), &r, 2);
    try testing.expectEqual(@as(usize, 2), attrs.len);
    try testing.expectEqual(@as(u16, 1), attrs[0].name_index);
    try testing.expectEqualSlices(u8, &.{ 0xAB, 0xCD }, attrs[0].info);
    try testing.expectEqual(@as(usize, 0), attrs[1].info.len);
}

test "attribute with an over-long length is rejected, not over-read" {
    const bytes = [_]u8{ 0, 1, 0x00, 0x00, 0x10, 0x00, 0x01 }; // len=4096, 1 byte present
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    try testing.expectError(error.Truncated, parseAttributes(arena.allocator(), &r, 1));
}

test "zero attributes" {
    const bytes = [_]u8{};
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    const attrs = try parseAttributes(arena.allocator(), &r, 0);
    try testing.expectEqual(@as(usize, 0), attrs.len);
}
