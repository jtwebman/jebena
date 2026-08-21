//! Field and method descriptor parsing (JVMS 4.3).
//!
//! FieldType   := BaseType | ObjectType | ArrayType
//! BaseType    := B C D F I J S Z
//! ObjectType  := L ClassName ;
//! ArrayType   := [ ComponentType
//! MethodType  := ( FieldType* ) ( FieldType | V )
//!
//! Object names are borrowed from the input string; the caller keeps it alive.

const std = @import("std");
const Allocator = std.mem.Allocator;

pub const BaseType = enum { byte, char, double, float, int, long, short, boolean };

pub const FieldKind = union(enum) {
    base: BaseType,
    /// Internal (slash-separated) class name, borrowed from the input.
    object: []const u8,
};

pub const FieldType = struct {
    /// Number of array dimensions (0 = not an array). JVMS caps this at 255.
    dims: u8,
    kind: FieldKind,
};

pub const MethodType = struct {
    params: []FieldType,
    /// Return type; null means void (V).
    ret: ?FieldType,
};

pub const Error = error{BadDescriptor} || Allocator.Error;

const Cursor = struct { s: []const u8, i: usize = 0 };

fn parseField(p: *Cursor) Error!FieldType {
    var dims: u8 = 0;
    while (p.i < p.s.len and p.s[p.i] == '[') {
        if (dims == 255) return error.BadDescriptor;
        dims += 1;
        p.i += 1;
    }
    if (p.i >= p.s.len) return error.BadDescriptor;
    const c = p.s[p.i];
    p.i += 1;
    const kind: FieldKind = switch (c) {
        'B' => .{ .base = .byte },
        'C' => .{ .base = .char },
        'D' => .{ .base = .double },
        'F' => .{ .base = .float },
        'I' => .{ .base = .int },
        'J' => .{ .base = .long },
        'S' => .{ .base = .short },
        'Z' => .{ .base = .boolean },
        'L' => blk: {
            const start = p.i;
            while (p.i < p.s.len and p.s[p.i] != ';') p.i += 1;
            if (p.i >= p.s.len) return error.BadDescriptor; // no terminating ';'
            const name = p.s[start..p.i];
            if (name.len == 0) return error.BadDescriptor; // empty class name
            p.i += 1; // consume ';'
            break :blk .{ .object = name };
        },
        else => return error.BadDescriptor,
    };
    return .{ .dims = dims, .kind = kind };
}

/// Parse a single field descriptor; the whole string must be consumed.
pub fn parseFieldDescriptor(s: []const u8) Error!FieldType {
    var p = Cursor{ .s = s };
    const ft = try parseField(&p);
    if (p.i != s.len) return error.BadDescriptor; // trailing junk
    return ft;
}

/// Parse a method descriptor. `params` is allocated with `a`.
pub fn parseMethodDescriptor(a: Allocator, s: []const u8) Error!MethodType {
    var p = Cursor{ .s = s };
    if (p.i >= s.len or s[p.i] != '(') return error.BadDescriptor;
    p.i += 1;

    // Each parameter consumes >= 1 char, so s.len is a safe upper bound.
    var params = try a.alloc(FieldType, s.len);
    errdefer a.free(params);
    var n: usize = 0;
    while (p.i < s.len and s[p.i] != ')') {
        params[n] = try parseField(&p);
        n += 1;
    }
    if (p.i >= s.len or s[p.i] != ')') return error.BadDescriptor;
    p.i += 1; // consume ')'

    var ret: ?FieldType = null;
    if (p.i < s.len and s[p.i] == 'V') {
        p.i += 1;
    } else {
        ret = try parseField(&p);
    }
    if (p.i != s.len) return error.BadDescriptor; // trailing junk

    params = try a.realloc(params, n);
    return .{ .params = params, .ret = ret };
}

/// Validate a field descriptor without allocating.
pub fn validateFieldDescriptor(s: []const u8) Error!void {
    _ = try parseFieldDescriptor(s);
}

/// Validate a method descriptor without allocating (unlike parseMethodDescriptor).
pub fn validateMethodDescriptor(s: []const u8) Error!void {
    var p = Cursor{ .s = s };
    if (p.i >= s.len or s[p.i] != '(') return error.BadDescriptor;
    p.i += 1;
    while (p.i < s.len and s[p.i] != ')') {
        _ = try parseField(&p);
    }
    if (p.i >= s.len or s[p.i] != ')') return error.BadDescriptor;
    p.i += 1;
    if (p.i < s.len and s[p.i] == 'V') {
        p.i += 1;
    } else {
        _ = try parseField(&p);
    }
    if (p.i != s.len) return error.BadDescriptor;
}

const testing = std.testing;

test "base field types" {
    try testing.expectEqual(FieldType{ .dims = 0, .kind = .{ .base = .int } }, try parseFieldDescriptor("I"));
    try testing.expectEqual(FieldType{ .dims = 0, .kind = .{ .base = .long } }, try parseFieldDescriptor("J"));
    try testing.expectEqual(FieldType{ .dims = 0, .kind = .{ .base = .boolean } }, try parseFieldDescriptor("Z"));
    try testing.expectEqual(FieldType{ .dims = 0, .kind = .{ .base = .double } }, try parseFieldDescriptor("D"));
}

test "object type" {
    const ft = try parseFieldDescriptor("Ljava/lang/String;");
    try testing.expectEqual(@as(u8, 0), ft.dims);
    try testing.expectEqualStrings("java/lang/String", ft.kind.object);
}

test "array types" {
    const a = try parseFieldDescriptor("[[J");
    try testing.expectEqual(@as(u8, 2), a.dims);
    try testing.expectEqual(BaseType.long, a.kind.base);

    const b = try parseFieldDescriptor("[Ljava/lang/Object;");
    try testing.expectEqual(@as(u8, 1), b.dims);
    try testing.expectEqualStrings("java/lang/Object", b.kind.object);
}

test "bad field descriptors" {
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor(""));
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("X"));
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("L")); // no name/;
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("Ljava")); // no ;
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("L;")); // empty name
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("II")); // trailing junk
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("[")); // array with no component
    try testing.expectError(error.BadDescriptor, parseFieldDescriptor("V")); // V is not a field type
}

test "method descriptor: no params, void" {
    const m = try parseMethodDescriptor(testing.allocator, "()V");
    defer testing.allocator.free(m.params);
    try testing.expectEqual(@as(usize, 0), m.params.len);
    try testing.expectEqual(@as(?FieldType, null), m.ret);
}

test "method descriptor: mixed params and object return (JVMS example)" {
    const m = try parseMethodDescriptor(testing.allocator, "(IDLjava/lang/Thread;)Ljava/lang/Object;");
    defer testing.allocator.free(m.params);
    try testing.expectEqual(@as(usize, 3), m.params.len);
    try testing.expectEqual(BaseType.int, m.params[0].kind.base);
    try testing.expectEqual(BaseType.double, m.params[1].kind.base);
    try testing.expectEqualStrings("java/lang/Thread", m.params[2].kind.object);
    try testing.expectEqualStrings("java/lang/Object", m.ret.?.kind.object);
}

test "method descriptor: array param" {
    const m = try parseMethodDescriptor(testing.allocator, "([[I)V");
    defer testing.allocator.free(m.params);
    try testing.expectEqual(@as(usize, 1), m.params.len);
    try testing.expectEqual(@as(u8, 2), m.params[0].dims);
    try testing.expectEqual(BaseType.int, m.params[0].kind.base);
    try testing.expectEqual(@as(?FieldType, null), m.ret);
}

test "method descriptor: base return type" {
    const m = try parseMethodDescriptor(testing.allocator, "(Ljava/lang/String;)I");
    defer testing.allocator.free(m.params);
    try testing.expectEqual(@as(usize, 1), m.params.len);
    try testing.expectEqual(BaseType.int, m.ret.?.kind.base);
}

test "bad method descriptors" {
    try testing.expectError(error.BadDescriptor, parseMethodDescriptor(testing.allocator, "V")); // no '('
    try testing.expectError(error.BadDescriptor, parseMethodDescriptor(testing.allocator, "()X")); // bad return
    try testing.expectError(error.BadDescriptor, parseMethodDescriptor(testing.allocator, "(I")); // unclosed
    try testing.expectError(error.BadDescriptor, parseMethodDescriptor(testing.allocator, "()Ijunk")); // trailing
    try testing.expectError(error.BadDescriptor, parseMethodDescriptor(testing.allocator, "(")); // just '('
}

test "descriptor validators (non-allocating)" {
    try descriptorValidatorsOk();
    try testing.expectError(error.BadDescriptor, validateFieldDescriptor("Q"));
    try testing.expectError(error.BadDescriptor, validateMethodDescriptor("()Q"));
    try testing.expectError(error.BadDescriptor, validateMethodDescriptor("I)V"));
}

fn descriptorValidatorsOk() Error!void {
    try validateFieldDescriptor("I");
    try validateFieldDescriptor("[Ljava/lang/String;");
    try validateMethodDescriptor("()V");
    try validateMethodDescriptor("(IDLjava/lang/Thread;)Ljava/lang/Object;");
}
