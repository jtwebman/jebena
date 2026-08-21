//! A bytecode interpreter for the integer / control-flow subset, now with
//! static method calls (invokestatic) and therefore recursion. Still no heap,
//! objects, or category-2 (long/double) values. Dispatch uses Zig's labeled
//! switch/continue (the computed-goto form from docs/research/02-interpreter.md).
//!
//! Bytecode is assumed structurally valid; the loop stays defensive (bounds,
//! type tags, a shared instruction budget, and a call-depth limit), so bad
//! input errors rather than crashes or hangs.

const std = @import("std");
const bc = @import("bytecode.zig");
const Op = bc.Op;
const class_file = @import("class_file.zig");
const ClassFile = class_file.ClassFile;
const constant_pool = @import("constant_pool.zig");
const ConstantPool = constant_pool.ConstantPool;
const attribute_decode = @import("attribute_decode.zig");
const descriptor = @import("descriptor.zig");

pub const Value = union(enum) {
    int: i32,
    long: i64,
    float: f32,
    double: f64,
};

pub const RunError = error{
    StackOverflow,
    StackUnderflow,
    BadLocal,
    BadBranch,
    TypeMismatch,
    UnsupportedOpcode,
    ArithmeticException,
    StepLimitExceeded,
    CallDepthExceeded,
    MethodNotFound,
    LinkError,
    Truncated,
} || bc.DecodeError || std.mem.Allocator.Error;

/// Shared across a whole call tree: bounds total work and recursion depth.
pub const Budget = struct {
    steps: usize = 0,
    max_steps: usize = 100_000_000,
    depth: usize = 0,
    max_depth: usize = 1024,
};

const Frame = struct {
    stack: []Value,
    locals: []Value,
    sp: usize = 0,
    pc: usize = 0,
    budget: *Budget,

    fn push(f: *Frame, v: Value) RunError!void {
        if (f.sp >= f.stack.len) return error.StackOverflow;
        f.stack[f.sp] = v;
        f.sp += 1;
    }
    fn pop(f: *Frame) RunError!Value {
        if (f.sp == 0) return error.StackUnderflow;
        f.sp -= 1;
        return f.stack[f.sp];
    }
    fn popInt(f: *Frame) RunError!i32 {
        return switch (try f.pop()) {
            .int => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn pushInt(f: *Frame, x: i32) RunError!void {
        return f.push(.{ .int = x });
    }
    fn localInt(f: *Frame, idx: usize) RunError!i32 {
        if (idx >= f.locals.len) return error.BadLocal;
        return switch (f.locals[idx]) {
            .int => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn setLocal(f: *Frame, idx: usize, v: Value) RunError!void {
        if (idx >= f.locals.len) return error.BadLocal;
        f.locals[idx] = v;
    }
};

fn opAt(code: []const u8, pc: usize) RunError!Op {
    if (pc >= code.len) return error.Truncated;
    return std.enums.fromInt(Op, code[pc]) orelse error.BadOpcode;
}

fn step(f: *Frame, code: []const u8) RunError!Op {
    f.budget.steps += 1;
    if (f.budget.steps > f.budget.max_steps) return error.StepLimitExceeded;
    return opAt(code, f.pc);
}

fn s8(code: []const u8, off: usize) RunError!i32 {
    if (off >= code.len) return error.Truncated;
    return @as(i8, @bitCast(code[off]));
}
fn u8At(code: []const u8, off: usize) RunError!usize {
    if (off >= code.len) return error.Truncated;
    return code[off];
}
fn s16(code: []const u8, off: usize) RunError!i32 {
    if (off + 2 > code.len) return error.Truncated;
    return std.mem.readInt(i16, code[off..][0..2], .big);
}
fn u16At(code: []const u8, off: usize) RunError!u16 {
    if (off + 2 > code.len) return error.Truncated;
    return std.mem.readInt(u16, code[off..][0..2], .big);
}

fn branch(pc: usize, offset: i32, code_len: usize) RunError!usize {
    const target = @as(i64, @intCast(pc)) + offset;
    if (target < 0 or target >= code_len) return error.BadBranch;
    return @intCast(target);
}

/// A loaded class: methods pre-resolved with their decoded Code, ready to run.
pub const Class = struct {
    gpa: std.mem.Allocator,
    cp: ConstantPool,
    methods: []Method,

    pub const Method = struct {
        name: []const u8,
        descriptor: []const u8,
        code: ?attribute_decode.CodeAttr,
    };

    /// Build from a parsed class file. `arena` holds the decoded Code; `gpa` is
    /// used for per-call operand stacks at run time.
    pub fn init(gpa: std.mem.Allocator, arena: std.mem.Allocator, cf: *const ClassFile) !Class {
        const methods = try arena.alloc(Method, cf.methods.len);
        for (cf.methods, 0..) |m, i| {
            const name = try cf.constant_pool.utf8(m.name_index);
            const desc = try cf.constant_pool.utf8(m.descriptor_index);
            var code: ?attribute_decode.CodeAttr = null;
            for (m.attributes) |ai| {
                if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "Code")) {
                    code = (try attribute_decode.decode(arena, cf.constant_pool, ai)).code;
                }
            }
            methods[i] = .{ .name = name, .descriptor = desc, .code = code };
        }
        return .{ .gpa = gpa, .cp = cf.constant_pool, .methods = methods };
    }

    fn find(self: *const Class, name: []const u8, desc: []const u8) ?*const Method {
        for (self.methods) |*m| {
            if (std.mem.eql(u8, m.name, name) and std.mem.eql(u8, m.descriptor, desc)) return m;
        }
        return null;
    }

    /// Resolve and run a static int-returning method (all-int parameters).
    pub fn callStaticInt(self: *const Class, name: []const u8, desc: []const u8, args: []const i32, budget: *Budget) RunError!?i32 {
        const m = self.find(name, desc) orelse return error.MethodNotFound;
        const c = m.code orelse return error.LinkError; // native/abstract not supported
        return exec(self.gpa, self, budget, c.code, c.max_stack, c.max_locals, args);
    }

    /// Convenience entry point with a fresh budget.
    pub fn callStatic(self: *const Class, name: []const u8, desc: []const u8, args: []const i32) RunError!?i32 {
        var b = Budget{};
        return self.callStaticInt(name, desc, args, &b);
    }
};

/// The core loop. `class` is optional: without it, invokestatic is unsupported.
fn exec(a: std.mem.Allocator, class: ?*const Class, budget: *Budget, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32) RunError!?i32 {
    if (args.len > max_locals) return error.BadLocal;
    const stack = try a.alloc(Value, max_stack);
    defer a.free(stack);
    const locals = try a.alloc(Value, max_locals);
    defer a.free(locals);
    for (locals) |*l| l.* = .{ .int = 0 };
    for (args, 0..) |arg, i| locals[i] = .{ .int = arg };

    var f = Frame{ .stack = stack, .locals = locals, .budget = budget };

    sw: switch (try opAt(code, f.pc)) {
        .nop => {
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .iconst_m1, .iconst_0, .iconst_1, .iconst_2, .iconst_3, .iconst_4, .iconst_5 => |o| {
            try f.pushInt(@as(i32, @intFromEnum(o)) - @intFromEnum(Op.iconst_0));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .bipush => {
            try f.pushInt(try s8(code, f.pc + 1));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .sipush => {
            try f.pushInt(try s16(code, f.pc + 1));
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .ldc => {
            try loadConstant(&f, class, @intCast(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .ldc_w => {
            try loadConstant(&f, class, try u16At(code, f.pc + 1));
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .iload => {
            try f.pushInt(try f.localInt(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .iload_0, .iload_1, .iload_2, .iload_3 => |o| {
            try f.pushInt(try f.localInt(@intFromEnum(o) - @intFromEnum(Op.iload_0)));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .istore => {
            try f.setLocal(try u8At(code, f.pc + 1), .{ .int = try f.popInt() });
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .istore_0, .istore_1, .istore_2, .istore_3 => |o| {
            try f.setLocal(@intFromEnum(o) - @intFromEnum(Op.istore_0), .{ .int = try f.popInt() });
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .pop => {
            _ = try f.pop();
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dup => {
            const v = try f.pop();
            try f.push(v);
            try f.push(v);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .swap => {
            const b = try f.pop();
            const a2 = try f.pop();
            try f.push(b);
            try f.push(a2);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .iadd, .isub, .imul, .idiv, .irem, .iand, .ior, .ixor, .ishl, .ishr, .iushr => |o| {
            const y = try f.popInt();
            const x = try f.popInt();
            try f.pushInt(try intBinary(o, x, y));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .ineg => {
            try f.pushInt(0 -% try f.popInt());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .i2b => {
            try f.pushInt(@as(i8, @truncate(try f.popInt())));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .i2c => {
            try f.pushInt(@as(u16, @truncate(@as(u32, @bitCast(try f.popInt())))));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .i2s => {
            try f.pushInt(@as(i16, @truncate(try f.popInt())));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .iinc => {
            const idx = try u8At(code, f.pc + 1);
            const c = try s8(code, f.pc + 2);
            try f.setLocal(idx, .{ .int = (try f.localInt(idx)) +% c });
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .ifeq, .ifne, .iflt, .ifge, .ifgt, .ifle => |o| {
            const x = try f.popInt();
            if (compareZero(o, x)) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(&f, code);
        },
        .if_icmpeq, .if_icmpne, .if_icmplt, .if_icmpge, .if_icmpgt, .if_icmple => |o| {
            const y = try f.popInt();
            const x = try f.popInt();
            if (compareInt(o, x, y)) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(&f, code);
        },
        .goto => {
            f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            continue :sw try step(&f, code);
        },
        .invokestatic => {
            const cls = class orelse return error.UnsupportedOpcode;
            try invokeStatic(&f, cls, code);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .ireturn => return try f.popInt(),
        .@"return" => return null,
        else => return error.UnsupportedOpcode,
    }
}

fn loadConstant(f: *Frame, class: ?*const Class, index: u16) RunError!void {
    const cls = class orelse return error.UnsupportedOpcode;
    const c = cls.cp.get(index) catch return error.LinkError;
    switch (c.*) {
        .integer => |v| try f.pushInt(v),
        .float => |v| try f.push(.{ .float = v }),
        else => return error.UnsupportedOpcode, // String/Class/etc. need the heap
    }
}

fn invokeStatic(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const idx = try u16At(code, f.pc + 1);
    const mref = cls.cp.get(idx) catch return error.LinkError;
    const ref = switch (mref.*) {
        .methodref => |r| r,
        else => return error.LinkError,
    };
    const nat_c = cls.cp.get(ref.name_and_type_index) catch return error.LinkError;
    const nat = switch (nat_c.*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const mname = cls.cp.utf8(nat.name_index) catch return error.LinkError;
    const mdesc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;
    const nparams = descriptor.paramCount(mdesc) catch return error.LinkError;
    if (nparams > 255) return error.LinkError;

    var argbuf: [255]i32 = undefined;
    var k: usize = nparams;
    while (k > 0) {
        k -= 1;
        argbuf[k] = try f.popInt();
    }

    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const ret = try cls.callStaticInt(mname, mdesc, argbuf[0..nparams], f.budget);
    if (ret) |rv| try f.pushInt(rv);
}

pub const default_budget: usize = 100_000_000;

/// Run a static int-only method with no class context (invokestatic unsupported).
pub fn runInt(a: std.mem.Allocator, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32) RunError!?i32 {
    return runIntBudgeted(a, code, max_stack, max_locals, args, default_budget);
}

pub fn runIntBudgeted(a: std.mem.Allocator, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32, max_steps: usize) RunError!?i32 {
    var b = Budget{ .max_steps = max_steps };
    return exec(a, null, &b, code, max_stack, max_locals, args);
}

fn intBinary(o: Op, x: i32, y: i32) RunError!i32 {
    return switch (o) {
        .iadd => x +% y,
        .isub => x -% y,
        .imul => x *% y,
        .idiv => blk: {
            if (y == 0) return error.ArithmeticException;
            if (x == std.math.minInt(i32) and y == -1) break :blk std.math.minInt(i32);
            break :blk @divTrunc(x, y);
        },
        .irem => blk: {
            if (y == 0) return error.ArithmeticException;
            if (x == std.math.minInt(i32) and y == -1) break :blk 0;
            break :blk @rem(x, y);
        },
        .iand => x & y,
        .ior => x | y,
        .ixor => x ^ y,
        .ishl => x << @intCast(@as(u32, @bitCast(y)) & 0x1f),
        .ishr => x >> @intCast(@as(u32, @bitCast(y)) & 0x1f),
        .iushr => @bitCast(@as(u32, @bitCast(x)) >> @intCast(@as(u32, @bitCast(y)) & 0x1f)),
        else => error.UnsupportedOpcode,
    };
}

fn compareZero(o: Op, x: i32) bool {
    return switch (o) {
        .ifeq => x == 0,
        .ifne => x != 0,
        .iflt => x < 0,
        .ifge => x >= 0,
        .ifgt => x > 0,
        .ifle => x <= 0,
        else => false,
    };
}

fn compareInt(o: Op, x: i32, y: i32) bool {
    return switch (o) {
        .if_icmpeq => x == y,
        .if_icmpne => x != y,
        .if_icmplt => x < y,
        .if_icmpge => x >= y,
        .if_icmpgt => x > y,
        .if_icmple => x <= y,
        else => false,
    };
}

const testing = std.testing;

test "hand-assembled: (2 + 3) * 4 = 20" {
    const code = [_]u8{ 0x05, 0x06, 0x60, 0x07, 0x68, 0xac };
    try testing.expectEqual(@as(?i32, 20), try runInt(testing.allocator, &code, 2, 0, &.{}));
}

test "division by zero traps" {
    const code = [_]u8{ 0x04, 0x03, 0x6c, 0xac };
    try testing.expectError(error.ArithmeticException, runInt(testing.allocator, &code, 2, 0, &.{}));
}

test "stack underflow is caught, not a crash" {
    const code = [_]u8{ 0x60, 0xac };
    try testing.expectError(error.StackUnderflow, runInt(testing.allocator, &code, 2, 0, &.{}));
}

test "an infinite loop hits the step budget instead of hanging" {
    const code = [_]u8{ 0xa7, 0x00, 0x00 };
    try testing.expectError(error.StepLimitExceeded, runIntBudgeted(testing.allocator, &code, 0, 0, &.{}, 1000));
}

// --- run whole methods from a real class ---------------------------------

fn runComputeMethod(name: []const u8, arg: i32) !?i32 {
    const bytes = @embedFile("testdata/Compute.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const cls = try Class.init(testing.allocator, arena.allocator(), &cf);
    return cls.callStatic(name, "(I)I", &.{arg});
}

test "executes real javac bytecode: sumTo/poly/fact/bits" {
    try testing.expectEqual(@as(?i32, 55), try runComputeMethod("sumTo", 10));
    try testing.expectEqual(@as(?i32, 28), try runComputeMethod("poly", 3));
    try testing.expectEqual(@as(?i32, 120), try runComputeMethod("fact", 5));
    try testing.expectEqual(@as(?i32, 20), try runComputeMethod("bits", 5));
}

test "sumTo matches the closed form for many inputs" {
    var n: i32 = 0;
    while (n <= 200) : (n += 1) {
        try testing.expectEqual(@as(?i32, @divTrunc(n * (n + 1), 2)), try runComputeMethod("sumTo", n));
    }
}

// --- recursion via invokestatic ------------------------------------------

fn loadRecur(cf: *ClassFile, arena: *std.heap.ArenaAllocator) !Class {
    return Class.init(testing.allocator, arena.allocator(), cf);
}

test "recursion: fib via invokestatic" {
    const bytes = @embedFile("testdata/Recur.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const cls = try loadRecur(&cf, &arena);

    const expected = [_]i32{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144 };
    for (expected, 0..) |want, n| {
        try testing.expectEqual(@as(?i32, want), try cls.callStatic("fib", "(I)I", &.{@intCast(n)}));
    }
}

test "recursion: gcd via invokestatic" {
    const bytes = @embedFile("testdata/Recur.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const cls = try Class.init(testing.allocator, arena.allocator(), &cf);
    try testing.expectEqual(@as(?i32, 12), try cls.callStatic("gcd", "(II)I", &.{ 48, 36 }));
    try testing.expectEqual(@as(?i32, 7), try cls.callStatic("gcd", "(II)I", &.{ 14, 21 }));
    try testing.expectEqual(@as(?i32, 1), try cls.callStatic("gcd", "(II)I", &.{ 17, 5 }));
}

test "non-recursive static call: addOne -> inc" {
    const bytes = @embedFile("testdata/Recur.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const cls = try Class.init(testing.allocator, arena.allocator(), &cf);
    try testing.expectEqual(@as(?i32, 42), try cls.callStatic("addOne", "(I)I", &.{41}));
}

test "runaway recursion hits the depth limit, not a native stack overflow" {
    // Craft a class-free scenario is hard; use Recur.fib with a tiny depth cap.
    const bytes = @embedFile("testdata/Recur.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const cls = try Class.init(testing.allocator, arena.allocator(), &cf);
    var b = Budget{ .max_depth = 4 };
    try testing.expectError(error.CallDepthExceeded, cls.callStaticInt("fib", "(I)I", &.{20}, &b));
}

test "ldc: big(x) = 1000000 + x via a pooled constant" {
    const bytes = @embedFile("testdata/Compute.class");
    var cf = try ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    const cls = try Class.init(testing.allocator, arena.allocator(), &cf);
    try testing.expectEqual(@as(?i32, 1000042), try cls.callStatic("big", "(I)I", &.{42}));
}
