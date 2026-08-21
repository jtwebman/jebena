//! JVM bytecode opcodes and instruction decoding (JVMS 6, 4.7.3). Decodes a
//! Code attribute's byte array into a stream of instructions, handling fixed
//! operand widths plus the three variable-length forms: tableswitch,
//! lookupswitch, and wide. This is the structural layer the interpreter (02)
//! dispatches over; it does no type-checking (that is Pass 3).

const std = @import("std");

pub const Op = enum(u8) {
    nop = 0x00,
    aconst_null = 0x01,
    iconst_m1 = 0x02,
    iconst_0 = 0x03,
    iconst_1 = 0x04,
    iconst_2 = 0x05,
    iconst_3 = 0x06,
    iconst_4 = 0x07,
    iconst_5 = 0x08,
    lconst_0 = 0x09,
    lconst_1 = 0x0a,
    fconst_0 = 0x0b,
    fconst_1 = 0x0c,
    fconst_2 = 0x0d,
    dconst_0 = 0x0e,
    dconst_1 = 0x0f,
    bipush = 0x10,
    sipush = 0x11,
    ldc = 0x12,
    ldc_w = 0x13,
    ldc2_w = 0x14,
    iload = 0x15,
    lload = 0x16,
    fload = 0x17,
    dload = 0x18,
    aload = 0x19,
    iload_0 = 0x1a,
    iload_1 = 0x1b,
    iload_2 = 0x1c,
    iload_3 = 0x1d,
    lload_0 = 0x1e,
    lload_1 = 0x1f,
    lload_2 = 0x20,
    lload_3 = 0x21,
    fload_0 = 0x22,
    fload_1 = 0x23,
    fload_2 = 0x24,
    fload_3 = 0x25,
    dload_0 = 0x26,
    dload_1 = 0x27,
    dload_2 = 0x28,
    dload_3 = 0x29,
    aload_0 = 0x2a,
    aload_1 = 0x2b,
    aload_2 = 0x2c,
    aload_3 = 0x2d,
    iaload = 0x2e,
    laload = 0x2f,
    faload = 0x30,
    daload = 0x31,
    aaload = 0x32,
    baload = 0x33,
    caload = 0x34,
    saload = 0x35,
    istore = 0x36,
    lstore = 0x37,
    fstore = 0x38,
    dstore = 0x39,
    astore = 0x3a,
    istore_0 = 0x3b,
    istore_1 = 0x3c,
    istore_2 = 0x3d,
    istore_3 = 0x3e,
    lstore_0 = 0x3f,
    lstore_1 = 0x40,
    lstore_2 = 0x41,
    lstore_3 = 0x42,
    fstore_0 = 0x43,
    fstore_1 = 0x44,
    fstore_2 = 0x45,
    fstore_3 = 0x46,
    dstore_0 = 0x47,
    dstore_1 = 0x48,
    dstore_2 = 0x49,
    dstore_3 = 0x4a,
    astore_0 = 0x4b,
    astore_1 = 0x4c,
    astore_2 = 0x4d,
    astore_3 = 0x4e,
    iastore = 0x4f,
    lastore = 0x50,
    fastore = 0x51,
    dastore = 0x52,
    aastore = 0x53,
    bastore = 0x54,
    castore = 0x55,
    sastore = 0x56,
    pop = 0x57,
    pop2 = 0x58,
    dup = 0x59,
    dup_x1 = 0x5a,
    dup_x2 = 0x5b,
    dup2 = 0x5c,
    dup2_x1 = 0x5d,
    dup2_x2 = 0x5e,
    swap = 0x5f,
    iadd = 0x60,
    ladd = 0x61,
    fadd = 0x62,
    dadd = 0x63,
    isub = 0x64,
    lsub = 0x65,
    fsub = 0x66,
    dsub = 0x67,
    imul = 0x68,
    lmul = 0x69,
    fmul = 0x6a,
    dmul = 0x6b,
    idiv = 0x6c,
    ldiv = 0x6d,
    fdiv = 0x6e,
    ddiv = 0x6f,
    irem = 0x70,
    lrem = 0x71,
    frem = 0x72,
    drem = 0x73,
    ineg = 0x74,
    lneg = 0x75,
    fneg = 0x76,
    dneg = 0x77,
    ishl = 0x78,
    lshl = 0x79,
    ishr = 0x7a,
    lshr = 0x7b,
    iushr = 0x7c,
    lushr = 0x7d,
    iand = 0x7e,
    land = 0x7f,
    ior = 0x80,
    lor = 0x81,
    ixor = 0x82,
    lxor = 0x83,
    iinc = 0x84,
    i2l = 0x85,
    i2f = 0x86,
    i2d = 0x87,
    l2i = 0x88,
    l2f = 0x89,
    l2d = 0x8a,
    f2i = 0x8b,
    f2l = 0x8c,
    f2d = 0x8d,
    d2i = 0x8e,
    d2l = 0x8f,
    d2f = 0x90,
    i2b = 0x91,
    i2c = 0x92,
    i2s = 0x93,
    lcmp = 0x94,
    fcmpl = 0x95,
    fcmpg = 0x96,
    dcmpl = 0x97,
    dcmpg = 0x98,
    ifeq = 0x99,
    ifne = 0x9a,
    iflt = 0x9b,
    ifge = 0x9c,
    ifgt = 0x9d,
    ifle = 0x9e,
    if_icmpeq = 0x9f,
    if_icmpne = 0xa0,
    if_icmplt = 0xa1,
    if_icmpge = 0xa2,
    if_icmpgt = 0xa3,
    if_icmple = 0xa4,
    if_acmpeq = 0xa5,
    if_acmpne = 0xa6,
    goto = 0xa7,
    jsr = 0xa8,
    ret = 0xa9,
    tableswitch = 0xaa,
    lookupswitch = 0xab,
    ireturn = 0xac,
    lreturn = 0xad,
    freturn = 0xae,
    dreturn = 0xaf,
    areturn = 0xb0,
    @"return" = 0xb1,
    getstatic = 0xb2,
    putstatic = 0xb3,
    getfield = 0xb4,
    putfield = 0xb5,
    invokevirtual = 0xb6,
    invokespecial = 0xb7,
    invokestatic = 0xb8,
    invokeinterface = 0xb9,
    invokedynamic = 0xba,
    new = 0xbb,
    newarray = 0xbc,
    anewarray = 0xbd,
    arraylength = 0xbe,
    athrow = 0xbf,
    checkcast = 0xc0,
    instanceof = 0xc1,
    monitorenter = 0xc2,
    monitorexit = 0xc3,
    wide = 0xc4,
    multianewarray = 0xc5,
    ifnull = 0xc6,
    ifnonnull = 0xc7,
    goto_w = 0xc8,
    jsr_w = 0xc9,
};

pub const DecodeError = error{ BadOpcode, Truncated, BadSwitch };

pub const Instruction = struct {
    pc: usize,
    op: Op,
    /// Operand bytes (excludes the opcode). For switch/wide this is the full
    /// variable operand region including any alignment padding.
    operands: []const u8,
    /// Total instruction length in bytes (opcode + operands).
    len: usize,
};

fn i32At(code: []const u8, off: usize) DecodeError!i32 {
    if (off + 4 > code.len) return error.Truncated;
    return std.mem.readInt(i32, code[off..][0..4], .big);
}

/// Total length (opcode + operands) of the instruction at `pc`.
pub fn instructionLen(code: []const u8, pc: usize) DecodeError!usize {
    if (pc >= code.len) return error.Truncated;
    const op = std.enums.fromInt(Op, code[pc]) orelse return error.BadOpcode;
    const operand_bytes: usize = switch (op) {
        .bipush, .ldc, .iload, .lload, .fload, .dload, .aload, .istore, .lstore, .fstore, .dstore, .astore, .ret, .newarray => 1,
        .sipush, .ldc_w, .ldc2_w, .iinc, .ifeq, .ifne, .iflt, .ifge, .ifgt, .ifle, .if_icmpeq, .if_icmpne, .if_icmplt, .if_icmpge, .if_icmpgt, .if_icmple, .if_acmpeq, .if_acmpne, .goto, .jsr, .getstatic, .putstatic, .getfield, .putfield, .invokevirtual, .invokespecial, .invokestatic, .new, .anewarray, .checkcast, .instanceof, .ifnull, .ifnonnull => 2,
        .multianewarray => 3,
        .invokeinterface, .invokedynamic, .goto_w, .jsr_w => 4,
        .wide => return wideLen(code, pc),
        .tableswitch => return tableswitchLen(code, pc),
        .lookupswitch => return lookupswitchLen(code, pc),
        else => 0,
    };
    const total = 1 + operand_bytes;
    if (pc + total > code.len) return error.Truncated;
    return total;
}

fn wideLen(code: []const u8, pc: usize) DecodeError!usize {
    if (pc + 1 >= code.len) return error.Truncated;
    const w = std.enums.fromInt(Op, code[pc + 1]) orelse return error.BadOpcode;
    const total: usize = switch (w) {
        .iinc => 6, // wide + iinc + index(2) + const(2)
        .iload, .lload, .fload, .dload, .aload, .istore, .lstore, .fstore, .dstore, .astore, .ret => 4, // wide + op + index(2)
        else => return error.BadOpcode,
    };
    if (pc + total > code.len) return error.Truncated;
    return total;
}

fn padding(pc: usize) usize {
    // Operands begin after the opcode, aligned up to a 4-byte boundary.
    const after = pc + 1;
    return (4 - (after % 4)) % 4;
}

fn tableswitchLen(code: []const u8, pc: usize) DecodeError!usize {
    const p = pc + 1 + padding(pc);
    const low = try i32At(code, p + 4);
    const high = try i32At(code, p + 8);
    if (high < low) return error.BadSwitch;
    const n: usize = @intCast(@as(i64, high) - @as(i64, low) + 1);
    const total = (p + 12 + n * 4) - pc;
    if (pc + total > code.len) return error.Truncated;
    return total;
}

fn lookupswitchLen(code: []const u8, pc: usize) DecodeError!usize {
    const p = pc + 1 + padding(pc);
    const npairs = try i32At(code, p + 4);
    if (npairs < 0) return error.BadSwitch;
    const n: usize = @intCast(npairs);
    const total = (p + 8 + n * 8) - pc;
    if (pc + total > code.len) return error.Truncated;
    return total;
}

/// Decode the instruction at `pc`.
pub fn decodeAt(code: []const u8, pc: usize) DecodeError!Instruction {
    const len = try instructionLen(code, pc);
    const op = std.enums.fromInt(Op, code[pc]).?;
    return .{ .pc = pc, .op = op, .operands = code[pc + 1 .. pc + len], .len = len };
}

pub const Iterator = struct {
    code: []const u8,
    pc: usize = 0,

    pub fn next(self: *Iterator) DecodeError!?Instruction {
        if (self.pc >= self.code.len) return null;
        const insn = try decodeAt(self.code, self.pc);
        self.pc += insn.len;
        return insn;
    }
};

pub fn iterate(code: []const u8) Iterator {
    return .{ .code = code };
}

/// Structural bytecode validation: every instruction decodes and fits. Does not
/// type-check (Pass 3). Returns the number of instructions.
pub fn validate(code: []const u8) DecodeError!usize {
    var it = iterate(code);
    var count: usize = 0;
    while (try it.next()) |_| count += 1;
    return count;
}

const testing = std.testing;

test "decode branch() bytecode: iload_1, ifle, iconst_1, ireturn, iconst_2, ireturn" {
    // 0: iload_1(1b) 1: ifle 6 (9e 00 05) 4: iconst_1(04) 5: ireturn(ac)
    // 6: iconst_2(05) 7: ireturn(ac)
    const code = [_]u8{ 0x1b, 0x9e, 0x00, 0x05, 0x04, 0xac, 0x05, 0xac };
    var it = iterate(&code);
    var ops: std.ArrayList(Op) = .empty;
    defer ops.deinit(testing.allocator);
    while (try it.next()) |insn| try ops.append(testing.allocator, insn.op);
    try testing.expectEqualSlices(Op, &.{ .iload_1, .ifle, .iconst_1, .ireturn, .iconst_2, .ireturn }, ops.items);
}

test "decode branch() from the real Sample.class Code attribute" {
    const cf_mod = @import("class_file.zig");
    const ad = @import("attribute_decode.zig");
    const bytes = @embedFile("testdata/Sample.class");
    var cf = try cf_mod.ClassFile.parse(testing.allocator, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();
    for (cf.methods) |m| {
        if (std.mem.eql(u8, try cf.constant_pool.utf8(m.name_index), "branch")) {
            for (m.attributes) |ai| {
                if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "Code")) {
                    const code = (try ad.decode(arena.allocator(), cf.constant_pool, ai)).code.code;
                    const count = try validate(code);
                    try testing.expectEqual(@as(usize, 6), count);
                    const first = try decodeAt(code, 0);
                    try testing.expectEqual(Op.iload_1, first.op);
                }
            }
        }
    }
}

test "fixed operand lengths" {
    try testing.expectEqual(@as(usize, 1), try instructionLen(&.{0x00}, 0)); // nop
    try testing.expectEqual(@as(usize, 2), try instructionLen(&.{ 0x10, 0x05 }, 0)); // bipush
    try testing.expectEqual(@as(usize, 3), try instructionLen(&.{ 0x11, 0, 0 }, 0)); // sipush
    try testing.expectEqual(@as(usize, 3), try instructionLen(&.{ 0x84, 0, 0 }, 0)); // iinc
    try testing.expectEqual(@as(usize, 5), try instructionLen(&.{ 0xb9, 0, 0, 0, 0 }, 0)); // invokeinterface
    try testing.expectEqual(@as(usize, 4), try instructionLen(&.{ 0xc5, 0, 0, 0 }, 0)); // multianewarray
    try testing.expectEqual(@as(usize, 5), try instructionLen(&.{ 0xc8, 0, 0, 0, 0 }, 0)); // goto_w
}

test "wide forms" {
    // wide iload: c4 15 00 01
    try testing.expectEqual(@as(usize, 4), try instructionLen(&.{ 0xc4, 0x15, 0, 1 }, 0));
    // wide iinc: c4 84 00 01 00 02
    try testing.expectEqual(@as(usize, 6), try instructionLen(&.{ 0xc4, 0x84, 0, 1, 0, 2 }, 0));
    // wide with an illegal target op
    try testing.expectError(error.BadOpcode, instructionLen(&.{ 0xc4, 0x00, 0, 0 }, 0));
}

test "tableswitch length with padding" {
    // pc=0 -> padding 3. default(4) low=0(4) high=1(4) -> 2 offsets(8). len=1+3+12+8=24
    var code = [_]u8{0} ** 24;
    code[0] = 0xaa;
    code[1 + 3 + 8 - 1] = 0; // low bytes already 0
    code[1 + 3 + 11] = 1; // high = 1 (low 4 bytes at offset p+8; last byte)
    const len = try instructionLen(&code, 0);
    try testing.expectEqual(@as(usize, 24), len);
}

test "lookupswitch length with padding" {
    // pc=0 -> padding 3. default(4) npairs=1(4) -> 1 pair(8). len=1+3+8+8=20
    var code = [_]u8{0} ** 20;
    code[0] = 0xab;
    code[1 + 3 + 7] = 1; // npairs = 1 (last byte of the npairs u32 at p+4)
    const len = try instructionLen(&code, 0);
    try testing.expectEqual(@as(usize, 20), len);
}

test "tableswitch with high < low is rejected" {
    var code = [_]u8{0} ** 24;
    code[0] = 0xaa;
    // low = 5, high = 0
    code[1 + 3 + 7] = 5; // low last byte
    // high stays 0
    try testing.expectError(error.BadSwitch, instructionLen(&code, 0));
}

test "bad opcode is rejected" {
    try testing.expectError(error.BadOpcode, instructionLen(&.{0xca}, 0)); // breakpoint: not valid in a class file
    try testing.expectError(error.BadOpcode, instructionLen(&.{0xff}, 0));
}

test "truncated operand is rejected" {
    try testing.expectError(error.Truncated, instructionLen(&.{0x10}, 0)); // bipush with no operand
    try testing.expectError(error.Truncated, instructionLen(&.{ 0xb6, 0x00 }, 0)); // invokevirtual missing a byte
}
