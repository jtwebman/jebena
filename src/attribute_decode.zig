//! Typed decoders for the attributes we care about first (JVMS 4.7). Unknown
//! attributes are preserved as `.unknown` (name + raw bytes). Driven by the
//! generic AttributeInfo envelope from attribute.zig.

const std = @import("std");
const Allocator = std.mem.Allocator;
const Reader = @import("reader.zig").Reader;
const constant_pool = @import("constant_pool.zig");
const ConstantPool = constant_pool.ConstantPool;
const AttributeInfo = @import("attribute.zig").AttributeInfo;

pub const ExceptionTableEntry = struct {
    start_pc: u16,
    end_pc: u16,
    handler_pc: u16,
    catch_type: u16, // 0 means "any" (finally)
};

pub const LineNumberEntry = struct {
    start_pc: u16,
    line_number: u16,
};

pub const BootstrapMethod = struct {
    bootstrap_method_ref: u16,
    arguments: []const u16,
};

pub const VerificationType = union(enum) {
    top,
    integer,
    float,
    long,
    double,
    null_,
    uninitialized_this,
    object: u16, // constant pool index
    uninitialized: u16, // bytecode offset
};

pub const StackMapFrame = struct {
    frame_type: u8,
    offset_delta: u16,
    kind: union(enum) {
        same,
        same_locals_1_stack_item: VerificationType,
        chop: u8, // number of locals chopped (1..3)
        same_extended,
        append: []const VerificationType, // 1..3 locals
        full: struct {
            locals: []const VerificationType,
            stack: []const VerificationType,
        },
    },
};

pub const CodeAttr = struct {
    max_stack: u16,
    max_locals: u16,
    code: []const u8,
    exception_table: []const ExceptionTableEntry,
    attributes: []const Attribute,
};

pub const Attribute = union(enum) {
    constant_value: u16, // constantvalue_index
    code: CodeAttr,
    exceptions: []const u16, // exception_index_table
    source_file: u16, // sourcefile_index
    line_number_table: []const LineNumberEntry,
    stack_map_table: []const StackMapFrame,
    bootstrap_methods: []const BootstrapMethod,
    unknown: struct { name: []const u8, info: []const u8 },
};

pub const Error = error{ Truncated, BadAttribute } || ConstantPool.Error || Allocator.Error;

fn readVerificationType(r: *Reader) Error!VerificationType {
    const tag = try r.readU8();
    return switch (tag) {
        0 => .top,
        1 => .integer,
        2 => .float,
        3 => .double,
        4 => .long,
        5 => .null_,
        6 => .uninitialized_this,
        7 => .{ .object = try r.readU16() },
        8 => .{ .uninitialized = try r.readU16() },
        else => error.BadAttribute,
    };
}

fn readVerificationTypes(a: Allocator, r: *Reader, n: usize) Error![]VerificationType {
    const items = try a.alloc(VerificationType, n);
    for (items) |*it| it.* = try readVerificationType(r);
    return items;
}

fn decodeStackMapFrame(a: Allocator, r: *Reader) Error!StackMapFrame {
    const ft = try r.readU8();
    if (ft <= 63) {
        return .{ .frame_type = ft, .offset_delta = ft, .kind = .same };
    } else if (ft <= 127) {
        return .{ .frame_type = ft, .offset_delta = ft - 64, .kind = .{ .same_locals_1_stack_item = try readVerificationType(r) } };
    } else if (ft <= 246) {
        return error.BadAttribute; // 128..246 reserved
    } else if (ft == 247) {
        const delta = try r.readU16();
        return .{ .frame_type = ft, .offset_delta = delta, .kind = .{ .same_locals_1_stack_item = try readVerificationType(r) } };
    } else if (ft <= 250) {
        // 248..250 chop_frame
        return .{ .frame_type = ft, .offset_delta = try r.readU16(), .kind = .{ .chop = 251 - ft } };
    } else if (ft == 251) {
        return .{ .frame_type = ft, .offset_delta = try r.readU16(), .kind = .same_extended };
    } else if (ft <= 254) {
        // 252..254 append_frame
        const delta = try r.readU16();
        const k: usize = ft - 251;
        return .{ .frame_type = ft, .offset_delta = delta, .kind = .{ .append = try readVerificationTypes(a, r, k) } };
    } else {
        // 255 full_frame
        const delta = try r.readU16();
        const num_locals = try r.readU16();
        const locals = try readVerificationTypes(a, r, num_locals);
        const num_stack = try r.readU16();
        const stack = try readVerificationTypes(a, r, num_stack);
        return .{ .frame_type = ft, .offset_delta = delta, .kind = .{ .full = .{ .locals = locals, .stack = stack } } };
    }
}

/// Decode one attribute. `cp` resolves the attribute name.
pub fn decode(a: Allocator, cp: ConstantPool, attr: AttributeInfo) Error!Attribute {
    const name = try cp.utf8(attr.name_index);
    var r = Reader.init(attr.info);

    if (std.mem.eql(u8, name, "ConstantValue")) {
        return .{ .constant_value = try r.readU16() };
    } else if (std.mem.eql(u8, name, "Code")) {
        const max_stack = try r.readU16();
        const max_locals = try r.readU16();
        const code_len = try r.readU32();
        const code = try a.dupe(u8, try r.take(code_len));
        const etl = try r.readU16();
        const et = try a.alloc(ExceptionTableEntry, etl);
        for (et) |*e| e.* = .{
            .start_pc = try r.readU16(),
            .end_pc = try r.readU16(),
            .handler_pc = try r.readU16(),
            .catch_type = try r.readU16(),
        };
        const ac = try r.readU16();
        const nested = try a.alloc(Attribute, ac);
        for (nested) |*na| {
            const ni = try r.readU16();
            const len = try r.readU32();
            const info = try a.dupe(u8, try r.take(len));
            na.* = try decode(a, cp, .{ .name_index = ni, .info = info });
        }
        return .{ .code = .{ .max_stack = max_stack, .max_locals = max_locals, .code = code, .exception_table = et, .attributes = nested } };
    } else if (std.mem.eql(u8, name, "Exceptions")) {
        const n = try r.readU16();
        const idx = try a.alloc(u16, n);
        for (idx) |*x| x.* = try r.readU16();
        return .{ .exceptions = idx };
    } else if (std.mem.eql(u8, name, "SourceFile")) {
        return .{ .source_file = try r.readU16() };
    } else if (std.mem.eql(u8, name, "LineNumberTable")) {
        const n = try r.readU16();
        const entries = try a.alloc(LineNumberEntry, n);
        for (entries) |*e| e.* = .{ .start_pc = try r.readU16(), .line_number = try r.readU16() };
        return .{ .line_number_table = entries };
    } else if (std.mem.eql(u8, name, "StackMapTable")) {
        const n = try r.readU16();
        const frames = try a.alloc(StackMapFrame, n);
        for (frames) |*f| f.* = try decodeStackMapFrame(a, &r);
        return .{ .stack_map_table = frames };
    } else if (std.mem.eql(u8, name, "BootstrapMethods")) {
        const n = try r.readU16();
        const methods = try a.alloc(BootstrapMethod, n);
        for (methods) |*m| {
            const ref = try r.readU16();
            const na = try r.readU16();
            const args = try a.alloc(u16, na);
            for (args) |*x| x.* = try r.readU16();
            m.* = .{ .bootstrap_method_ref = ref, .arguments = args };
        }
        return .{ .bootstrap_methods = methods };
    } else {
        return .{ .unknown = .{ .name = name, .info = attr.info } };
    }
}

const testing = std.testing;
const ClassFile = @import("class_file.zig").ClassFile;

fn findAttr(a: Allocator, cp: ConstantPool, attrs: []const AttributeInfo, name: []const u8) !?Attribute {
    for (attrs) |ai| {
        if (std.mem.eql(u8, try cp.utf8(ai.name_index), name)) return try decode(a, cp, ai);
    }
    return null;
}

test "decode attributes of a real class (Sample.class)" {
    const bytes = @embedFile("testdata/Sample.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const a = arena.allocator();
    const cp = cf.constant_pool;

    // Class-level SourceFile -> "Sample.java"
    const sf = (try findAttr(a, cp, cf.attributes, "SourceFile")).?;
    try testing.expectEqualStrings("Sample.java", try cp.utf8(sf.source_file));

    // Class-level BootstrapMethods: 1 method, 3 arguments (the lambda)
    const bm = (try findAttr(a, cp, cf.attributes, "BootstrapMethods")).?;
    try testing.expectEqual(@as(usize, 1), bm.bootstrap_methods.len);
    try testing.expectEqual(@as(usize, 3), bm.bootstrap_methods[0].arguments.len);

    // Field K has ConstantValue -> Integer 7
    var found_k = false;
    for (cf.fields) |f| {
        if (std.mem.eql(u8, try cp.utf8(f.name_index), "K")) {
            const cv = (try findAttr(a, cp, f.attributes, "ConstantValue")).?;
            try testing.expectEqual(@as(i32, 7), (try cp.get(cv.constant_value)).integer);
            found_k = true;
        }
    }
    try testing.expect(found_k);

    // Method branch(int): Code stack=1 locals=2, code_length=8, StackMapTable 1 same-frame
    // Method mayThrow(): Exceptions -> java/io/IOException
    var found_branch = false;
    var found_throws = false;
    for (cf.methods) |m| {
        const mname = try cp.utf8(m.name_index);
        if (std.mem.eql(u8, mname, "branch")) {
            const code = (try findAttr(a, cp, m.attributes, "Code")).?.code;
            try testing.expectEqual(@as(u16, 1), code.max_stack);
            try testing.expectEqual(@as(u16, 2), code.max_locals);
            try testing.expectEqual(@as(usize, 8), code.code.len);
            var smt: ?Attribute = null;
            for (code.attributes) |na| {
                if (na == .stack_map_table) smt = na;
            }
            try testing.expectEqual(@as(usize, 1), smt.?.stack_map_table.len);
            try testing.expect(smt.?.stack_map_table[0].kind == .same);
            found_branch = true;
        } else if (std.mem.eql(u8, mname, "mayThrow")) {
            const exc = (try findAttr(a, cp, m.attributes, "Exceptions")).?;
            try testing.expectEqual(@as(usize, 1), exc.exceptions.len);
            try testing.expectEqualStrings("java/io/IOException", try cp.classNameOf(exc.exceptions[0]));
            found_throws = true;
        }
    }
    try testing.expect(found_branch and found_throws);
}

test "truncated Code attribute is rejected" {
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const a = arena.allocator();
    // constant pool: #1 Utf8 "Code"
    const entries = [_]constant_pool.Constant{ .unusable, .{ .utf8 = "Code" } };
    const cp = ConstantPool{ .entries = &entries };
    // Code attr claiming code_length huge but no bytes
    const info = [_]u8{ 0, 1, 0, 1, 0x00, 0x00, 0x10, 0x00 }; // max_stack=1 max_locals=1 code_len=4096
    try testing.expectError(error.Truncated, decode(a, cp, .{ .name_index = 1, .info = &info }));
}

test "unknown attribute is preserved" {
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const a = arena.allocator();
    const entries = [_]constant_pool.Constant{ .unusable, .{ .utf8 = "Synthetic" } };
    const cp = ConstantPool{ .entries = &entries };
    const info = [_]u8{ 0xAA, 0xBB };
    const attr = try decode(a, cp, .{ .name_index = 1, .info = &info });
    try testing.expectEqualStrings("Synthetic", attr.unknown.name);
    try testing.expectEqualSlices(u8, &.{ 0xAA, 0xBB }, attr.unknown.info);
}
