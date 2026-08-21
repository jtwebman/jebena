//! Bytecode interpreter for the full numeric tower (int, long, float, double)
//! plus control flow and static method calls. Category-2 values (long, double)
//! occupy two slots on the operand stack and in locals, matching the JVM; the
//! second slot holds a `.top` marker. No heap/objects yet.
//!
//! Dispatch uses Zig's labeled switch/continue (docs/research/02-interpreter.md).
//! Bytecode is assumed structurally valid; the loop stays defensive (bounds,
//! type tags, a shared step budget, a call-depth limit).

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
    /// Object reference: heap object id, or null.
    reference: ?u32,
    /// Reserved upper half of a category-2 value (long/double).
    top,
};

pub const Kind = enum { int, long, float, double, reference };

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
    NullPointer,
    Truncated,
} || bc.DecodeError || std.mem.Allocator.Error;

pub const Budget = struct {
    steps: usize = 0,
    max_steps: usize = 100_000_000,
    depth: usize = 0,
    max_depth: usize = 1024,
};

fn isTop(v: Value) bool {
    return switch (v) {
        .top => true,
        else => false,
    };
}

const Frame = struct {
    stack: []Value,
    locals: []Value,
    sp: usize = 0,
    pc: usize = 0,
    budget: *Budget,
    heap: ?*Heap = null,

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
    // category-1
    fn pushInt(f: *Frame, x: i32) RunError!void {
        return f.push(.{ .int = x });
    }
    fn popInt(f: *Frame) RunError!i32 {
        return switch (try f.pop()) {
            .int => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn pushFloat(f: *Frame, x: f32) RunError!void {
        return f.push(.{ .float = x });
    }
    fn popFloat(f: *Frame) RunError!f32 {
        return switch (try f.pop()) {
            .float => |x| x,
            else => error.TypeMismatch,
        };
    }
    // category-2: value then top
    fn pushLong(f: *Frame, x: i64) RunError!void {
        try f.push(.{ .long = x });
        try f.push(.top);
    }
    fn popLong(f: *Frame) RunError!i64 {
        if (!isTop(try f.pop())) return error.TypeMismatch;
        return switch (try f.pop()) {
            .long => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn pushDouble(f: *Frame, x: f64) RunError!void {
        try f.push(.{ .double = x });
        try f.push(.top);
    }
    fn popDouble(f: *Frame) RunError!f64 {
        if (!isTop(try f.pop())) return error.TypeMismatch;
        return switch (try f.pop()) {
            .double => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn pushKind(f: *Frame, v: Value) RunError!void {
        switch (v) {
            .int => |x| try f.pushInt(x),
            .float => |x| try f.pushFloat(x),
            .long => |x| try f.pushLong(x),
            .double => |x| try f.pushDouble(x),
            .reference => try f.push(v),
            .top => return error.TypeMismatch,
        }
    }
    fn popKind(f: *Frame, kind: Kind) RunError!Value {
        return switch (kind) {
            .int => .{ .int = try f.popInt() },
            .float => .{ .float = try f.popFloat() },
            .long => .{ .long = try f.popLong() },
            .double => .{ .double = try f.popDouble() },
            .reference => .{ .reference = try f.popRef() },
        };
    }
    // locals
    fn localInt(f: *Frame, idx: usize) RunError!i32 {
        if (idx >= f.locals.len) return error.BadLocal;
        return switch (f.locals[idx]) {
            .int => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn localFloat(f: *Frame, idx: usize) RunError!f32 {
        if (idx >= f.locals.len) return error.BadLocal;
        return switch (f.locals[idx]) {
            .float => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn localLong(f: *Frame, idx: usize) RunError!i64 {
        if (idx + 1 >= f.locals.len) return error.BadLocal;
        return switch (f.locals[idx]) {
            .long => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn localDouble(f: *Frame, idx: usize) RunError!f64 {
        if (idx + 1 >= f.locals.len) return error.BadLocal;
        return switch (f.locals[idx]) {
            .double => |x| x,
            else => error.TypeMismatch,
        };
    }
    fn setLocal1(f: *Frame, idx: usize, v: Value) RunError!void {
        if (idx >= f.locals.len) return error.BadLocal;
        f.locals[idx] = v;
    }
    fn setLocal2(f: *Frame, idx: usize, v: Value) RunError!void {
        if (idx + 1 >= f.locals.len) return error.BadLocal;
        f.locals[idx] = v;
        f.locals[idx + 1] = .top;
    }
    fn localRaw(f: *Frame, idx: usize) RunError!Value {
        if (idx >= f.locals.len) return error.BadLocal;
        return f.locals[idx];
    }
    fn popRef(f: *Frame) RunError!?u32 {
        return switch (try f.pop()) {
            .reference => |r| r,
            else => error.TypeMismatch,
        };
    }
};

pub const Object = struct { class: *const Class, fields: []Value };

pub const Heap = struct {
    gpa: std.mem.Allocator,
    objects: std.ArrayList(Object) = .empty,

    pub fn deinit(self: *Heap) void {
        for (self.objects.items) |o| self.gpa.free(o.fields);
        self.objects.deinit(self.gpa);
    }
    fn defaultValue(k: Kind) Value {
        return switch (k) {
            .int => .{ .int = 0 },
            .long => .{ .long = 0 },
            .float => .{ .float = 0 },
            .double => .{ .double = 0 },
            .reference => .{ .reference = null },
        };
    }
    pub fn alloc(self: *Heap, class: *const Class) !u32 {
        const fields = try self.gpa.alloc(Value, class.instance_fields.len);
        for (fields, class.instance_fields) |*fv, fd| fv.* = defaultValue(fd.kind);
        try self.objects.append(self.gpa, .{ .class = class, .fields = fields });
        return @intCast(self.objects.items.len - 1);
    }
    pub fn get(self: *Heap, id: u32) *Object {
        return &self.objects.items[id];
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
fn i32At(code: []const u8, off: usize) RunError!i32 {
    if (off + 4 > code.len) return error.Truncated;
    return std.mem.readInt(i32, code[off..][0..4], .big);
}
fn switchPad(pc: usize) usize {
    return (4 - ((pc + 1) % 4)) % 4;
}
fn branch(pc: usize, offset: i32, code_len: usize) RunError!usize {
    const target = @as(i64, @intCast(pc)) + offset;
    if (target < 0 or target >= code_len) return error.BadBranch;
    return @intCast(target);
}

pub const Class = struct {
    gpa: std.mem.Allocator,
    cp: ConstantPool,
    name: []const u8,
    methods: []Method,
    instance_fields: []Field,

    pub const Field = struct { name: []const u8, kind: Kind };
    pub const Param = struct { kind: Kind, slot: u16 };
    pub const Method = struct {
        name: []const u8,
        descriptor: []const u8,
        code: ?attribute_decode.CodeAttr,
        params: []Param,
        arg_slots: u16,
        ret: ?Kind,
        is_static: bool,
    };

    fn kindOf(ft: descriptor.FieldType) Kind {
        if (ft.dims > 0) return .reference;
        return switch (ft.kind) {
            .object => .reference,
            .base => |b| switch (b) {
                .long => .long,
                .float => .float,
                .double => .double,
                else => .int, // byte/char/short/boolean/int all live in an int slot
            },
        };
    }

    pub fn init(gpa: std.mem.Allocator, arena: std.mem.Allocator, cf: *const ClassFile) !Class {
        const cls_name = try cf.constant_pool.classNameOf(cf.this_class);

        // Instance (non-static) fields, in declaration order.
        var nfields: usize = 0;
        for (cf.fields) |fld| {
            if (!fld.access_flags.isStatic()) nfields += 1;
        }
        const instance_fields = try arena.alloc(Field, nfields);
        var fi: usize = 0;
        for (cf.fields) |fld| {
            if (fld.access_flags.isStatic()) continue;
            const fdesc = try cf.constant_pool.utf8(fld.descriptor_index);
            instance_fields[fi] = .{
                .name = try cf.constant_pool.utf8(fld.name_index),
                .kind = kindOf(try descriptor.parseFieldDescriptor(fdesc)),
            };
            fi += 1;
        }

        const methods = try arena.alloc(Method, cf.methods.len);
        for (cf.methods, 0..) |m, i| {
            const name = try cf.constant_pool.utf8(m.name_index);
            const desc = try cf.constant_pool.utf8(m.descriptor_index);
            const is_static = m.access_flags.isStatic();
            const mt = try descriptor.parseMethodDescriptor(arena, desc);
            const params = try arena.alloc(Param, mt.params.len);
            var slot: u16 = if (is_static) 0 else 1; // slot 0 is `this` for instance methods
            for (mt.params, 0..) |pt, k| {
                const kind = kindOf(pt);
                params[k] = .{ .kind = kind, .slot = slot };
                slot += if (kind == .long or kind == .double) 2 else 1;
            }
            var code: ?attribute_decode.CodeAttr = null;
            for (m.attributes) |ai| {
                if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "Code")) {
                    code = (try attribute_decode.decode(arena, cf.constant_pool, ai)).code;
                }
            }
            methods[i] = .{
                .name = name,
                .descriptor = desc,
                .code = code,
                .params = params,
                .arg_slots = slot,
                .ret = if (mt.ret) |r| kindOf(r) else null,
                .is_static = is_static,
            };
        }
        return .{ .gpa = gpa, .cp = cf.constant_pool, .name = cls_name, .methods = methods, .instance_fields = instance_fields };
    }

    fn findField(self: *const Class, name: []const u8) ?usize {
        for (self.instance_fields, 0..) |fd, i| {
            if (std.mem.eql(u8, fd.name, name)) return i;
        }
        return null;
    }

    fn find(self: *const Class, name: []const u8, desc: []const u8) ?*const Method {
        for (self.methods) |*m| {
            if (std.mem.eql(u8, m.name, name) and std.mem.eql(u8, m.descriptor, desc)) return m;
        }
        return null;
    }

    /// Lay out logical argument values into a slot buffer (category-2 gets a top).
    fn layoutArgs(m: *const Method, args: []const Value, out: []Value) RunError!usize {
        if (args.len != m.params.len) return error.LinkError;
        for (m.params, 0..) |p, i| {
            out[p.slot] = args[i];
            if (p.kind == .long or p.kind == .double) out[p.slot + 1] = .top;
        }
        return m.arg_slots;
    }

    pub fn callStaticValues(self: *const Class, name: []const u8, desc: []const u8, args: []const Value, budget: *Budget) RunError!?Value {
        const m = self.find(name, desc) orelse return error.MethodNotFound;
        const c = m.code orelse return error.LinkError;
        var slots: [256]Value = undefined;
        if (m.arg_slots > slots.len) return error.LinkError;
        const n = try layoutArgs(m, args, &slots);
        var heap = Heap{ .gpa = self.gpa };
        defer heap.deinit();
        return exec(self.gpa, self, &heap, budget, c.code, c.max_stack, c.max_locals, slots[0..n]);
    }

    /// Convenience for int-argument methods with a fresh budget.
    pub fn callStatic(self: *const Class, name: []const u8, desc: []const u8, int_args: []const i32) RunError!?Value {
        var buf: [64]Value = undefined;
        if (int_args.len > buf.len) return error.LinkError;
        for (int_args, 0..) |a, i| buf[i] = .{ .int = a };
        var b = Budget{};
        return self.callStaticValues(name, desc, buf[0..int_args.len], &b);
    }
};

fn refClassName(cls: *const Class, class_index: u16) RunError![]const u8 {
    const c = cls.cp.get(class_index) catch return error.LinkError;
    const ni = switch (c.*) {
        .class => |x| x,
        else => return error.LinkError,
    };
    return cls.cp.utf8(ni) catch error.LinkError;
}

fn fieldName(cls: *const Class, cp_index: u16) RunError![]const u8 {
    const c = cls.cp.get(cp_index) catch return error.LinkError;
    const ref = switch (c.*) {
        .fieldref => |r| r,
        else => return error.LinkError,
    };
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    return cls.cp.utf8(nat.name_index) catch error.LinkError;
}

fn doNew(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const cname = try refClassName(cls, try u16At(code, f.pc + 1));
    if (!std.mem.eql(u8, cname, cls.name)) return error.UnsupportedOpcode; // only self-class for now
    try f.push(.{ .reference = try heap.alloc(cls) });
}

fn doGetField(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const fname = try fieldName(cls, try u16At(code, f.pc + 1));
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const obj = heap.get(oid);
    const fi = obj.class.findField(fname) orelse return error.LinkError;
    try f.push(obj.fields[fi]);
}

fn doPutField(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const fname = try fieldName(cls, try u16At(code, f.pc + 1));
    const value = try f.pop();
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const obj = heap.get(oid);
    const fi = obj.class.findField(fname) orelse return error.LinkError;
    obj.fields[fi] = value;
}

fn invokeInstance(f: *Frame, cls: *const Class, code: []const u8, is_special: bool) RunError!void {
    const idx = try u16At(code, f.pc + 1);
    const c = cls.cp.get(idx) catch return error.LinkError;
    const ref = switch (c.*) {
        .methodref => |r| r,
        else => return error.LinkError,
    };
    const cname = try refClassName(cls, ref.class_index);
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const mname = cls.cp.utf8(nat.name_index) catch return error.LinkError;
    const mdesc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;

    // Bootstrap stub: java/lang/Object.<init> is a no-op (we have no JDK loaded).
    if (is_special and std.mem.eql(u8, mname, "<init>") and std.mem.eql(u8, cname, "java/lang/Object")) {
        _ = (try f.popRef()) orelse return error.NullPointer; // consume `this`
        return;
    }

    // Single-class resolution (true virtual dispatch across a hierarchy is future work).
    const target = cls.find(mname, mdesc) orelse return error.MethodNotFound;
    var slots: [256]Value = undefined;
    if (target.arg_slots > slots.len) return error.LinkError;
    var i: usize = target.params.len;
    while (i > 0) {
        i -= 1;
        const p = target.params[i];
        slots[p.slot] = try f.popKind(p.kind);
        if (p.kind == .long or p.kind == .double) slots[p.slot + 1] = .top;
    }
    const oid = (try f.popRef()) orelse return error.NullPointer;
    slots[0] = .{ .reference = oid };

    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const cc = target.code orelse return error.LinkError;
    const ret = try exec(cls.gpa, cls, f.heap, f.budget, cc.code, cc.max_stack, cc.max_locals, slots[0..target.arg_slots]);
    if (ret) |rv| try f.pushKind(rv);
}

fn invokeStatic(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const idx = try u16At(code, f.pc + 1);
    const mref = cls.cp.get(idx) catch return error.LinkError;
    const ref = switch (mref.*) {
        .methodref => |r| r,
        else => return error.LinkError,
    };
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const mname = cls.cp.utf8(nat.name_index) catch return error.LinkError;
    const mdesc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;
    const target = cls.find(mname, mdesc) orelse return error.MethodNotFound;

    // Pop args (reverse order) into the callee's slot layout.
    var slots: [256]Value = undefined;
    if (target.arg_slots > slots.len) return error.LinkError;
    var i: usize = target.params.len;
    while (i > 0) {
        i -= 1;
        const p = target.params[i];
        const v = try f.popKind(p.kind);
        slots[p.slot] = v;
        if (p.kind == .long or p.kind == .double) slots[p.slot + 1] = .top;
    }

    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const c = target.code orelse return error.LinkError;
    const ret = try exec(cls.gpa, cls, f.heap, f.budget, c.code, c.max_stack, c.max_locals, slots[0..target.arg_slots]);
    if (ret) |rv| try f.pushKind(rv);
}

fn loadConstant(f: *Frame, class: ?*const Class, index: u16) RunError!void {
    const cls = class orelse return error.UnsupportedOpcode;
    switch ((cls.cp.get(index) catch return error.LinkError).*) {
        .integer => |v| try f.pushInt(v),
        .float => |v| try f.pushFloat(v),
        else => return error.UnsupportedOpcode,
    }
}
fn loadConstant2(f: *Frame, class: ?*const Class, index: u16) RunError!void {
    const cls = class orelse return error.UnsupportedOpcode;
    switch ((cls.cp.get(index) catch return error.LinkError).*) {
        .long => |v| try f.pushLong(v),
        .double => |v| try f.pushDouble(v),
        else => return error.UnsupportedOpcode,
    }
}

fn exec(alloc: std.mem.Allocator, class: ?*const Class, heap: ?*Heap, budget: *Budget, code: []const u8, max_stack: u16, max_locals: u16, arg_slots: []const Value) RunError!?Value {
    if (arg_slots.len > max_locals) return error.BadLocal;
    const stack = try alloc.alloc(Value, max_stack);
    defer alloc.free(stack);
    const locals = try alloc.alloc(Value, max_locals);
    defer alloc.free(locals);
    for (locals) |*l| l.* = .{ .int = 0 };
    for (arg_slots, 0..) |v, i| locals[i] = v;

    var f = Frame{ .stack = stack, .locals = locals, .budget = budget, .heap = heap };

    sw: switch (try opAt(code, f.pc)) {
        .nop => {
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- constants ----
        .iconst_m1, .iconst_0, .iconst_1, .iconst_2, .iconst_3, .iconst_4, .iconst_5 => |o| {
            try f.pushInt(@as(i32, @intFromEnum(o)) - @intFromEnum(Op.iconst_0));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .lconst_0, .lconst_1 => |o| {
            try f.pushLong(@intFromEnum(o) - @intFromEnum(Op.lconst_0));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .fconst_0, .fconst_1, .fconst_2 => |o| {
            try f.pushFloat(@floatFromInt(@intFromEnum(o) - @intFromEnum(Op.fconst_0)));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dconst_0, .dconst_1 => |o| {
            try f.pushDouble(@floatFromInt(@intFromEnum(o) - @intFromEnum(Op.dconst_0)));
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
        .ldc2_w => {
            try loadConstant2(&f, class, try u16At(code, f.pc + 1));
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        // ---- loads ----
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
        .lload => {
            try f.pushLong(try f.localLong(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .lload_0, .lload_1, .lload_2, .lload_3 => |o| {
            try f.pushLong(try f.localLong(@intFromEnum(o) - @intFromEnum(Op.lload_0)));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .fload => {
            try f.pushFloat(try f.localFloat(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .fload_0, .fload_1, .fload_2, .fload_3 => |o| {
            try f.pushFloat(try f.localFloat(@intFromEnum(o) - @intFromEnum(Op.fload_0)));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dload => {
            try f.pushDouble(try f.localDouble(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .dload_0, .dload_1, .dload_2, .dload_3 => |o| {
            try f.pushDouble(try f.localDouble(@intFromEnum(o) - @intFromEnum(Op.dload_0)));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- stores ----
        .istore => {
            try f.setLocal1(try u8At(code, f.pc + 1), .{ .int = try f.popInt() });
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .istore_0, .istore_1, .istore_2, .istore_3 => |o| {
            try f.setLocal1(@intFromEnum(o) - @intFromEnum(Op.istore_0), .{ .int = try f.popInt() });
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .lstore => {
            try f.setLocal2(try u8At(code, f.pc + 1), .{ .long = try f.popLong() });
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .lstore_0, .lstore_1, .lstore_2, .lstore_3 => |o| {
            try f.setLocal2(@intFromEnum(o) - @intFromEnum(Op.lstore_0), .{ .long = try f.popLong() });
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .fstore => {
            try f.setLocal1(try u8At(code, f.pc + 1), .{ .float = try f.popFloat() });
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .fstore_0, .fstore_1, .fstore_2, .fstore_3 => |o| {
            try f.setLocal1(@intFromEnum(o) - @intFromEnum(Op.fstore_0), .{ .float = try f.popFloat() });
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dstore => {
            try f.setLocal2(try u8At(code, f.pc + 1), .{ .double = try f.popDouble() });
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .dstore_0, .dstore_1, .dstore_2, .dstore_3 => |o| {
            try f.setLocal2(@intFromEnum(o) - @intFromEnum(Op.dstore_0), .{ .double = try f.popDouble() });
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- stack ops ----
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
        .pop2 => {
            if (f.sp < 2) return error.StackUnderflow;
            f.sp -= 2;
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dup_x1 => {
            if (f.sp < 2) return error.StackUnderflow;
            if (f.sp + 1 > f.stack.len) return error.StackOverflow;
            const a = f.stack[f.sp - 1];
            const b = f.stack[f.sp - 2];
            f.stack[f.sp - 2] = a;
            f.stack[f.sp - 1] = b;
            f.stack[f.sp] = a;
            f.sp += 1;
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dup_x2 => {
            if (f.sp < 3) return error.StackUnderflow;
            if (f.sp + 1 > f.stack.len) return error.StackOverflow;
            const a = f.stack[f.sp - 1];
            const b = f.stack[f.sp - 2];
            const c = f.stack[f.sp - 3];
            f.stack[f.sp - 3] = a;
            f.stack[f.sp - 2] = c;
            f.stack[f.sp - 1] = b;
            f.stack[f.sp] = a;
            f.sp += 1;
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dup2 => {
            if (f.sp < 2) return error.StackUnderflow;
            if (f.sp + 2 > f.stack.len) return error.StackOverflow;
            const a = f.stack[f.sp - 1];
            const b = f.stack[f.sp - 2];
            f.stack[f.sp] = b;
            f.stack[f.sp + 1] = a;
            f.sp += 2;
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dup2_x1 => {
            if (f.sp < 3) return error.StackUnderflow;
            if (f.sp + 2 > f.stack.len) return error.StackOverflow;
            const a = f.stack[f.sp - 1];
            const b = f.stack[f.sp - 2];
            const c = f.stack[f.sp - 3];
            f.stack[f.sp - 3] = b;
            f.stack[f.sp - 2] = a;
            f.stack[f.sp - 1] = c;
            f.stack[f.sp] = b;
            f.stack[f.sp + 1] = a;
            f.sp += 2;
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dup2_x2 => {
            if (f.sp < 4) return error.StackUnderflow;
            if (f.sp + 2 > f.stack.len) return error.StackOverflow;
            const a = f.stack[f.sp - 1];
            const b = f.stack[f.sp - 2];
            const c = f.stack[f.sp - 3];
            const d = f.stack[f.sp - 4];
            f.stack[f.sp - 4] = b;
            f.stack[f.sp - 3] = a;
            f.stack[f.sp - 2] = d;
            f.stack[f.sp - 1] = c;
            f.stack[f.sp] = b;
            f.stack[f.sp + 1] = a;
            f.sp += 2;
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- int arithmetic ----
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
        // ---- long arithmetic ----
        .ladd, .lsub, .lmul, .ldiv, .lrem, .land, .lor, .lxor => |o| {
            const y = try f.popLong();
            const x = try f.popLong();
            try f.pushLong(try longBinary(o, x, y));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .lshl, .lshr, .lushr => |o| {
            const s = try f.popInt();
            const x = try f.popLong();
            try f.pushLong(longShift(o, x, s));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .lneg => {
            try f.pushLong(0 -% try f.popLong());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- float arithmetic ----
        .fadd, .fsub, .fmul, .fdiv, .frem => |o| {
            const y = try f.popFloat();
            const x = try f.popFloat();
            try f.pushFloat(floatBinary(o, x, y));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .fneg => {
            try f.pushFloat(-try f.popFloat());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- double arithmetic ----
        .dadd, .dsub, .dmul, .ddiv, .drem => |o| {
            const y = try f.popDouble();
            const x = try f.popDouble();
            try f.pushDouble(doubleBinary(o, x, y));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dneg => {
            try f.pushDouble(-try f.popDouble());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- conversions ----
        .i2l => {
            try f.pushLong(try f.popInt());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .i2f => {
            try f.pushFloat(@floatFromInt(try f.popInt()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .i2d => {
            try f.pushDouble(@floatFromInt(try f.popInt()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .l2i => {
            try f.pushInt(@truncate(try f.popLong()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .l2f => {
            try f.pushFloat(@floatFromInt(try f.popLong()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .l2d => {
            try f.pushDouble(@floatFromInt(try f.popLong()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .f2i => {
            try f.pushInt(f2i(try f.popFloat()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .f2l => {
            try f.pushLong(f2l(try f.popFloat()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .f2d => {
            try f.pushDouble(try f.popFloat());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .d2i => {
            try f.pushInt(f2i(try f.popDouble()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .d2l => {
            try f.pushLong(f2l(try f.popDouble()));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .d2f => {
            try f.pushFloat(@floatCast(try f.popDouble()));
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
        // ---- comparisons ----
        .lcmp => {
            const y = try f.popLong();
            const x = try f.popLong();
            try f.pushInt(if (x > y) @as(i32, 1) else if (x < y) @as(i32, -1) else 0);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .fcmpl, .fcmpg => |o| {
            const y = try f.popFloat();
            const x = try f.popFloat();
            try f.pushInt(fcmp(o == .fcmpg, x, y));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dcmpl, .dcmpg => |o| {
            const y = try f.popDouble();
            const x = try f.popDouble();
            try f.pushInt(dcmp(o == .dcmpg, x, y));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        // ---- iinc ----
        .iinc => {
            const idx = try u8At(code, f.pc + 1);
            const c = try s8(code, f.pc + 2);
            try f.setLocal1(idx, .{ .int = (try f.localInt(idx)) +% c });
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        // ---- branches ----
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
        .tableswitch => {
            const key = try f.popInt();
            const p = f.pc + 1 + switchPad(f.pc);
            const def = try i32At(code, p);
            const low = try i32At(code, p + 4);
            const high = try i32At(code, p + 8);
            var off = def;
            if (key >= low and key <= high) {
                const i: usize = @intCast(@as(i64, key) - @as(i64, low));
                off = try i32At(code, p + 12 + i * 4);
            }
            f.pc = try branch(f.pc, off, code.len);
            continue :sw try step(&f, code);
        },
        .lookupswitch => {
            const key = try f.popInt();
            const p = f.pc + 1 + switchPad(f.pc);
            const def = try i32At(code, p);
            const npairs = try i32At(code, p + 4);
            if (npairs < 0) return error.BadBranch;
            var off = def;
            var i: usize = 0;
            while (i < @as(usize, @intCast(npairs))) : (i += 1) {
                if (try i32At(code, p + 8 + i * 8) == key) {
                    off = try i32At(code, p + 8 + i * 8 + 4);
                    break;
                }
            }
            f.pc = try branch(f.pc, off, code.len);
            continue :sw try step(&f, code);
        },
        .wide => {
            const w = try opAt(code, f.pc + 1);
            const idx: usize = try u16At(code, f.pc + 2);
            switch (w) {
                .iload => try f.pushInt(try f.localInt(idx)),
                .lload => try f.pushLong(try f.localLong(idx)),
                .fload => try f.pushFloat(try f.localFloat(idx)),
                .dload => try f.pushDouble(try f.localDouble(idx)),
                .istore => try f.setLocal1(idx, .{ .int = try f.popInt() }),
                .lstore => try f.setLocal2(idx, .{ .long = try f.popLong() }),
                .fstore => try f.setLocal1(idx, .{ .float = try f.popFloat() }),
                .dstore => try f.setLocal2(idx, .{ .double = try f.popDouble() }),
                .iinc => {
                    const c = try s16(code, f.pc + 4);
                    try f.setLocal1(idx, .{ .int = (try f.localInt(idx)) +% c });
                    f.pc += 6;
                    continue :sw try step(&f, code);
                },
                else => return error.UnsupportedOpcode,
            }
            f.pc += 4;
            continue :sw try step(&f, code);
        },
        // ---- calls / returns ----
        .aconst_null => {
            try f.push(.{ .reference = null });
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .aload => {
            try f.push(try f.localRaw(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .aload_0, .aload_1, .aload_2, .aload_3 => |o| {
            try f.push(try f.localRaw(@intFromEnum(o) - @intFromEnum(Op.aload_0)));
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .astore => {
            try f.setLocal1(try u8At(code, f.pc + 1), try f.pop());
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .astore_0, .astore_1, .astore_2, .astore_3 => |o| {
            try f.setLocal1(@intFromEnum(o) - @intFromEnum(Op.astore_0), try f.pop());
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .areturn => return try f.pop(),
        .new => {
            const cls = class orelse return error.UnsupportedOpcode;
            try doNew(&f, cls, code);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .getfield => {
            const cls = class orelse return error.UnsupportedOpcode;
            try doGetField(&f, cls, code);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .putfield => {
            const cls = class orelse return error.UnsupportedOpcode;
            try doPutField(&f, cls, code);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokespecial => {
            const cls = class orelse return error.UnsupportedOpcode;
            try invokeInstance(&f, cls, code, true);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokevirtual => {
            const cls = class orelse return error.UnsupportedOpcode;
            try invokeInstance(&f, cls, code, false);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokestatic => {
            const cls = class orelse return error.UnsupportedOpcode;
            try invokeStatic(&f, cls, code);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .ireturn => return .{ .int = try f.popInt() },
        .lreturn => return .{ .long = try f.popLong() },
        .freturn => return .{ .float = try f.popFloat() },
        .dreturn => return .{ .double = try f.popDouble() },
        .@"return" => return null,
        else => return error.UnsupportedOpcode,
    }
}

// ---- helpers -------------------------------------------------------------

pub const default_budget: usize = 100_000_000;

pub fn runInt(a: std.mem.Allocator, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32) RunError!?i32 {
    return runIntBudgeted(a, code, max_stack, max_locals, args, default_budget);
}
pub fn runIntBudgeted(a: std.mem.Allocator, code: []const u8, max_stack: u16, max_locals: u16, args: []const i32, max_steps: usize) RunError!?i32 {
    var b = Budget{ .max_steps = max_steps };
    var buf: [64]Value = undefined;
    if (args.len > buf.len) return error.BadLocal;
    for (args, 0..) |x, i| buf[i] = .{ .int = x };
    const r = try exec(a, null, null, &b, code, max_stack, max_locals, buf[0..args.len]);
    return if (r) |v| switch (v) {
        .int => |x| x,
        else => error.TypeMismatch,
    } else null;
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

fn longBinary(o: Op, x: i64, y: i64) RunError!i64 {
    return switch (o) {
        .ladd => x +% y,
        .lsub => x -% y,
        .lmul => x *% y,
        .ldiv => blk: {
            if (y == 0) return error.ArithmeticException;
            if (x == std.math.minInt(i64) and y == -1) break :blk std.math.minInt(i64);
            break :blk @divTrunc(x, y);
        },
        .lrem => blk: {
            if (y == 0) return error.ArithmeticException;
            if (x == std.math.minInt(i64) and y == -1) break :blk 0;
            break :blk @rem(x, y);
        },
        .land => x & y,
        .lor => x | y,
        .lxor => x ^ y,
        else => error.UnsupportedOpcode,
    };
}

fn longShift(o: Op, x: i64, s: i32) i64 {
    const amount: u6 = @intCast(@as(u32, @bitCast(s)) & 0x3f);
    return switch (o) {
        .lshl => x << amount,
        .lshr => x >> amount,
        .lushr => @bitCast(@as(u64, @bitCast(x)) >> amount),
        else => 0,
    };
}

fn floatBinary(o: Op, x: f32, y: f32) f32 {
    return switch (o) {
        .fadd => x + y,
        .fsub => x - y,
        .fmul => x * y,
        .fdiv => x / y,
        .frem => @rem(x, y),
        else => 0,
    };
}
fn doubleBinary(o: Op, x: f64, y: f64) f64 {
    return switch (o) {
        .dadd => x + y,
        .dsub => x - y,
        .dmul => x * y,
        .ddiv => x / y,
        .drem => @rem(x, y),
        else => 0,
    };
}

fn f2i(x: anytype) i32 {
    if (std.math.isNan(x)) return 0;
    if (x >= 2147483647.0) return std.math.maxInt(i32);
    if (x <= -2147483648.0) return std.math.minInt(i32);
    return @intFromFloat(@trunc(x));
}
fn f2l(x: anytype) i64 {
    if (std.math.isNan(x)) return 0;
    if (x >= 9223372036854775808.0) return std.math.maxInt(i64);
    if (x <= -9223372036854775808.0) return std.math.minInt(i64);
    return @intFromFloat(@trunc(x));
}

fn fcmp(g: bool, x: f32, y: f32) i32 {
    if (std.math.isNan(x) or std.math.isNan(y)) return if (g) 1 else -1;
    return if (x > y) @as(i32, 1) else if (x < y) @as(i32, -1) else 0;
}
fn dcmp(g: bool, x: f64, y: f64) i32 {
    if (std.math.isNan(x) or std.math.isNan(y)) return if (g) 1 else -1;
    return if (x > y) @as(i32, 1) else if (x < y) @as(i32, -1) else 0;
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

// ---- tests ---------------------------------------------------------------

const testing = std.testing;

test "hand-assembled int: (2 + 3) * 4 = 20" {
    const code = [_]u8{ 0x05, 0x06, 0x60, 0x07, 0x68, 0xac };
    try testing.expectEqual(@as(?i32, 20), try runInt(testing.allocator, &code, 2, 0, &.{}));
}
test "division by zero traps" {
    const code = [_]u8{ 0x04, 0x03, 0x6c, 0xac };
    try testing.expectError(error.ArithmeticException, runInt(testing.allocator, &code, 2, 0, &.{}));
}
test "stack underflow is caught" {
    const code = [_]u8{ 0x60, 0xac };
    try testing.expectError(error.StackUnderflow, runInt(testing.allocator, &code, 2, 0, &.{}));
}
test "infinite loop hits the step budget" {
    const code = [_]u8{ 0xa7, 0x00, 0x00 };
    try testing.expectError(error.StepLimitExceeded, runIntBudgeted(testing.allocator, &code, 0, 0, &.{}, 1000));
}

fn loadClass(comptime path: []const u8, cf: *ClassFile, arena: *std.heap.ArenaAllocator) !Class {
    cf.* = try ClassFile.parse(testing.allocator, @embedFile(path));
    arena.* = std.heap.ArenaAllocator.init(testing.allocator);
    return Class.init(testing.allocator, arena.allocator(), cf);
}

test "Compute: int methods still work" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Compute.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    try testing.expectEqual(Value{ .int = 55 }, (try cls.callStatic("sumTo", "(I)I", &.{10})).?);
    try testing.expectEqual(Value{ .int = 120 }, (try cls.callStatic("fact", "(I)I", &.{5})).?);
    try testing.expectEqual(Value{ .int = 1000042 }, (try cls.callStatic("big", "(I)I", &.{42})).?);
}

test "Recur: recursion still works" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Recur.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    try testing.expectEqual(Value{ .int = 55 }, (try cls.callStatic("fib", "(I)I", &.{10})).?);
    try testing.expectEqual(Value{ .int = 12 }, (try cls.callStatic("gcd", "(II)I", &.{ 48, 36 })).?);
}

test "Numeric: long results" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Numeric.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    try testing.expectEqual(Value{ .long = 120 }, (try cls.callStatic("lfact", "(I)J", &.{5})).?);
    try testing.expectEqual(Value{ .long = 3628800 }, (try cls.callStatic("lfact", "(I)J", &.{10})).?);
    try testing.expectEqual(Value{ .long = 5050 }, (try cls.callStatic("lsum", "(I)J", &.{100})).?);
    // (1<<40)>>20 == 1<<20
    var b = Budget{};
    try testing.expectEqual(Value{ .long = 1 << 20 }, (try cls.callStaticValues("shifts", "(J)J", &.{.{ .long = 1 }}, &b)).?);
}

test "Numeric: long params (addLong)" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Numeric.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    var b = Budget{};
    const r = try cls.callStaticValues("addLong", "(JJ)J", &.{ .{ .long = 1_000_000_000_000 }, .{ .long = 2_000_000_000_000 } }, &b);
    try testing.expectEqual(Value{ .long = 3_000_000_000_000 }, r.?);
}

test "Numeric: double and float results" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Numeric.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    try testing.expectEqual(Value{ .double = 3.5 }, (try cls.callStatic("davg", "(II)D", &.{ 3, 4 })).?);
    try testing.expectEqual(Value{ .float = 2.5 }, (try cls.callStatic("fhalf", "(I)F", &.{5})).?);
    var b = Budget{};
    // dpoly(2.0) = 4 - 4 + 1 = 1.0
    try testing.expectEqual(Value{ .double = 1.0 }, (try cls.callStaticValues("dpoly", "(D)D", &.{.{ .double = 2.0 }}, &b)).?);
}

test "Numeric: double comparison (dsgn uses dcmp)" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Numeric.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    var b = Budget{};
    try testing.expectEqual(Value{ .int = -1 }, (try cls.callStaticValues("dsgn", "(DD)I", &.{ .{ .double = 1.0 }, .{ .double = 2.0 } }, &b)).?);
    b = Budget{};
    try testing.expectEqual(Value{ .int = 1 }, (try cls.callStaticValues("dsgn", "(DD)I", &.{ .{ .double = 5.0 }, .{ .double = 2.0 } }, &b)).?);
    b = Budget{};
    try testing.expectEqual(Value{ .int = 0 }, (try cls.callStaticValues("dsgn", "(DD)I", &.{ .{ .double = 2.0 }, .{ .double = 2.0 } }, &b)).?);
}

test "stack op dup2: (2,3) duplicated then summed = 10" {
    // iconst_2 iconst_3 dup2 iadd iadd iadd ireturn
    const code = [_]u8{ 0x05, 0x06, 0x5c, 0x60, 0x60, 0x60, 0xac };
    try testing.expectEqual(@as(?i32, 10), try runInt(testing.allocator, &code, 4, 0, &.{}));
}

test "stack op dup_x1" {
    // iconst_1 iconst_2 dup_x1 -> stack 2,1,2 ; iadd iadd -> 2+1+2=5
    const code = [_]u8{ 0x04, 0x05, 0x5a, 0x60, 0x60, 0xac };
    try testing.expectEqual(@as(?i32, 5), try runInt(testing.allocator, &code, 3, 0, &.{}));
}

test "tableswitch executes (Switch.dense)" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Switch.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    try testing.expectEqual(Value{ .int = 10 }, (try cls.callStatic("dense", "(I)I", &.{0})).?);
    try testing.expectEqual(Value{ .int = 30 }, (try cls.callStatic("dense", "(I)I", &.{2})).?);
    try testing.expectEqual(Value{ .int = 40 }, (try cls.callStatic("dense", "(I)I", &.{3})).?);
    try testing.expectEqual(Value{ .int = -1 }, (try cls.callStatic("dense", "(I)I", &.{9})).?);
}

test "lookupswitch executes (Switch.sparse)" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Switch.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    try testing.expectEqual(Value{ .int = 1 }, (try cls.callStatic("sparse", "(I)I", &.{1})).?);
    try testing.expectEqual(Value{ .int = 2 }, (try cls.callStatic("sparse", "(I)I", &.{100})).?);
    try testing.expectEqual(Value{ .int = 3 }, (try cls.callStatic("sparse", "(I)I", &.{1000})).?);
    try testing.expectEqual(Value{ .int = 0 }, (try cls.callStatic("sparse", "(I)I", &.{7})).?);
}

test "wide istore/iload/iinc" {
    // bipush 42 ; wide istore 258 ; wide iinc 258, +8 ; wide iload 258 ; ireturn
    const code = [_]u8{ 0x10, 42, 0xc4, 0x36, 0x01, 0x02, 0xc4, 0x84, 0x01, 0x02, 0x00, 0x08, 0xc4, 0x15, 0x01, 0x02, 0xac };
    try testing.expectEqual(@as(?i32, 50), try runInt(testing.allocator, &code, 1, 300, &.{}));
}

test "object model: construct, mutate fields, virtual + special dispatch (Point)" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Point.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();

    // make(3,4): new Point(3,4) -> bump() -> (4,5) -> scaledSum(2) = (4+5)*2 = 18
    try testing.expectEqual(Value{ .int = 18 }, (try cls.callStatic("make", "(II)I", &.{ 3, 4 })).?);
    // dist2(3,4): sum()=7 -> 49
    try testing.expectEqual(Value{ .int = 49 }, (try cls.callStatic("dist2", "(II)I", &.{ 3, 4 })).?);
    // a few more
    try testing.expectEqual(Value{ .int = 2 }, (try cls.callStatic("make", "(II)I", &.{ -1, 0 })).?); // bump->(0,1); (0+1)*2 = 2
}
