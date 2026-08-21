//! Static/semantic verification (JVMS 4.8, 4.9) — "Pass 2". Checks that the
//! constant pool is internally consistent (every index references a constant of
//! the expected kind), that this/super/interfaces are Class constants, and that
//! members have Utf8 names and valid descriptors. Bytecode type-checking
//! (StackMapTable, Pass 3) is a separate, later pass.
//!
//! Allocation-free: it only reads the already-parsed ClassFile.

const std = @import("std");
const ClassFile = @import("class_file.zig").ClassFile;
const MemberInfo = @import("class_file.zig").MemberInfo;
const constant_pool = @import("constant_pool.zig");
const ConstantPool = constant_pool.ConstantPool;
const AttributeInfo = @import("attribute.zig").AttributeInfo;
const descriptor = @import("descriptor.zig");

pub const VerifyError = error{
    BadConstantReference,
    BadThisClass,
    BadSuperClass,
    BadInterface,
    BadMemberName,
    BadMemberDescriptor,
    BadAttributeName,
    BadReferenceKind,
};

fn tag(c: *const constant_pool.Constant) std.meta.Tag(constant_pool.Constant) {
    return std.meta.activeTag(c.*);
}

fn requireUtf8(cp: ConstantPool, i: u16) VerifyError!void {
    const c = cp.get(i) catch return error.BadConstantReference;
    if (tag(c) != .utf8) return error.BadConstantReference;
}

fn requireClass(cp: ConstantPool, i: u16) VerifyError!void {
    const c = cp.get(i) catch return error.BadConstantReference;
    if (tag(c) != .class) return error.BadConstantReference;
}

fn requireNameAndType(cp: ConstantPool, i: u16) VerifyError!void {
    const c = cp.get(i) catch return error.BadConstantReference;
    if (tag(c) != .name_and_type) return error.BadConstantReference;
}

fn requireRef(cp: ConstantPool, i: u16) VerifyError!void {
    const c = cp.get(i) catch return error.BadConstantReference;
    switch (tag(c)) {
        .fieldref, .methodref, .interface_methodref => {},
        else => return error.BadConstantReference,
    }
}

fn verifyAttrNames(cp: ConstantPool, attrs: []const AttributeInfo) VerifyError!void {
    for (attrs) |a| {
        requireUtf8(cp, a.name_index) catch return error.BadAttributeName;
    }
}

fn verifyMembers(cp: ConstantPool, members: []const MemberInfo, is_method: bool) VerifyError!void {
    for (members) |m| {
        requireUtf8(cp, m.name_index) catch return error.BadMemberName;
        const d = cp.utf8(m.descriptor_index) catch return error.BadMemberDescriptor;
        if (is_method) {
            descriptor.validateMethodDescriptor(d) catch return error.BadMemberDescriptor;
        } else {
            descriptor.validateFieldDescriptor(d) catch return error.BadMemberDescriptor;
        }
        try verifyAttrNames(cp, m.attributes);
    }
}

/// Run Pass 2 verification over a parsed class file.
pub fn verify(cf: *const ClassFile) VerifyError!void {
    const cp = cf.constant_pool;

    // 1. Constant pool internal consistency.
    var i: u16 = 1;
    while (i < cp.entries.len) : (i += 1) {
        switch (cp.entries[i]) {
            .class, .string, .method_type, .module, .package => |idx| try requireUtf8(cp, idx),
            .fieldref, .methodref, .interface_methodref => |r| {
                try requireClass(cp, r.class_index);
                try requireNameAndType(cp, r.name_and_type_index);
            },
            .name_and_type => |nt| {
                try requireUtf8(cp, nt.name_index);
                try requireUtf8(cp, nt.descriptor_index);
            },
            .method_handle => |mh| {
                if (mh.reference_kind < 1 or mh.reference_kind > 9) return error.BadReferenceKind;
                try requireRef(cp, mh.reference_index);
            },
            .dynamic, .invoke_dynamic => |d| try requireNameAndType(cp, d.name_and_type_index),
            else => {}, // utf8/integer/float/long/double/unusable: no references
        }
    }

    // 2. this_class / super_class / interfaces.
    requireClass(cp, cf.this_class) catch return error.BadThisClass;
    if (cf.super_class != 0) {
        requireClass(cp, cf.super_class) catch return error.BadSuperClass;
    }
    for (cf.interfaces) |ix| {
        requireClass(cp, ix) catch return error.BadInterface;
    }

    // 3. Members.
    try verifyMembers(cp, cf.fields, false);
    try verifyMembers(cp, cf.methods, true);

    // 4. Class-level attributes.
    try verifyAttrNames(cp, cf.attributes);
}

const testing = std.testing;

test "real classes pass verification" {
    inline for (.{ "testdata/Hello.class", "testdata/Sample.class" }) |path| {
        const bytes = @embedFile(path);
        var cf = try ClassFile.parse(testing.allocator, bytes);
        defer cf.deinit();
        try verify(&cf);
    }
}

// Helpers to build tiny malformed classes: header + cp + tail.
fn parseAndVerify(bytes: []const u8) !void {
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    try verify(&cf);
}

test "this_class must be a Class constant" {
    // #1 Utf8 "A", #2 Class->#1, but this_class points at #1 (a Utf8).
    const bytes = [_]u8{
        0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 0x45, // magic/ver
        0, 3, // cp_count
        1, 0, 1, 'A', // #1 Utf8
        7, 0, 1, // #2 Class
        0, 1, // access
        0, 1, // this_class = #1 (Utf8!) -> bad
        0, 0, // super
        0, 0, 0, 0, 0, 0, // ifaces/fields/methods
        0, 0, // attrs
    };
    try testing.expectError(error.BadThisClass, parseAndVerify(&bytes));
}

test "method with an invalid descriptor is rejected" {
    // cp: #1 "A", #2 Class->#1, #3 "m", #4 "xyz" (bad method descriptor)
    const bytes = [_]u8{
        0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 0x45,
        0, 5, // cp_count = 5
        1, 0, 1, 'A', // #1
        7, 0, 1, // #2 Class
        1, 0, 1, 'm', // #3 Utf8 "m"
        1, 0, 3, 'x', 'y', 'z', // #4 Utf8 "xyz"
        0, 1, // access
        0, 2, // this_class = #2
        0, 0, // super
        0, 0, // ifaces
        0, 0, // fields
        0, 1, // methods = 1
        0, 0, 0, 3, 0, 4, 0, 0, // method: access=0 name=#3 desc=#4 attrs=0
        0, 0, // class attrs
    };
    try testing.expectError(error.BadMemberDescriptor, parseAndVerify(&bytes));
}

test "constant pool cross-reference is checked (Fieldref pointing at non-Class)" {
    // #1 Utf8 "A", #2 Class->#1, #3 NameAndType(#1,#1), #4 Fieldref(class=#1(Utf8!),nat=#3)
    const bytes = [_]u8{
        0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 0x45,
        0,    5,
        1, 0, 1, 'A', // #1
        7, 0, 1, // #2 Class
        12, 0, 1, 0, 1, // #3 NameAndType(#1,#1)
        9, 0, 1, 0, 3, // #4 Fieldref(class=#1 Utf8 -> bad, nat=#3)
        0, 1,
        0, 2, // this_class=#2
        0, 0,
        0, 0,
        0, 0,
        0, 0,
        0, 0,
    };
    try testing.expectError(error.BadConstantReference, parseAndVerify(&bytes));
}
