//! The top-level class file (JVMS 4.1). Owns all parsed data via an internal
//! arena (single free on deinit); it does not borrow the input buffer.

const std = @import("std");
const Reader = @import("reader.zig").Reader;
const constant_pool = @import("constant_pool.zig");
const attribute = @import("attribute.zig");
const AccessFlags = @import("access_flags.zig").AccessFlags;

pub const ConstantPool = constant_pool.ConstantPool;
pub const AttributeInfo = attribute.AttributeInfo;

/// Java SE version -> major = 44 + N. We target Java SE 25.
pub const min_major: u16 = 45; // Java 1.1
pub const max_major: u16 = 69; // Java SE 25

pub const magic: u32 = 0xCAFEBABE;

/// field_info and method_info share this shape (JVMS 4.5, 4.6).
pub const MemberInfo = struct {
    access_flags: AccessFlags,
    name_index: u16,
    descriptor_index: u16,
    attributes: []const AttributeInfo,
};

pub const ClassFile = struct {
    arena: std.heap.ArenaAllocator,
    minor_version: u16,
    major_version: u16,
    constant_pool: ConstantPool,
    access_flags: AccessFlags,
    this_class: u16,
    super_class: u16,
    interfaces: []const u16,
    fields: []const MemberInfo,
    methods: []const MemberInfo,
    attributes: []const AttributeInfo,

    pub fn deinit(self: *ClassFile) void {
        self.arena.deinit();
    }

    /// Parse a complete class file. `bytes` is only borrowed for the duration of
    /// this call; the returned ClassFile owns its data.
    pub fn parse(gpa: std.mem.Allocator, bytes: []const u8) !ClassFile {
        var arena = std.heap.ArenaAllocator.init(gpa);
        errdefer arena.deinit();
        const a = arena.allocator();
        var r = Reader.init(bytes);

        if (try r.readU32() != magic) return error.BadMagic;
        const minor = try r.readU16();
        const major = try r.readU16();
        if (major < min_major or major > max_major) return error.UnsupportedVersion;

        const cp_count = try r.readU16();
        if (cp_count < 1) return error.ConstantPoolTooSmall;
        const cp = try constant_pool.parse(a, &r, cp_count);

        const access = AccessFlags.init(try r.readU16());
        const this_class = try r.readU16();
        const super_class = try r.readU16();

        const ifaces_count = try r.readU16();
        const ifaces = try a.alloc(u16, ifaces_count);
        for (ifaces) |*x| x.* = try r.readU16();

        const fields = try parseMembers(a, &r, try r.readU16());
        const methods = try parseMembers(a, &r, try r.readU16());
        const attrs = try attribute.parseAttributes(a, &r, try r.readU16());

        return .{
            .arena = arena,
            .minor_version = minor,
            .major_version = major,
            .constant_pool = cp,
            .access_flags = access,
            .this_class = this_class,
            .super_class = super_class,
            .interfaces = ifaces,
            .fields = fields,
            .methods = methods,
            .attributes = attrs,
        };
    }
};

fn parseMembers(a: std.mem.Allocator, r: *Reader, count: u16) ![]MemberInfo {
    const members = try a.alloc(MemberInfo, count);
    var i: usize = 0;
    while (i < count) : (i += 1) {
        const access = AccessFlags.init(try r.readU16());
        const name_index = try r.readU16();
        const descriptor_index = try r.readU16();
        const attr_count = try r.readU16();
        const attrs = try attribute.parseAttributes(a, r, attr_count);
        members[i] = .{
            .access_flags = access,
            .name_index = name_index,
            .descriptor_index = descriptor_index,
            .attributes = attrs,
        };
    }
    return members;
}

const testing = std.testing;

/// A minimal but well-formed class: class "A", extends nothing (super=0),
/// no interfaces/fields/methods/attributes.
const minimal_class = [_]u8{
    0xCA, 0xFE, 0xBA, 0xBE, // magic
    0x00, 0x00, // minor
    0x00, 0x45, // major 69 (Java 25)
    0x00, 0x03, // cp_count = 3
    1, 0, 1, 'A', // #1 Utf8 "A"
    7, 0, 1, // #2 Class -> #1
    0x00, 0x01, // access_flags = PUBLIC
    0x00, 0x02, // this_class = #2
    0x00, 0x00, // super_class = 0
    0x00, 0x00, // interfaces_count = 0
    0x00, 0x00, // fields_count = 0
    0x00, 0x00, // methods_count = 0
    0x00, 0x00, // attributes_count = 0
};

test "parse a minimal hand-crafted class" {
    var cf = try ClassFile.parse(testing.allocator, &minimal_class);
    defer cf.deinit();
    try testing.expectEqual(@as(u16, 69), cf.major_version);
    try testing.expectEqual(@as(u16, 0), cf.minor_version);
    try testing.expect(cf.access_flags.isPublic());
    try testing.expectEqual(@as(u16, 2), cf.this_class);
    try testing.expectEqual(@as(u16, 0), cf.super_class);
    try testing.expectEqual(@as(usize, 0), cf.interfaces.len);
    try testing.expectEqual(@as(usize, 0), cf.fields.len);
    try testing.expectEqual(@as(usize, 0), cf.methods.len);
    try testing.expectEqualStrings("A", try cf.constant_pool.classNameOf(cf.this_class));
}

test "bad magic is rejected" {
    var bytes = minimal_class;
    bytes[0] = 0x00;
    try testing.expectError(error.BadMagic, ClassFile.parse(testing.allocator, &bytes));
}

test "unsupported version (too new) is rejected" {
    var bytes = minimal_class;
    bytes[7] = 70; // major 70 > 69
    try testing.expectError(error.UnsupportedVersion, ClassFile.parse(testing.allocator, &bytes));
}

test "unsupported version (too old) is rejected" {
    var bytes = minimal_class;
    bytes[7] = 44; // major 44 < 45
    try testing.expectError(error.UnsupportedVersion, ClassFile.parse(testing.allocator, &bytes));
}

test "zero constant pool count is rejected" {
    var bytes = minimal_class;
    bytes[8] = 0;
    bytes[9] = 0;
    try testing.expectError(error.ConstantPoolTooSmall, ClassFile.parse(testing.allocator, &bytes));
}

test "truncation at every prefix length only ever errors, never crashes" {
    // Every proper prefix of a valid class file must be rejected cleanly.
    var n: usize = 0;
    while (n < minimal_class.len) : (n += 1) {
        try testing.expectError(error.Truncated, ClassFile.parse(testing.allocator, minimal_class[0..n]));
    }
    // The full thing parses.
    var cf = try ClassFile.parse(testing.allocator, &minimal_class);
    cf.deinit();
}

test "parses a real javac-compiled class (integration)" {
    const bytes = @embedFile("testdata/Hello.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    try testing.expectEqual(@as(u16, 61), cf.major_version); // javac 17
    try testing.expectEqualStrings("Hello", try cf.constant_pool.classNameOf(cf.this_class));
    // Hello implements java.io.Serializable
    try testing.expectEqual(@as(usize, 1), cf.interfaces.len);
    try testing.expectEqualStrings("java/io/Serializable", try cf.constant_pool.classNameOf(cf.interfaces[0]));
    // one field (int x), plus static final long BIG => 2 fields
    try testing.expectEqual(@as(usize, 2), cf.fields.len);
    // main + area + <init> => 3 methods
    try testing.expectEqual(@as(usize, 3), cf.methods.len);
    try testing.expect(cf.access_flags.isPublic());
}
