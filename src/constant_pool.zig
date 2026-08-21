//! The constant pool (JVMS 4.4). 1-indexed; Long and Double each occupy TWO
//! entries (the second is `.unusable`). Typed accessors validate index and kind
//! and return errors, never panic — usable by both parse-time checks and the
//! verifier.

const std = @import("std");
const Reader = @import("reader.zig").Reader;

pub const Tag = enum(u8) {
    utf8 = 1,
    integer = 3,
    float = 4,
    long = 5,
    double = 6,
    class = 7,
    string = 8,
    fieldref = 9,
    methodref = 10,
    interface_methodref = 11,
    name_and_type = 12,
    method_handle = 15,
    method_type = 16,
    dynamic = 17,
    invoke_dynamic = 18,
    module = 19,
    package = 20,
};

pub const RefInfo = struct {
    class_index: u16,
    name_and_type_index: u16,
};

pub const NameAndTypeInfo = struct {
    name_index: u16,
    descriptor_index: u16,
};

pub const MethodHandleInfo = struct {
    reference_kind: u8,
    reference_index: u16,
};

pub const DynamicInfo = struct {
    bootstrap_method_attr_index: u16,
    name_and_type_index: u16,
};

pub const Constant = union(enum) {
    /// Slot 0, and the second slot of a Long/Double.
    unusable,
    utf8: []const u8,
    integer: i32,
    float: f32,
    long: i64,
    double: f64,
    class: u16,
    string: u16,
    fieldref: RefInfo,
    methodref: RefInfo,
    interface_methodref: RefInfo,
    name_and_type: NameAndTypeInfo,
    method_handle: MethodHandleInfo,
    method_type: u16,
    dynamic: DynamicInfo,
    invoke_dynamic: DynamicInfo,
    module: u16,
    package: u16,
};

pub const ConstantPool = struct {
    /// 1-indexed; entries[0] is `.unusable`. Length == constant_pool_count.
    entries: []const Constant,

    pub const Error = error{ InvalidConstantIndex, WrongConstantType };

    /// Number of usable index slots (constant_pool_count), including the reserved 0.
    pub fn slotCount(self: ConstantPool) usize {
        return self.entries.len;
    }

    pub fn get(self: ConstantPool, index: u16) Error!*const Constant {
        if (index == 0 or index >= self.entries.len) return error.InvalidConstantIndex;
        const c = &self.entries[index];
        return switch (c.*) {
            .unusable => error.InvalidConstantIndex,
            else => c,
        };
    }

    pub fn utf8(self: ConstantPool, index: u16) Error![]const u8 {
        const c = try self.get(index);
        return switch (c.*) {
            .utf8 => |s| s,
            else => error.WrongConstantType,
        };
    }

    /// A Class constant's name (its name_index resolved to Utf8).
    pub fn classNameOf(self: ConstantPool, index: u16) Error![]const u8 {
        const c = try self.get(index);
        const name_index = switch (c.*) {
            .class => |ni| ni,
            else => return error.WrongConstantType,
        };
        return self.utf8(name_index);
    }
};

fn parseOne(arena: std.mem.Allocator, r: *Reader, tag: Tag) !Constant {
    return switch (tag) {
        .utf8 => blk: {
            const len = try r.readU16();
            const raw = try r.take(len);
            break :blk .{ .utf8 = try arena.dupe(u8, raw) };
        },
        .integer => .{ .integer = @bitCast(try r.readU32()) },
        .float => .{ .float = @bitCast(try r.readU32()) },
        .long => .{ .long = @bitCast(try r.readU64()) },
        .double => .{ .double = @bitCast(try r.readU64()) },
        .class => .{ .class = try r.readU16() },
        .string => .{ .string = try r.readU16() },
        .fieldref => .{ .fieldref = .{ .class_index = try r.readU16(), .name_and_type_index = try r.readU16() } },
        .methodref => .{ .methodref = .{ .class_index = try r.readU16(), .name_and_type_index = try r.readU16() } },
        .interface_methodref => .{ .interface_methodref = .{ .class_index = try r.readU16(), .name_and_type_index = try r.readU16() } },
        .name_and_type => .{ .name_and_type = .{ .name_index = try r.readU16(), .descriptor_index = try r.readU16() } },
        .method_handle => .{ .method_handle = .{ .reference_kind = try r.readU8(), .reference_index = try r.readU16() } },
        .method_type => .{ .method_type = try r.readU16() },
        .dynamic => .{ .dynamic = .{ .bootstrap_method_attr_index = try r.readU16(), .name_and_type_index = try r.readU16() } },
        .invoke_dynamic => .{ .invoke_dynamic = .{ .bootstrap_method_attr_index = try r.readU16(), .name_and_type_index = try r.readU16() } },
        .module => .{ .module = try r.readU16() },
        .package => .{ .package = try r.readU16() },
    };
}

/// Parse the constant pool. `count` is constant_pool_count (entries + 1).
pub fn parse(arena: std.mem.Allocator, r: *Reader, count: u16) !ConstantPool {
    const entries = try arena.alloc(Constant, count);
    entries[0] = .unusable;
    var i: usize = 1;
    while (i < count) {
        const tag_byte = try r.readU8();
        const tag = std.enums.fromInt(Tag, tag_byte) orelse return error.InvalidConstantTag;
        entries[i] = try parseOne(arena, r, tag);
        if (tag == .long or tag == .double) {
            // The second slot is unusable and must exist within the pool.
            if (i + 1 >= count) return error.Truncated;
            entries[i + 1] = .unusable;
            i += 2;
        } else {
            i += 1;
        }
    }
    return .{ .entries = entries };
}

const testing = std.testing;

test "parse a small pool: Utf8 + Class" {
    // #1 Utf8 "A", #2 Class -> #1
    const bytes = [_]u8{
        1, 0, 1, 'A', // #1 Utf8 len=1 "A"
        7, 0, 1, // #2 Class name_index=1
    };
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    const cp = try parse(arena.allocator(), &r, 3);
    try testing.expectEqual(@as(usize, 3), cp.slotCount());
    try testing.expectEqualStrings("A", try cp.utf8(1));
    try testing.expectEqualStrings("A", try cp.classNameOf(2));
}

test "long occupies two slots; next index is unusable" {
    const bytes = [_]u8{
        5, 0, 0, 0, 0, 0, 0, 0, 42, // #1 Long = 42 (occupies #1 and #2)
        3, 0, 0, 0, 7, // #3 Integer = 7
    };
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    const cp = try parse(arena.allocator(), &r, 4);
    try testing.expectEqual(Constant{ .long = 42 }, cp.entries[1]);
    try testing.expectEqual(Constant.unusable, cp.entries[2]);
    try testing.expectEqual(Constant{ .integer = 7 }, cp.entries[3]);
    // index 2 is unusable -> error
    try testing.expectError(error.InvalidConstantIndex, cp.get(2));
}

test "all remaining constant kinds parse" {
    const bytes = [_]u8{
        4, 0x40, 0x00, 0x00, 0x00, // #1 Float 2.0
        6, 0x40, 0x09, 0x21, 0xFB, 0x54, 0x44, 0x2D, 0x18, // #2 Double PI (occupies #2,#3)
        8, 0, 1, // #4 String -> #1
        9, 0, 1, 0, 1, // #5 Fieldref
        10, 0, 1, 0, 1, // #6 Methodref
        11, 0, 1, 0, 1, // #7 InterfaceMethodref
        12, 0, 1, 0, 1, // #8 NameAndType
        15, 6, 0, 1, // #9 MethodHandle kind=6 ref=1
        16, 0, 1, // #10 MethodType
        17, 0, 0, 0, 1, // #11 Dynamic
        18, 0, 0, 0, 1, // #12 InvokeDynamic
        19, 0, 1, // #13 Module
        20, 0, 1, // #14 Package
    };
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    const cp = try parse(arena.allocator(), &r, 15);
    try testing.expectEqual(@as(f32, 2.0), cp.entries[1].float);
    try testing.expectEqual(Constant.unusable, cp.entries[3]);
    try testing.expectEqual(@as(u16, 1), cp.entries[4].string);
    try testing.expectEqual(@as(u16, 1), cp.entries[5].fieldref.class_index);
    try testing.expectEqual(@as(u8, 6), cp.entries[9].method_handle.reference_kind);
    try testing.expectEqual(@as(u16, 1), cp.entries[13].module);
    try testing.expectEqual(@as(u16, 1), cp.entries[14].package);
}

test "unknown constant tag is rejected" {
    const bytes = [_]u8{ 2, 0, 0 }; // tag 2 is not valid
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    try testing.expectError(error.InvalidConstantTag, parse(arena.allocator(), &r, 2));
}

test "truncated Utf8 is rejected" {
    const bytes = [_]u8{ 1, 0, 5, 'A', 'B' }; // claims len 5, only 2 present
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    try testing.expectError(error.Truncated, parse(arena.allocator(), &r, 2));
}

test "long as the last entry (no room for its second slot) is rejected" {
    const bytes = [_]u8{ 5, 0, 0, 0, 0, 0, 0, 0, 1 }; // Long at #1, but count=2 leaves no #2
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    try testing.expectError(error.Truncated, parse(arena.allocator(), &r, 2));
}

test "accessors reject bad index and wrong type" {
    const bytes = [_]u8{ 1, 0, 1, 'A', 7, 0, 1 };
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    var r = Reader.init(&bytes);
    const cp = try parse(arena.allocator(), &r, 3);
    try testing.expectError(error.InvalidConstantIndex, cp.get(0));
    try testing.expectError(error.InvalidConstantIndex, cp.get(99));
    try testing.expectError(error.WrongConstantType, cp.utf8(2)); // #2 is a Class, not Utf8
    try testing.expectError(error.WrongConstantType, cp.classNameOf(1)); // #1 is Utf8, not Class
}
