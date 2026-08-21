//! A bytecode interpreter for the integer / control-flow subset (no heap, no
//! method calls yet). Enough to execute pure-int javac methods (loops,
//! arithmetic, branches). Dispatch uses Zig's labeled switch/continue — the
//! computed-goto form chosen in docs/research/02-interpreter.md.
//!
//! Bytecode is assumed structurally valid (see bytecode.validate). The loop is
//! still defensive: stack/local bounds and type tags are checked, so malformed
//! input yields an error rather than a crash.

const std = @import("std");
const bc = @import("bytecode.zig");
const Op = bc.Op;

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
    Truncated,
} || bc.DecodeError || std.mem.Allocator.Error;

const Frame = struct {
    stack: []Value,
    locals: []Value,
    sp: usize = 0,
    pc: usize = 0,
    steps: usize = 0,
    budget: usize = std.math.maxInt(usize),

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
    f.steps += 1;
    if (f.steps > f.budget) return error.StepLimitExceeded;
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

fn branch(pc: usize, offset: i32, code_len: usize) RunError!usize {
    const target = @as(i64, @intCast(pc)) + offset;
    if (target < 0 or target >= code_len) return error.BadBranch;
    return @intCast(target);
}

/// Run a static int-only method. `args` are placed in locals[0..]. Returns the
/// ireturn value, or null for a void return.
pub const default_budget: usize = 100_000_000;

/// Convenience wrapper with a generous default instruction budget.
pub fn runInt(a: std.mem.Allocator, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32) RunError!?i32 {
    return runIntBudgeted(a, code, max_stack, max_locals, args, default_budget);
}

/// Run a static int-only method with an explicit instruction budget (guarantees
/// termination: exceeding it returns error.StepLimitExceeded).
pub fn runIntBudgeted(a: std.mem.Allocator, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32, budget: usize) RunError!?i32 {
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
        .ireturn => return try f.popInt(),
        .@"return" => return null,
        else => return error.UnsupportedOpcode,
    }
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
    // iconst_2, iconst_3, iadd, iconst_4, imul, ireturn
    const code = [_]u8{ 0x05, 0x06, 0x60, 0x07, 0x68, 0xac };
    const r = try runInt(testing.allocator, &code, 2, 0, &.{});
    try testing.expectEqual(@as(?i32, 20), r);
}

test "division by zero traps" {
    // iconst_1, iconst_0, idiv, ireturn
    const code = [_]u8{ 0x04, 0x03, 0x6c, 0xac };
    try testing.expectError(error.ArithmeticException, runInt(testing.allocator, &code, 2, 0, &.{}));
}

test "stack underflow is caught, not a crash" {
    const code = [_]u8{ 0x60, 0xac }; // iadd with empty stack
    try testing.expectError(error.StackUnderflow, runInt(testing.allocator, &code, 2, 0, &.{}));
}

fn runMethod(name: []const u8, arg: i32) !?i32 {
    const cf_mod = @import("class_file.zig");
    const ad = @import("attribute_decode.zig");
    const bytes = @embedFile("testdata/Compute.class");
    var cf = try cf_mod.ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    for (cf.methods) |m| {
        if (std.mem.eql(u8, try cf.constant_pool.utf8(m.name_index), name)) {
            for (m.attributes) |ai| {
                if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "Code")) {
                    const c = (try ad.decode(arena.allocator(), cf.constant_pool, ai)).code;
                    return try runInt(testing.allocator, c.code, c.max_stack, c.max_locals, &.{arg});
                }
            }
        }
    }
    return error.MethodNotFound;
}

test "executes real javac bytecode: sumTo(10) == 55" {
    try testing.expectEqual(@as(?i32, 55), try runMethod("sumTo", 10));
}

test "executes real javac bytecode: poly(3) == 28" {
    // 3*3*3 - 2*3 + 7 = 27 - 6 + 7 = 28
    try testing.expectEqual(@as(?i32, 28), try runMethod("poly", 3));
}

test "executes real javac bytecode: fact(5) == 120" {
    try testing.expectEqual(@as(?i32, 120), try runMethod("fact", 5));
}

test "executes real javac bytecode: bits(5)" {
    // ((5<<2)|1) ^ (5&3) = (20|1) ^ 1 = 21 ^ 1 = 20
    try testing.expectEqual(@as(?i32, 20), try runMethod("bits", 5));
}

test "sumTo matches the closed form for many inputs" {
    var n: i32 = 0;
    while (n <= 200) : (n += 1) {
        const expected: i32 = @divTrunc(n * (n + 1), 2);
        try testing.expectEqual(@as(?i32, expected), try runMethod("sumTo", n));
    }
}

test "an infinite loop hits the step budget instead of hanging" {
    const code = [_]u8{ 0xa7, 0x00, 0x00 }; // goto 0 (jumps to itself)
    try testing.expectError(error.StepLimitExceeded, runIntBudgeted(testing.allocator, &code, 0, 0, &.{}, 1000));
}
