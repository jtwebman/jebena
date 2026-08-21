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
const mutf8_mod = @import("mutf8.zig");

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
    NegativeArraySize,
    ArrayIndexOutOfBounds,
    JavaException,
    Truncated,
} || bc.DecodeError || std.mem.Allocator.Error;

pub const Budget = struct {
    steps: usize = 0,
    max_steps: usize = 100_000_000,
    depth: usize = 0,
    max_depth: usize = 2500,
    /// Exception object id currently propagating up the native call stack.
    pending: ?u32 = null,
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
    loader: *Loader = undefined,
    parent: ?*Frame = null,

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

pub const Instance = struct { class: *const Class, fields: []Value };
pub const Array = struct { elem: Kind, data: []Value };
pub const StringObj = struct { class: *const Class, chars: []i32 }; // UTF-16 code units
pub const HeapObj = union(enum) { instance: Instance, array: Array, string: StringObj };

const stub_cp_entries = [_]constant_pool.Constant{.unusable};
const stub_return_code = [_]u8{0xb1}; // just `return`

fn defaultValue(k: Kind) Value {
    return switch (k) {
        .int => .{ .int = 0 },
        .long => .{ .long = 0 },
        .float => .{ .float = 0 },
        .double => .{ .double = 0 },
        .reference => .{ .reference = null },
    };
}

pub const Heap = struct {
    gpa: std.mem.Allocator,
    /// Object table; a null slot is free (reused via free_list). Object ids are
    /// stable across collection (non-moving GC).
    objects: std.ArrayList(?HeapObj) = .empty,
    marked: std.ArrayList(bool) = .empty,
    /// Generation: true = old (survived a minor collection).
    old: std.ArrayList(bool) = .empty,
    /// Remembered set: old objects that may hold references to young objects
    /// (maintained by the write barrier). Parallel to `objects`.
    remembered: std.ArrayList(bool) = .empty,
    free_list: std.ArrayList(u32) = .empty,
    allocs_since_gc: usize = 0,
    minor_count: usize = 0,
    /// Minor collection after this many allocations. Default is effectively off.
    gc_interval: usize = 1 << 20,
    /// A major (compacting) collection every this many minors.
    minors_per_major: usize = 8,

    pub fn deinit(self: *Heap) void {
        for (self.objects.items) |maybe| if (maybe) |o| switch (o) {
            .instance => |x| self.gpa.free(x.fields),
            .array => |x| self.gpa.free(x.data),
            .string => |x| self.gpa.free(x.chars),
        };
        self.objects.deinit(self.gpa);
        self.marked.deinit(self.gpa);
        self.old.deinit(self.gpa);
        self.remembered.deinit(self.gpa);
        self.free_list.deinit(self.gpa);
    }
    fn put(self: *Heap, obj: HeapObj) !u32 {
        if (self.free_list.items.len > 0) {
            const id = self.free_list.items[self.free_list.items.len - 1];
            self.free_list.items.len -= 1;
            self.objects.items[id] = obj;
            self.marked.items[id] = false;
            self.old.items[id] = false; // reused slot: new object is young
            self.remembered.items[id] = false;
            return id;
        }
        try self.objects.append(self.gpa, obj);
        try self.marked.append(self.gpa, false);
        try self.old.append(self.gpa, false);
        try self.remembered.append(self.gpa, false);
        return @intCast(self.objects.items.len - 1);
    }
    pub fn allocInstance(self: *Heap, class: *const Class) !u32 {
        const fields = try self.gpa.alloc(Value, class.instance_fields.len);
        for (fields, class.instance_fields) |*fv, fd| fv.* = defaultValue(fd.kind);
        return self.put(.{ .instance = .{ .class = class, .fields = fields } });
    }
    pub fn allocArray(self: *Heap, elem: Kind, len: usize) !u32 {
        const data = try self.gpa.alloc(Value, len);
        for (data) |*d| d.* = defaultValue(elem);
        return self.put(.{ .array = .{ .elem = elem, .data = data } });
    }
    pub fn putString(self: *Heap, class: *const Class, chars: []i32) !u32 {
        return self.put(.{ .string = .{ .class = class, .chars = chars } });
    }
    pub fn get(self: *Heap, id: u32) *HeapObj {
        return if (self.objects.items[id]) |*o| o else unreachable;
    }
    pub fn liveCount(self: *Heap) usize {
        return self.objects.items.len - self.free_list.items.len;
    }
};

// --- Garbage collector: non-moving mark-sweep -----------------------------

fn markValue(heap: *Heap, v: Value) void {
    switch (v) {
        .reference => |r| if (r) |id| markObject(heap, id),
        else => {},
    }
}
fn markObject(heap: *Heap, id: u32) void {
    if (heap.marked.items[id]) return;
    heap.marked.items[id] = true;
    switch (heap.get(id).*) {
        .instance => |x| for (x.fields) |v| markValue(heap, v),
        .array => |x| if (x.elem == .reference) for (x.data) |v| markValue(heap, v),
        .string => {},
    }
}
fn remapValuePtr(v: *Value, forwarding: []const u32) void {
    switch (v.*) {
        .reference => |r| if (r) |id| {
            v.* = .{ .reference = forwarding[id] };
        },
        else => {},
    }
}
fn remapRoots(f: *Frame, forwarding: []const u32) void {
    var fr: ?*Frame = f;
    while (fr) |ff| {
        for (ff.stack[0..ff.sp]) |*v| remapValuePtr(v, forwarding);
        for (ff.locals) |*v| remapValuePtr(v, forwarding);
        fr = ff.parent;
    }
    for (f.loader.statics.items) |st| for (st) |*v| remapValuePtr(v, forwarding);
}
fn remapObject(obj: *HeapObj, forwarding: []const u32) void {
    switch (obj.*) {
        .instance => |*x| for (x.fields) |*v| remapValuePtr(v, forwarding),
        .array => |*x| if (x.elem == .reference) for (x.data) |*v| remapValuePtr(v, forwarding),
        .string => {},
    }
}

/// Stop-the-world mark-COMPACT (a moving collector). Live objects are compacted
/// to the front of the table and every reference is rewritten to the object's
/// new id (roots via the frame parent-chain, statics, pending exception, and the
/// live object graph). Non-fragmenting; ids change on each collection. Runs at a
/// `new` safepoint. This is the reference-rewriting machinery LXR/Immix require.
fn collectMajor(f: *Frame) void {
    const heap = f.heap orelse return;
    // 1. Mark reachable objects from all roots.
    for (heap.marked.items) |*m| m.* = false;
    var fr: ?*Frame = f;
    while (fr) |ff| {
        for (ff.stack[0..ff.sp]) |v| markValue(heap, v);
        for (ff.locals) |v| markValue(heap, v);
        fr = ff.parent;
    }
    for (f.loader.statics.items) |st| for (st) |v| markValue(heap, v);
    if (f.budget.pending) |eid| markObject(heap, eid);

    // 2. Assign new ids to live objects (compact order); free the dead.
    const forwarding = heap.gpa.alloc(u32, heap.objects.items.len) catch return;
    defer heap.gpa.free(forwarding);
    var new_id: u32 = 0;
    for (heap.objects.items, 0..) |maybe, old| {
        if (maybe) |obj| {
            if (heap.marked.items[old]) {
                forwarding[old] = new_id;
                new_id += 1;
            } else {
                switch (obj) {
                    .instance => |x| heap.gpa.free(x.fields),
                    .array => |x| heap.gpa.free(x.data),
                    .string => |x| heap.gpa.free(x.chars),
                }
                heap.objects.items[old] = null;
            }
        }
    }
    const live = new_id;

    // 3. Rewrite every reference to its object's new id (objects still at old
    //    positions; only dead slots are null).
    remapRoots(f, forwarding);
    for (heap.objects.items) |*maybe| if (maybe.*) |*obj| remapObject(obj, forwarding);
    if (f.budget.pending) |eid| f.budget.pending = forwarding[eid];

    // 4. Move live objects (and their generation/remembered bits) to new slots.
    for (heap.objects.items, 0..) |maybe, oid| {
        if (maybe) |obj| {
            const nid = forwarding[oid];
            heap.objects.items[nid] = obj;
            heap.old.items[nid] = heap.old.items[oid];
            heap.remembered.items[nid] = heap.remembered.items[oid];
        }
    }
    heap.objects.items.len = live;
    heap.marked.items.len = live;
    heap.old.items.len = live;
    heap.remembered.items.len = live;
    heap.free_list.items.len = 0;
}

// --- Generational minor collection (non-moving mark-sweep of the young gen) ---

fn markYoungValue(heap: *Heap, v: Value) void {
    switch (v) {
        .reference => |r| if (r) |id| markYoungObject(heap, id),
        else => {},
    }
}
fn markYoungObject(heap: *Heap, id: u32) void {
    if (heap.old.items[id]) return; // old objects are not collected by a minor GC
    if (heap.marked.items[id]) return;
    heap.marked.items[id] = true;
    switch (heap.get(id).*) {
        .instance => |x| for (x.fields) |v| markYoungValue(heap, v),
        .array => |x| if (x.elem == .reference) for (x.data) |v| markYoungValue(heap, v),
        .string => {},
    }
}
/// Fast, non-moving collection of the young generation. Roots into the young gen
/// come from the frame chain, statics, the pending exception, and the remembered
/// set (old objects that hold references to young objects). Survivors are promoted
/// to the old generation. Since all survivors become old, no young objects remain
/// afterward, so the remembered set is cleared.
fn collectMinor(f: *Frame) void {
    const heap = f.heap orelse return;
    for (heap.marked.items) |*m| m.* = false;
    var fr: ?*Frame = f;
    while (fr) |ff| {
        for (ff.stack[0..ff.sp]) |v| markYoungValue(heap, v);
        for (ff.locals) |v| markYoungValue(heap, v);
        fr = ff.parent;
    }
    for (f.loader.statics.items) |st| for (st) |v| markYoungValue(heap, v);
    if (f.budget.pending) |eid| markYoungObject(heap, eid);
    var id: u32 = 0;
    while (id < heap.objects.items.len) : (id += 1) {
        if (heap.remembered.items[id]) {
            if (heap.objects.items[id]) |obj| switch (obj) {
                .instance => |x| for (x.fields) |v| markYoungValue(heap, v),
                .array => |x| if (x.elem == .reference) for (x.data) |v| markYoungValue(heap, v),
                .string => {},
            };
        }
    }
    id = 0;
    while (id < heap.objects.items.len) : (id += 1) {
        if (heap.objects.items[id]) |obj| {
            if (!heap.old.items[id]) {
                if (heap.marked.items[id]) {
                    heap.old.items[id] = true; // promote survivor
                } else {
                    switch (obj) {
                        .instance => |x| heap.gpa.free(x.fields),
                        .array => |x| heap.gpa.free(x.data),
                        .string => |x| heap.gpa.free(x.chars),
                    }
                    heap.objects.items[id] = null;
                    heap.free_list.append(heap.gpa, id) catch {};
                }
            }
        }
    }
    for (heap.remembered.items) |*r| r.* = false;
}

/// Throw a fresh built-in exception object of class `name`, searching the current
/// frame for a handler. Returns normally if handled (f.pc at the handler); returns
/// error.JavaException to propagate. If the class or heap is unavailable, the
/// internal error propagates unchanged.
fn raise(f: *Frame, class: ?*const Class, exceptions: []const attribute_decode.ExceptionTableEntry, name: []const u8, fallback: RunError) RunError!void {
    const heap = f.heap orelse return fallback;
    const cls = f.loader.find(name) orelse return fallback;
    const eid = heap.allocInstance(cls) catch return error.OutOfMemory;
    if (try handleException(f, class, exceptions, eid)) return;
    f.budget.pending = eid;
    return error.JavaException;
}

/// Map an internal trap error to the corresponding Java exception (thrown via
/// `raise`). Non-trap errors propagate unchanged.
fn mapTrap(f: *Frame, class: ?*const Class, exceptions: []const attribute_decode.ExceptionTableEntry, e: RunError) RunError!void {
    const name = switch (e) {
        error.ArithmeticException => "java/lang/ArithmeticException",
        error.NullPointer => "java/lang/NullPointerException",
        error.ArrayIndexOutOfBounds => "java/lang/ArrayIndexOutOfBoundsException",
        error.NegativeArraySize => "java/lang/NegativeArraySizeException",
        else => return e,
    };
    return raise(f, class, exceptions, name, e);
}

/// Write barrier: record an old object that now references a young object.
fn writeBarrier(heap: *Heap, target_id: u32, v: Value) void {
    switch (v) {
        .reference => |r| if (r) |vid| {
            if (heap.old.items[target_id] and !heap.old.items[vid]) heap.remembered.items[target_id] = true;
        },
        else => {},
    }
}

fn maybeCollect(f: *Frame) void {
    const heap = f.heap orelse return;
    heap.allocs_since_gc += 1;
    if (heap.allocs_since_gc >= heap.gc_interval) {
        heap.allocs_since_gc = 0;
        heap.minor_count += 1;
        if (heap.minors_per_major != 0 and heap.minor_count % heap.minors_per_major == 0) {
            collectMajor(f);
        } else {
            collectMinor(f);
        }
    }
}

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

/// A class registry: name -> Class, with per-class mutable static storage and
/// lazy <clinit>. This is the beginning of a class loader; classes are still
/// preloaded by the caller (no classpath search yet).
pub const Loader = struct {
    gpa: std.mem.Allocator,
    classes: std.ArrayList(*const Class) = .empty,
    statics: std.ArrayList([]Value) = .empty,
    initialized: std.ArrayList(bool) = .empty,

    pub fn init(gpa: std.mem.Allocator) Loader {
        return .{ .gpa = gpa };
    }
    pub fn deinit(self: *Loader) void {
        for (self.statics.items) |st| self.gpa.free(st);
        self.statics.deinit(self.gpa);
        self.classes.deinit(self.gpa);
        self.initialized.deinit(self.gpa);
    }
    pub fn register(self: *Loader, class: *const Class) !void {
        const st = try self.gpa.alloc(Value, class.static_fields.len);
        for (st, class.static_fields) |*sv, sf| sv.* = defaultValue(sf.kind);
        try self.classes.append(self.gpa, class);
        try self.statics.append(self.gpa, st);
        try self.initialized.append(self.gpa, false);
    }
    fn indexOf(self: *const Loader, class: *const Class) ?usize {
        for (self.classes.items, 0..) |c, i| {
            if (c == class) return i;
        }
        return null;
    }
    pub fn find(self: *const Loader, name: []const u8) ?*const Class {
        for (self.classes.items) |c| {
            if (std.mem.eql(u8, c.name, name)) return c;
        }
        return null;
    }
    fn staticsOf(self: *Loader, class: *const Class) RunError![]Value {
        const i = self.indexOf(class) orelse return error.LinkError;
        return self.statics.items[i];
    }
    /// Run <clinit> once for `class` (and register it if unseen).
    fn ensureInit(self: *Loader, class: *const Class, heap: *Heap, budget: *Budget) RunError!void {
        const i = self.indexOf(class) orelse return error.LinkError;
        if (self.initialized.items[i]) return;
        self.initialized.items[i] = true;
        if (class.find("<clinit>", "()V")) |ci| {
            if (ci.code) |cc| {
                _ = try exec(self.gpa, class, heap, self, budget, cc.code, cc.max_stack, cc.max_locals, &.{}, cc.exception_table, null);
            }
        }
    }
};

pub const Class = struct {
    gpa: std.mem.Allocator,
    cp: ConstantPool,
    name: []const u8,
    super: ?*const Class,
    super_name: ?[]const u8,
    interfaces: [][]const u8,
    methods: []Method,
    instance_fields: []Field,
    static_fields: []Field,
    bootstrap_methods: []const attribute_decode.BootstrapMethod,

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

    pub fn init(gpa: std.mem.Allocator, arena: std.mem.Allocator, cf: *const ClassFile, super: ?*const Class) !Class {
        const cls_name = try cf.constant_pool.classNameOf(cf.this_class);
        const super_name: ?[]const u8 = if (cf.super_class != 0) try cf.constant_pool.classNameOf(cf.super_class) else null;
        const interfaces = try arena.alloc([]const u8, cf.interfaces.len);
        for (cf.interfaces, 0..) |ix, i| interfaces[i] = try cf.constant_pool.classNameOf(ix);

        // Instance (non-static) fields, in declaration order.
        var nfields: usize = 0;
        for (cf.fields) |fld| {
            if (!fld.access_flags.isStatic()) nfields += 1;
        }
        const super_fields: []const Field = if (super) |sp| sp.instance_fields else &.{};
        const instance_fields = try arena.alloc(Field, super_fields.len + nfields);
        const static_fields = try arena.alloc(Field, cf.fields.len - nfields);
        for (super_fields, 0..) |sf, i| instance_fields[i] = sf; // inherited fields first
        var fi: usize = super_fields.len;
        var si: usize = 0;
        for (cf.fields) |fld| {
            const fdesc = try cf.constant_pool.utf8(fld.descriptor_index);
            const field = Field{
                .name = try cf.constant_pool.utf8(fld.name_index),
                .kind = kindOf(try descriptor.parseFieldDescriptor(fdesc)),
            };
            if (fld.access_flags.isStatic()) {
                static_fields[si] = field;
                si += 1;
            } else {
                instance_fields[fi] = field;
                fi += 1;
            }
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
        var bootstrap: []const attribute_decode.BootstrapMethod = &.{};
        for (cf.attributes) |ai| {
            if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "BootstrapMethods")) {
                bootstrap = (try attribute_decode.decode(arena, cf.constant_pool, ai)).bootstrap_methods;
            }
        }
        return .{ .gpa = gpa, .cp = cf.constant_pool, .name = cls_name, .super = super, .super_name = super_name, .interfaces = interfaces, .methods = methods, .instance_fields = instance_fields, .static_fields = static_fields, .bootstrap_methods = bootstrap };
    }

    fn findField(self: *const Class, name: []const u8) ?usize {
        for (self.instance_fields, 0..) |fd, i| {
            if (std.mem.eql(u8, fd.name, name)) return i;
        }
        return null;
    }
    fn findStatic(self: *const Class, name: []const u8) ?usize {
        for (self.static_fields, 0..) |fd, i| {
            if (std.mem.eql(u8, fd.name, name)) return i;
        }
        return null;
    }

    fn find(self: *const Class, name: []const u8, desc: []const u8) ?*const Method {
        for (self.methods) |*m| {
            if (std.mem.eql(u8, m.name, name) and std.mem.eql(u8, m.descriptor, desc)) return m;
        }
        if (self.super) |sp| return sp.find(name, desc);
        return null;
    }
    /// Like find, but also returns the class that declares the method (which owns
    /// the code and its constant pool -- essential for inherited methods).
    const Resolved = struct { method: *const Method, owner: *const Class };
    fn resolve(self: *const Class, name: []const u8, desc: []const u8) ?Resolved {
        var c: ?*const Class = self;
        while (c) |cc| {
            for (cc.methods) |*m| {
                if (std.mem.eql(u8, m.name, name) and std.mem.eql(u8, m.descriptor, desc)) return .{ .method = m, .owner = cc };
            }
            c = cc.super;
        }
        return null;
    }

    pub fn callStaticValues(self: *const Class, name: []const u8, desc: []const u8, args: []const Value, budget: *Budget) RunError!?Value {
        var loader = Loader.init(self.gpa);
        defer loader.deinit();
        try loader.register(self);
        return runInLoader(&loader, self, name, desc, args, budget);
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
    maybeCollect(f);
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const tclass = try resolveClass(f, cls, try refClassName(cls, try u16At(code, f.pc + 1)));
    try f.push(.{ .reference = try heap.allocInstance(tclass) });
}

fn doGetField(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const fname = try fieldName(cls, try u16At(code, f.pc + 1));
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const inst = switch (heap.get(oid).*) {
        .instance => |*x| x,
        else => return error.LinkError,
    };
    const fi = inst.class.findField(fname) orelse return error.LinkError;
    try f.push(inst.fields[fi]);
}

fn doPutField(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const fname = try fieldName(cls, try u16At(code, f.pc + 1));
    const value = try f.pop();
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const inst = switch (heap.get(oid).*) {
        .instance => |*x| x,
        else => return error.LinkError,
    };
    const fi = inst.class.findField(fname) orelse return error.LinkError;
    inst.fields[fi] = value;
    writeBarrier(heap, oid, value);
}

fn atypeKind(atype: usize) RunError!Kind {
    return switch (atype) {
        4, 5, 8, 9, 10 => .int, // boolean, char, byte, short, int
        6 => .float,
        7 => .double,
        11 => .long,
        else => error.LinkError,
    };
}
fn doNewArray(f: *Frame, code: []const u8) RunError!void {
    maybeCollect(f);
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const kind = try atypeKind(try u8At(code, f.pc + 1));
    const len = try f.popInt();
    if (len < 0) return error.NegativeArraySize;
    try f.push(.{ .reference = try heap.allocArray(kind, @intCast(len)) });
}
fn doANewArray(f: *Frame, code: []const u8) RunError!void {
    maybeCollect(f);
    const heap = f.heap orelse return error.UnsupportedOpcode;
    _ = try u16At(code, f.pc + 1); // element class ignored; references are uniform here
    const len = try f.popInt();
    if (len < 0) return error.NegativeArraySize;
    try f.push(.{ .reference = try heap.allocArray(.reference, @intCast(len)) });
}
fn buildMulti(heap: *Heap, counts: []const i32, level: usize, inner_kind: Kind) RunError!u32 {
    const len = counts[level];
    if (len < 0) return error.NegativeArraySize;
    const ulen: usize = @intCast(len);
    if (level == counts.len - 1) return heap.allocArray(inner_kind, ulen);
    const id = try heap.allocArray(.reference, ulen);
    var i: usize = 0;
    while (i < ulen) : (i += 1) {
        const child = try buildMulti(heap, counts, level + 1, inner_kind);
        heap.get(id).array.data[i] = .{ .reference = child };
    }
    return id;
}
fn doMultiANewArray(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    maybeCollect(f);
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const cname = try refClassName(cls, try u16At(code, f.pc + 1)); // e.g. "[[I"
    const dims: usize = try u8At(code, f.pc + 3);
    if (dims == 0 or dims > 255 or dims > cname.len) return error.LinkError;
    var counts: [255]i32 = undefined;
    var k: usize = dims;
    while (k > 0) {
        k -= 1;
        counts[k] = try f.popInt(); // stack top is the innermost dimension count
    }
    const rem = cname[dims..];
    const inner_kind: Kind = if (rem.len == 0) .reference else switch (rem[0]) {
        '[', 'L' => .reference,
        'B', 'C', 'S', 'Z', 'I' => .int,
        'J' => .long,
        'F' => .float,
        'D' => .double,
        else => .reference,
    };
    try f.push(.{ .reference = try buildMulti(heap, counts[0..dims], 0, inner_kind) });
}
fn doArrayLength(f: *Frame) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const arr = switch (heap.get(oid).*) {
        .array => |a| a,
        else => return error.LinkError,
    };
    try f.pushInt(@intCast(arr.data.len));
}
fn arrayIndex(f: *Frame) RunError!struct { arr: *Array, i: usize, oid: u32 } {
    const idx = try f.popInt();
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const arr = switch (heap.get(oid).*) {
        .array => |*a| a,
        else => return error.LinkError,
    };
    if (idx < 0) return error.ArrayIndexOutOfBounds;
    const ui: usize = @intCast(idx);
    if (ui >= arr.data.len) return error.ArrayIndexOutOfBounds;
    return .{ .arr = arr, .i = ui, .oid = oid };
}

fn invokeInstance(f: *Frame, cls: *const Class, code: []const u8, is_special: bool) RunError!void {
    const idx = try u16At(code, f.pc + 1);
    const c = cls.cp.get(idx) catch return error.LinkError;
    const ref = switch (c.*) {
        .methodref => |r| r,
        .interface_methodref => |r| r,
        else => return error.LinkError,
    };
    const cname = try refClassName(cls, ref.class_index);
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const mname = cls.cp.utf8(nat.name_index) catch return error.LinkError;
    const mdesc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;

    if (std.mem.eql(u8, cname, "java/lang/String")) return stringIntrinsic(f, mname, mdesc);

    // Bootstrap stub: java/lang/Object.<init> is a no-op (we have no JDK loaded).
    if (is_special and std.mem.eql(u8, mname, "<init>") and std.mem.eql(u8, cname, "java/lang/Object")) {
        _ = (try f.popRef()) orelse return error.NullPointer; // consume `this`
        return;
    }

    // Declared class (from the ref) gives the param layout. Pop args + this.
    const dclass = try resolveClass(f, cls, cname);
    const decl = dclass.find(mname, mdesc) orelse return error.MethodNotFound;
    const slots = try dclass.gpa.alloc(Value, decl.arg_slots);
    defer dclass.gpa.free(slots);
    var i: usize = decl.params.len;
    while (i > 0) {
        i -= 1;
        const p = decl.params[i];
        slots[p.slot] = try f.popKind(p.kind);
        if (p.kind == .long or p.kind == .double) slots[p.slot + 1] = .top;
    }
    const oid = (try f.popRef()) orelse return error.NullPointer;
    slots[0] = .{ .reference = oid };

    // Dispatch: invokespecial uses the declared class; invokevirtual uses the
    // receiver's actual class (single-level: no superclass walk yet).
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const rclass = if (is_special) dclass else switch (heap.get(oid).*) {
        .instance => |x| x.class,
        .string => |x| x.class,
        else => return error.LinkError,
    };
    const tr = rclass.resolve(mname, mdesc) orelse return error.MethodNotFound;
    const target = tr.method;
    const owner = tr.owner;

    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const cc = target.code orelse return error.LinkError;
    const ret = try exec(owner.gpa, owner, f.heap, f.loader, f.budget, cc.code, cc.max_stack, cc.max_locals, slots, cc.exception_table, f);
    if (ret) |rv| try f.pushKind(rv);
}

const FieldRefInfo = struct { class_name: []const u8, field_name: []const u8 };
fn fieldRef(cls: *const Class, cp_index: u16) RunError!FieldRefInfo {
    const c = cls.cp.get(cp_index) catch return error.LinkError;
    const ref = switch (c.*) {
        .fieldref => |r| r,
        else => return error.LinkError,
    };
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    return .{
        .class_name = try refClassName(cls, ref.class_index),
        .field_name = cls.cp.utf8(nat.name_index) catch return error.LinkError,
    };
}

fn resolveClass(f: *Frame, current: *const Class, name: []const u8) RunError!*const Class {
    if (std.mem.eql(u8, name, current.name)) return current;
    return f.loader.find(name) orelse error.LinkError;
}

fn layoutArgs(m: *const Class.Method, args: []const Value, out: []Value) RunError!usize {
    if (args.len != m.params.len) return error.LinkError;
    for (m.params, 0..) |p, i| {
        out[p.slot] = args[i];
        if (p.kind == .long or p.kind == .double) out[p.slot + 1] = .top;
    }
    return m.arg_slots;
}

/// Run a static method by name in a (multi-class) loader. Initializes all
/// registered classes first (eager; lazy init is future work).
pub fn runInLoader(loader: *Loader, class: *const Class, name: []const u8, desc: []const u8, args: []const Value, budget: *Budget) RunError!?Value {
    var heap = Heap{ .gpa = loader.gpa };
    defer heap.deinit();
    return runInLoaderWithHeap(loader, class, name, desc, args, budget, &heap);
}

/// Like runInLoader but with a caller-provided heap (to configure GC / inspect it).
pub fn runInLoaderWithHeap(loader: *Loader, class: *const Class, name: []const u8, desc: []const u8, args: []const Value, budget: *Budget, heap: *Heap) RunError!?Value {
    for (loader.classes.items) |c| try loader.ensureInit(c, heap, budget);
    const rr = class.resolve(name, desc) orelse return error.MethodNotFound;
    const m = rr.method;
    const c = m.code orelse return error.LinkError;
    const slots = try loader.gpa.alloc(Value, m.arg_slots);
    defer loader.gpa.free(slots);
    const n = try layoutArgs(m, args, slots);
    return exec(loader.gpa, rr.owner, heap, loader, budget, c.code, c.max_stack, c.max_locals, slots[0..n], c.exception_table, null);
}

fn eq2(a: []const u8, b: []const u8, x: []const u8, y: []const u8) bool {
    return std.mem.eql(u8, a, x) and std.mem.eql(u8, b, y);
}

/// java.lang.Math intrinsics (the class is not loaded; we compute directly).
fn mathIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    // int
    if (eq2(name, desc, "abs", "(I)I")) return f.pushInt(@intCast(@abs(try f.popInt())));
    if (eq2(name, desc, "max", "(II)I")) {
        const b = try f.popInt();
        const a = try f.popInt();
        return f.pushInt(@max(a, b));
    }
    if (eq2(name, desc, "min", "(II)I")) {
        const b = try f.popInt();
        const a = try f.popInt();
        return f.pushInt(@min(a, b));
    }
    // long
    if (eq2(name, desc, "abs", "(J)J")) return f.pushLong(@intCast(@abs(try f.popLong())));
    if (eq2(name, desc, "max", "(JJ)J")) {
        const b = try f.popLong();
        const a = try f.popLong();
        return f.pushLong(@max(a, b));
    }
    if (eq2(name, desc, "min", "(JJ)J")) {
        const b = try f.popLong();
        const a = try f.popLong();
        return f.pushLong(@min(a, b));
    }
    // double
    if (eq2(name, desc, "abs", "(D)D")) return f.pushDouble(@abs(try f.popDouble()));
    if (eq2(name, desc, "max", "(DD)D")) {
        const b = try f.popDouble();
        const a = try f.popDouble();
        return f.pushDouble(@max(a, b));
    }
    if (eq2(name, desc, "min", "(DD)D")) {
        const b = try f.popDouble();
        const a = try f.popDouble();
        return f.pushDouble(@min(a, b));
    }
    if (eq2(name, desc, "sqrt", "(D)D")) return f.pushDouble(@sqrt(try f.popDouble()));
    if (eq2(name, desc, "cbrt", "(D)D")) return f.pushDouble(std.math.cbrt(try f.popDouble()));
    if (eq2(name, desc, "floor", "(D)D")) return f.pushDouble(@floor(try f.popDouble()));
    if (eq2(name, desc, "ceil", "(D)D")) return f.pushDouble(@ceil(try f.popDouble()));
    if (eq2(name, desc, "exp", "(D)D")) return f.pushDouble(@exp(try f.popDouble()));
    if (eq2(name, desc, "log", "(D)D")) return f.pushDouble(@log(try f.popDouble()));
    if (eq2(name, desc, "sin", "(D)D")) return f.pushDouble(@sin(try f.popDouble()));
    if (eq2(name, desc, "cos", "(D)D")) return f.pushDouble(@cos(try f.popDouble()));
    if (eq2(name, desc, "tan", "(D)D")) return f.pushDouble(@tan(try f.popDouble()));
    if (eq2(name, desc, "pow", "(DD)D")) {
        const b = try f.popDouble();
        const a = try f.popDouble();
        return f.pushDouble(std.math.pow(f64, a, b));
    }
    if (eq2(name, desc, "hypot", "(DD)D")) {
        const b = try f.popDouble();
        const a = try f.popDouble();
        return f.pushDouble(@sqrt(a * a + b * b));
    }
    if (eq2(name, desc, "round", "(D)J")) return f.pushLong(@intFromFloat(@floor((try f.popDouble()) + 0.5)));
    if (eq2(name, desc, "round", "(F)I")) return f.pushInt(@intFromFloat(@floor((try f.popFloat()) + 0.5))); // matches Java for finite values
    if (eq2(name, desc, "signum", "(D)D")) {
        const x = try f.popDouble();
        return f.pushDouble(if (std.math.isNan(x)) x else if (x > 0) @as(f64, 1) else if (x < 0) @as(f64, -1) else x);
    }
    return error.UnsupportedOpcode;
}

fn mutf8ToChars(gpa: std.mem.Allocator, m: []const u8) ![]i32 {
    const utf8 = try mutf8_mod.decodeAlloc(gpa, m);
    defer gpa.free(utf8);
    var out: std.ArrayList(i32) = .empty;
    errdefer out.deinit(gpa);
    const view = std.unicode.Utf8View.initUnchecked(utf8);
    var it = view.iterator();
    while (it.nextCodepoint()) |cp| {
        if (cp <= 0xFFFF) {
            try out.append(gpa, @intCast(cp));
        } else {
            const c = cp - 0x10000;
            try out.append(gpa, @intCast(0xD800 + (c >> 10)));
            try out.append(gpa, @intCast(0xDC00 + (c & 0x3FF)));
        }
    }
    return out.toOwnedSlice(gpa);
}
fn fieldKind(ft: descriptor.FieldType) Kind {
    if (ft.dims > 0) return .reference;
    return switch (ft.kind) {
        .object => .reference,
        .base => |b| switch (b) {
            .long => .long,
            .float => .float,
            .double => .double,
            else => .int,
        },
    };
}
fn appendAscii(out: *std.ArrayList(i32), gpa: std.mem.Allocator, str: []const u8) RunError!void {
    for (str) |c| out.append(gpa, c) catch return error.OutOfMemory;
}
fn appendDecimalInt(out: *std.ArrayList(i32), gpa: std.mem.Allocator, x: i32) RunError!void {
    var buf: [16]u8 = undefined;
    const str = std.fmt.bufPrint(&buf, "{d}", .{x}) catch return error.OutOfMemory;
    try appendAscii(out, gpa, str);
}
fn appendDecimalLong(out: *std.ArrayList(i32), gpa: std.mem.Allocator, x: i64) RunError!void {
    var buf: [24]u8 = undefined;
    const str = std.fmt.bufPrint(&buf, "{d}", .{x}) catch return error.OutOfMemory;
    try appendAscii(out, gpa, str);
}
fn appendArg(out: *std.ArrayList(i32), heap: *Heap, v: Value, ft: descriptor.FieldType) RunError!void {
    const gpa = heap.gpa;
    if (ft.dims > 0) return error.UnsupportedOpcode;
    switch (ft.kind) {
        .base => |b| switch (b) {
            .char => out.append(gpa, v.int) catch return error.OutOfMemory,
            .boolean => try appendAscii(out, gpa, if (v.int != 0) "true" else "false"),
            .byte, .short, .int => try appendDecimalInt(out, gpa, v.int),
            .long => try appendDecimalLong(out, gpa, v.long),
            .float, .double => return error.UnsupportedOpcode, // float/double toString not supported yet
        },
        .object => switch (v) {
            .reference => |r| if (r) |id| switch (heap.get(id).*) {
                .string => |st| out.appendSlice(gpa, st.chars) catch return error.OutOfMemory,
                else => return error.UnsupportedOpcode, // non-String object toString not supported
            } else try appendAscii(out, gpa, "null"),
            else => return error.TypeMismatch,
        },
    }
}
fn appendConstantChars(out: *std.ArrayList(i32), gpa: std.mem.Allocator, cls: *const Class, cp_index: u16) RunError!void {
    switch ((cls.cp.get(cp_index) catch return error.LinkError).*) {
        .string => |si| {
            const chars = mutf8ToChars(gpa, cls.cp.utf8(si) catch return error.LinkError) catch return error.OutOfMemory;
            defer gpa.free(chars);
            out.appendSlice(gpa, chars) catch return error.OutOfMemory;
        },
        .integer => |v| try appendDecimalInt(out, gpa, v),
        else => return error.UnsupportedOpcode,
    }
}
fn doInvokeDynamic(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const idx = try u16At(code, f.pc + 1);
    const dyn = switch ((cls.cp.get(idx) catch return error.LinkError).*) {
        .invoke_dynamic => |d| d,
        else => return error.LinkError,
    };
    const nat = switch ((cls.cp.get(dyn.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const desc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;
    if (dyn.bootstrap_method_attr_index >= cls.bootstrap_methods.len) return error.LinkError;
    const bm = cls.bootstrap_methods[dyn.bootstrap_method_attr_index];
    const mh = switch ((cls.cp.get(bm.bootstrap_method_ref) catch return error.LinkError).*) {
        .method_handle => |x| x,
        else => return error.LinkError,
    };
    const bref = switch ((cls.cp.get(mh.reference_index) catch return error.LinkError).*) {
        .methodref => |r| r,
        .interface_methodref => |r| r,
        else => return error.LinkError,
    };
    const bclass = try refClassName(cls, bref.class_index);
    const bnat = switch ((cls.cp.get(bref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const bname = cls.cp.utf8(bnat.name_index) catch return error.LinkError;

    if (std.mem.eql(u8, bclass, "java/lang/invoke/StringConcatFactory")) {
        return doStringConcat(f, cls, desc, bm, std.mem.eql(u8, bname, "makeConcatWithConstants"));
    }
    return error.UnsupportedOpcode;
}
fn doStringConcat(f: *Frame, cls: *const Class, desc: []const u8, bm: attribute_decode.BootstrapMethod, with_constants: bool) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const mt = descriptor.parseMethodDescriptor(heap.gpa, desc) catch return error.LinkError;
    defer heap.gpa.free(mt.params);
    const n = mt.params.len;
    if (n > 64) return error.LinkError;
    var argvals: [64]Value = undefined;
    var i: usize = n;
    while (i > 0) {
        i -= 1;
        argvals[i] = try f.popKind(fieldKind(mt.params[i]));
    }
    var out: std.ArrayList(i32) = .empty;
    defer out.deinit(heap.gpa);
    if (with_constants) {
        const recipe_utf8 = switch ((cls.cp.get(bm.arguments[0]) catch return error.LinkError).*) {
            .string => |si| cls.cp.utf8(si) catch return error.LinkError,
            else => return error.LinkError,
        };
        const recipe = mutf8ToChars(heap.gpa, recipe_utf8) catch return error.OutOfMemory;
        defer heap.gpa.free(recipe);
        var ai: usize = 0;
        var ci: usize = 1;
        for (recipe) |rc| {
            if (rc == 1) {
                if (ai >= n) return error.LinkError;
                try appendArg(&out, heap, argvals[ai], mt.params[ai]);
                ai += 1;
            } else if (rc == 2) {
                if (ci >= bm.arguments.len) return error.LinkError;
                try appendConstantChars(&out, heap.gpa, cls, bm.arguments[ci]);
                ci += 1;
            } else {
                out.append(heap.gpa, rc) catch return error.OutOfMemory;
            }
        }
    } else {
        var ai: usize = 0;
        while (ai < n) : (ai += 1) try appendArg(&out, heap, argvals[ai], mt.params[ai]);
    }
    try f.push(.{ .reference = try newString(f, out.items) });
}

fn createString(f: *Frame, mutf8_bytes: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
    const chars = mutf8ToChars(heap.gpa, mutf8_bytes) catch return error.OutOfMemory;
    try f.push(.{ .reference = try heap.putString(str_class, chars) });
}
fn strChars(heap: *Heap, id: u32) RunError![]i32 {
    return switch (heap.get(id).*) {
        .string => |s| s.chars,
        else => error.LinkError,
    };
}
fn stringIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    if (eq2(name, desc, "length", "()I")) return f.pushInt(@intCast((try strChars(heap, (try f.popRef()) orelse return error.NullPointer)).len));
    if (eq2(name, desc, "isEmpty", "()Z")) return f.pushInt(if ((try strChars(heap, (try f.popRef()) orelse return error.NullPointer)).len == 0) 1 else 0);
    if (eq2(name, desc, "charAt", "(I)C")) {
        const idx = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        if (idx < 0 or idx >= s.len) return error.ArrayIndexOutOfBounds;
        return f.pushInt(s[@intCast(idx)]);
    }
    if (eq2(name, desc, "hashCode", "()I")) {
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var h: i32 = 0;
        for (s) |c| h = 31 *% h +% c;
        return f.pushInt(h);
    }
    if (eq2(name, desc, "equals", "(Ljava/lang/Object;)Z")) {
        const other = try f.popRef();
        const self_chars = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var result: i32 = 0;
        if (other) |oid| switch (heap.get(oid).*) {
            .string => |os| if (std.mem.eql(i32, self_chars, os.chars)) {
                result = 1;
            },
            else => {},
        };
        return f.pushInt(result);
    }
    if (eq2(name, desc, "compareTo", "(Ljava/lang/String;)I")) {
        const other = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const self_chars = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const n = @min(self_chars.len, other.len);
        var i: usize = 0;
        while (i < n) : (i += 1) {
            if (self_chars[i] != other[i]) return f.pushInt(self_chars[i] - other[i]);
        }
        return f.pushInt(@as(i32, @intCast(self_chars.len)) - @as(i32, @intCast(other.len)));
    }
    if (eq2(name, desc, "substring", "(II)Ljava/lang/String;")) {
        const end = try f.popInt();
        const begin = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        if (begin < 0 or end > s.len or begin > end) return error.ArrayIndexOutOfBounds;
        return f.push(.{ .reference = try newString(f, s[@intCast(begin)..@intCast(end)]) });
    }
    if (eq2(name, desc, "substring", "(I)Ljava/lang/String;")) {
        const begin = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        if (begin < 0 or begin > s.len) return error.ArrayIndexOutOfBounds;
        return f.push(.{ .reference = try newString(f, s[@intCast(begin)..]) });
    }
    if (eq2(name, desc, "indexOf", "(I)I")) {
        const ch = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        for (s, 0..) |c, i| if (c == ch) return f.pushInt(@intCast(i));
        return f.pushInt(-1);
    }
    if (eq2(name, desc, "indexOf", "(Ljava/lang/String;)I")) {
        const needle = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        return f.pushInt(indexOfSub(s, needle));
    }
    if (eq2(name, desc, "startsWith", "(Ljava/lang/String;)Z")) {
        const pre = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        return f.pushInt(if (pre.len <= s.len and std.mem.eql(i32, s[0..pre.len], pre)) 1 else 0);
    }
    if (eq2(name, desc, "endsWith", "(Ljava/lang/String;)Z")) {
        const suf = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        return f.pushInt(if (suf.len <= s.len and std.mem.eql(i32, s[s.len - suf.len ..], suf)) 1 else 0);
    }
    if (eq2(name, desc, "contains", "(Ljava/lang/CharSequence;)Z")) {
        const needle = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        return f.pushInt(if (indexOfSub(s, needle) >= 0) 1 else 0);
    }
    if (eq2(name, desc, "concat", "(Ljava/lang/String;)Ljava/lang/String;")) {
        const other = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const buf = heap.gpa.alloc(i32, s.len + other.len) catch return error.OutOfMemory;
        @memcpy(buf[0..s.len], s);
        @memcpy(buf[s.len..], other);
        const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
        return f.push(.{ .reference = try heap.putString(str_class, buf) });
    }
    if (eq2(name, desc, "replace", "(CC)Ljava/lang/String;")) {
        const newc = try f.popInt();
        const oldc = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const buf = heap.gpa.dupe(i32, s) catch return error.OutOfMemory;
        for (buf) |*c| {
            if (c.* == oldc) c.* = newc;
        }
        const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
        return f.push(.{ .reference = try heap.putString(str_class, buf) });
    }
    return error.UnsupportedOpcode;
}

fn indexOfSub(s: []const i32, needle: []const i32) i32 {
    if (needle.len == 0) return 0;
    if (needle.len > s.len) return -1;
    var i: usize = 0;
    while (i + needle.len <= s.len) : (i += 1) {
        if (std.mem.eql(i32, s[i .. i + needle.len], needle)) return @intCast(i);
    }
    return -1;
}
fn newString(f: *Frame, chars: []const i32) RunError!u32 {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
    const dup = heap.gpa.dupe(i32, chars) catch return error.OutOfMemory;
    return heap.putString(str_class, dup);
}

fn lessThanInt(_: void, a: Value, b: Value) bool {
    return a.int < b.int;
}
fn lessThanLong(_: void, a: Value, b: Value) bool {
    return a.long < b.long;
}
fn arrayOf(f: *Frame, id: u32) RunError!*Array {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    return switch (heap.get(id).*) {
        .array => |*a| a,
        else => error.LinkError,
    };
}
fn arraysIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    if (eq2(name, desc, "sort", "([I)V")) {
        const arr = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        std.sort.pdq(Value, arr.data, {}, lessThanInt);
        return;
    }
    if (eq2(name, desc, "sort", "([J)V")) {
        const arr = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        std.sort.pdq(Value, arr.data, {}, lessThanLong);
        return;
    }
    if (eq2(name, desc, "fill", "([II)V")) {
        const val = try f.popInt();
        const arr = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        for (arr.data) |*v| v.* = .{ .int = val };
        return;
    }
    if (eq2(name, desc, "copyOf", "([II)[I")) {
        const new_len = try f.popInt();
        const src = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        if (new_len < 0) return error.NegativeArraySize;
        const heap = f.heap orelse return error.UnsupportedOpcode;
        const nlen: usize = @intCast(new_len);
        const id = try heap.allocArray(.int, nlen);
        const dst = try arrayOf(f, id);
        const copy = @min(nlen, src.data.len);
        var i: usize = 0;
        while (i < copy) : (i += 1) dst.data[i] = src.data[i];
        return f.push(.{ .reference = id });
    }
    if (eq2(name, desc, "equals", "([I[I)Z")) {
        const b = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        const a = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        var eq = a.data.len == b.data.len;
        if (eq) for (a.data, b.data) |x, y| {
            if (x.int != y.int) {
                eq = false;
                break;
            }
        };
        return f.pushInt(if (eq) 1 else 0);
    }
    return error.UnsupportedOpcode;
}

fn integerIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    if (eq2(name, desc, "bitCount", "(I)I")) return f.pushInt(@popCount(try f.popInt()));
    if (eq2(name, desc, "numberOfLeadingZeros", "(I)I")) return f.pushInt(@clz(@as(u32, @bitCast(try f.popInt()))));
    if (eq2(name, desc, "numberOfTrailingZeros", "(I)I")) return f.pushInt(@ctz(@as(u32, @bitCast(try f.popInt()))));
    if (eq2(name, desc, "reverse", "(I)I")) return f.pushInt(@bitReverse(try f.popInt()));
    if (eq2(name, desc, "reverseBytes", "(I)I")) return f.pushInt(@byteSwap(try f.popInt()));
    if (eq2(name, desc, "highestOneBit", "(I)I")) {
        const x = @as(u32, @bitCast(try f.popInt()));
        const r: u32 = if (x == 0) 0 else @as(u32, 1) << @intCast(31 - @clz(x));
        return f.pushInt(@bitCast(r));
    }
    if (eq2(name, desc, "lowestOneBit", "(I)I")) {
        const x = try f.popInt();
        return f.pushInt(x & (0 -% x));
    }
    if (eq2(name, desc, "rotateLeft", "(II)I")) {
        const d = try f.popInt();
        const x = @as(u32, @bitCast(try f.popInt()));
        return f.pushInt(@bitCast(std.math.rotl(u32, x, d)));
    }
    if (eq2(name, desc, "rotateRight", "(II)I")) {
        const d = try f.popInt();
        const x = @as(u32, @bitCast(try f.popInt()));
        return f.pushInt(@bitCast(std.math.rotr(u32, x, d)));
    }
    if (eq2(name, desc, "max", "(II)I")) {
        const b = try f.popInt();
        const a = try f.popInt();
        return f.pushInt(@max(a, b));
    }
    if (eq2(name, desc, "min", "(II)I")) {
        const b = try f.popInt();
        const a = try f.popInt();
        return f.pushInt(@min(a, b));
    }
    if (eq2(name, desc, "signum", "(I)I")) {
        const x = try f.popInt();
        return f.pushInt(if (x > 0) @as(i32, 1) else if (x < 0) @as(i32, -1) else 0);
    }
    return error.UnsupportedOpcode;
}
fn longIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    if (eq2(name, desc, "bitCount", "(J)I")) return f.pushInt(@popCount(try f.popLong()));
    if (eq2(name, desc, "numberOfLeadingZeros", "(J)I")) return f.pushInt(@clz(@as(u64, @bitCast(try f.popLong()))));
    if (eq2(name, desc, "numberOfTrailingZeros", "(J)I")) return f.pushInt(@ctz(@as(u64, @bitCast(try f.popLong()))));
    if (eq2(name, desc, "reverse", "(J)J")) return f.pushLong(@bitReverse(try f.popLong()));
    if (eq2(name, desc, "reverseBytes", "(J)J")) return f.pushLong(@byteSwap(try f.popLong()));
    if (eq2(name, desc, "max", "(JJ)J")) {
        const b = try f.popLong();
        const a = try f.popLong();
        return f.pushLong(@max(a, b));
    }
    if (eq2(name, desc, "min", "(JJ)J")) {
        const b = try f.popLong();
        const a = try f.popLong();
        return f.pushLong(@min(a, b));
    }
    return error.UnsupportedOpcode;
}

fn systemIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    if (eq2(name, desc, "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")) {
        const length = try f.popInt();
        const dest_pos = try f.popInt();
        const dest = (try f.popRef()) orelse return error.NullPointer;
        const src_pos = try f.popInt();
        const src = (try f.popRef()) orelse return error.NullPointer;
        const heap = f.heap orelse return error.UnsupportedOpcode;
        const sa = switch (heap.get(src).*) {
            .array => |*a| a,
            else => return error.LinkError,
        };
        const da = switch (heap.get(dest).*) {
            .array => |*a| a,
            else => return error.LinkError,
        };
        if (length < 0 or src_pos < 0 or dest_pos < 0) return error.ArrayIndexOutOfBounds;
        const len: usize = @intCast(length);
        const sp: usize = @intCast(src_pos);
        const dp: usize = @intCast(dest_pos);
        if (sp + len > sa.data.len or dp + len > da.data.len) return error.ArrayIndexOutOfBounds;
        // memmove semantics (handle overlap)
        if (dp <= sp) {
            var i: usize = 0;
            while (i < len) : (i += 1) da.data[dp + i] = sa.data[sp + i];
        } else {
            var i: usize = len;
            while (i > 0) {
                i -= 1;
                da.data[dp + i] = sa.data[sp + i];
            }
        }
        if (da.elem == .reference) {
            for (da.data[dp .. dp + len]) |v| writeBarrier(heap, dest, v);
        }
        return;
    }
    return error.UnsupportedOpcode;
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
    const owner_name = try refClassName(cls, ref.class_index);
    if (std.mem.eql(u8, owner_name, "java/lang/Math")) return mathIntrinsic(f, mname, mdesc);
    if (std.mem.eql(u8, owner_name, "java/lang/System")) return systemIntrinsic(f, mname, mdesc);
    if (std.mem.eql(u8, owner_name, "java/lang/Integer")) return integerIntrinsic(f, mname, mdesc);
    if (std.mem.eql(u8, owner_name, "java/lang/Long")) return longIntrinsic(f, mname, mdesc);
    if (std.mem.eql(u8, owner_name, "java/util/Arrays")) return arraysIntrinsic(f, mname, mdesc);
    const tclass = try resolveClass(f, cls, try refClassName(cls, ref.class_index));
    const tr = tclass.resolve(mname, mdesc) orelse return error.MethodNotFound;
    const target = tr.method;
    const owner = tr.owner;

    const slots = try owner.gpa.alloc(Value, target.arg_slots);
    defer owner.gpa.free(slots);
    var i: usize = target.params.len;
    while (i > 0) {
        i -= 1;
        const p = target.params[i];
        slots[p.slot] = try f.popKind(p.kind);
        if (p.kind == .long or p.kind == .double) slots[p.slot + 1] = .top;
    }

    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const c = target.code orelse return error.LinkError;
    const ret = try exec(owner.gpa, owner, f.heap, f.loader, f.budget, c.code, c.max_stack, c.max_locals, slots, c.exception_table, f);
    if (ret) |rv| try f.pushKind(rv);
}

fn loadConstant(f: *Frame, class: ?*const Class, index: u16) RunError!void {
    const cls = class orelse return error.UnsupportedOpcode;
    switch ((cls.cp.get(index) catch return error.LinkError).*) {
        .integer => |v| try f.pushInt(v),
        .float => |v| try f.pushFloat(v),
        .string => |si| try createString(f, cls.cp.utf8(si) catch return error.LinkError),
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

fn isInstanceOf(cls: *const Class, target_name: []const u8) bool {
    var c: ?*const Class = cls;
    while (c) |cc| {
        if (std.mem.eql(u8, cc.name, target_name)) return true;
        for (cc.interfaces) |iface| if (std.mem.eql(u8, iface, target_name)) return true;
        c = cc.super;
    }
    return false;
}

/// Search `exceptions` for a handler covering f.pc that catches `exc_id`. On a
/// match, clears the operand stack, pushes the exception, jumps to the handler,
/// and returns true. Otherwise returns false (caller propagates).
fn handleException(f: *Frame, class: ?*const Class, exceptions: []const attribute_decode.ExceptionTableEntry, exc_id: u32) RunError!bool {
    const cls = class orelse return false;
    const heap = f.heap orelse return false;
    const exc_class = switch (heap.get(exc_id).*) {
        .instance => |x| x.class,
        else => return error.LinkError,
    };
    for (exceptions) |e| {
        if (f.pc < e.start_pc or f.pc >= e.end_pc) continue;
        const matches = if (e.catch_type == 0) true else blk: {
            const cn = cls.cp.classNameOf(e.catch_type) catch break :blk false;
            break :blk isInstanceOf(exc_class, cn);
        };
        if (matches) {
            f.sp = 0;
            try f.push(.{ .reference = exc_id });
            f.pc = e.handler_pc;
            return true;
        }
    }
    return false;
}

fn exec(alloc: std.mem.Allocator, class: ?*const Class, heap: ?*Heap, loader: *Loader, budget: *Budget, code: []const u8, max_stack: u16, max_locals: u16, arg_slots: []const Value, exceptions: []const attribute_decode.ExceptionTableEntry, parent: ?*Frame) RunError!?Value {
    if (arg_slots.len > max_locals) return error.BadLocal;
    const stack = try alloc.alloc(Value, max_stack);
    defer alloc.free(stack);
    const locals = try alloc.alloc(Value, max_locals);
    defer alloc.free(locals);
    for (locals) |*l| l.* = .{ .int = 0 };
    for (arg_slots, 0..) |v, i| locals[i] = v;

    var f = Frame{ .stack = stack, .locals = locals, .budget = budget, .heap = heap, .loader = loader, .parent = parent };

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
            const res = intBinary(o, x, y) catch |e| {
                if (e == error.ArithmeticException) {
                    try raise(&f, class, exceptions, "java/lang/ArithmeticException", e);
                    continue :sw try step(&f, code);
                }
                return e;
            };
            try f.pushInt(res);
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
            const res = longBinary(o, x, y) catch |e| {
                if (e == error.ArithmeticException) {
                    try raise(&f, class, exceptions, "java/lang/ArithmeticException", e);
                    continue :sw try step(&f, code);
                }
                return e;
            };
            try f.pushLong(res);
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
        .ifnull, .ifnonnull => |o| {
            const r = try f.popRef();
            const take = if (o == .ifnull) (r == null) else (r != null);
            if (take) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(&f, code);
        },
        .if_acmpeq, .if_acmpne => |o| {
            const b = try f.popRef();
            const a2 = try f.popRef();
            const eq = (a2 == null and b == null) or (a2 != null and b != null and a2.? == b.?);
            const take = if (o == .if_acmpeq) eq else !eq;
            if (take) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
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
        .newarray => {
            doNewArray(&f, code) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 2;
            continue :sw try step(&f, code);
        },
        .anewarray => {
            doANewArray(&f, code) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .multianewarray => {
            const cls = class orelse return error.UnsupportedOpcode;
            try doMultiANewArray(&f, cls, code);
            f.pc += 4;
            continue :sw try step(&f, code);
        },
        .arraylength => {
            doArrayLength(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .iaload, .baload, .caload, .saload => {
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            try f.pushInt(ai.arr.data[ai.i].int);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .laload => {
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            try f.pushLong(ai.arr.data[ai.i].long);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .faload => {
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            try f.pushFloat(ai.arr.data[ai.i].float);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .daload => {
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            try f.pushDouble(ai.arr.data[ai.i].double);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .aaload => {
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            try f.push(ai.arr.data[ai.i]);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .iastore => {
            const v = try f.popInt();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .int = v };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .bastore => {
            const v = try f.popInt();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .int = @as(i8, @truncate(v)) };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .castore => {
            const v = try f.popInt();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .int = @as(u16, @truncate(@as(u32, @bitCast(v)))) };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .sastore => {
            const v = try f.popInt();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .int = @as(i16, @truncate(v)) };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .lastore => {
            const v = try f.popLong();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .long = v };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .fastore => {
            const v = try f.popFloat();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .float = v };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .dastore => {
            const v = try f.popDouble();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = .{ .double = v };
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .aastore => {
            const v = try f.pop();
            const ai = arrayIndex(&f) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            ai.arr.data[ai.i] = v;
            if (f.heap) |hp| writeBarrier(hp, ai.oid, v);
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .new => {
            const cls = class orelse return error.UnsupportedOpcode;
            try doNew(&f, cls, code);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .getfield => {
            const cls = class orelse return error.UnsupportedOpcode;
            doGetField(&f, cls, code) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .putfield => {
            const cls = class orelse return error.UnsupportedOpcode;
            doPutField(&f, cls, code) catch |e| {
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokespecial => {
            const cls = class orelse return error.UnsupportedOpcode;
            invokeInstance(&f, cls, code, true) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(&f, class, exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(&f, code);
                    }
                    return e;
                }
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokevirtual => {
            const cls = class orelse return error.UnsupportedOpcode;
            invokeInstance(&f, cls, code, false) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(&f, class, exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(&f, code);
                    }
                    return e;
                }
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokeinterface => {
            const cls = class orelse return error.UnsupportedOpcode;
            invokeInstance(&f, cls, code, false) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(&f, class, exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(&f, code);
                    }
                    return e;
                }
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 5;
            continue :sw try step(&f, code);
        },
        .invokedynamic => {
            const cls = class orelse return error.UnsupportedOpcode;
            doInvokeDynamic(&f, cls, code) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(&f, class, exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(&f, code);
                    }
                    return e;
                }
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 5;
            continue :sw try step(&f, code);
        },
        .instanceof => {
            const cls = class orelse return error.UnsupportedOpcode;
            const hp = f.heap orelse return error.UnsupportedOpcode;
            const target = try refClassName(cls, try u16At(code, f.pc + 1));
            const r = try f.popRef();
            var result: i32 = 0;
            if (r) |id| switch (hp.get(id).*) {
                .instance => |x| result = if (isInstanceOf(x.class, target)) 1 else 0,
                .string => |x| result = if (isInstanceOf(x.class, target)) 1 else 0,
                .array => {}, // array instanceof: not modeled -> 0
            };
            try f.pushInt(result);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .checkcast => {
            const cls = class orelse return error.UnsupportedOpcode;
            const hp = f.heap orelse return error.UnsupportedOpcode;
            const target = try refClassName(cls, try u16At(code, f.pc + 1));
            const r = try f.popRef();
            if (r) |id| switch (hp.get(id).*) {
                .instance => |x| if (!isInstanceOf(x.class, target)) return error.LinkError, // ClassCastException (no JDK class yet)
                .string => |x| if (!isInstanceOf(x.class, target)) return error.LinkError,
                .array => {},
            };
            try f.push(.{ .reference = r });
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .monitorenter, .monitorexit => {
            _ = (try f.popRef()) orelse return error.NullPointer; // single-threaded: null-check only
            f.pc += 1;
            continue :sw try step(&f, code);
        },
        .getstatic => {
            const cls = class orelse return error.UnsupportedOpcode;
            const fr = try fieldRef(cls, try u16At(code, f.pc + 1));
            const dcls = try resolveClass(&f, cls, fr.class_name);
            const si = dcls.findStatic(fr.field_name) orelse return error.LinkError;
            try f.pushKind((try f.loader.staticsOf(dcls))[si]);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .putstatic => {
            const cls = class orelse return error.UnsupportedOpcode;
            const fr = try fieldRef(cls, try u16At(code, f.pc + 1));
            const dcls = try resolveClass(&f, cls, fr.class_name);
            const si = dcls.findStatic(fr.field_name) orelse return error.LinkError;
            const kind = dcls.static_fields[si].kind;
            (try f.loader.staticsOf(dcls))[si] = try f.popKind(kind);
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .invokestatic => {
            const cls = class orelse return error.UnsupportedOpcode;
            invokeStatic(&f, cls, code) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(&f, class, exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(&f, code);
                    }
                    return e;
                }
                try mapTrap(&f, class, exceptions, e);
                continue :sw try step(&f, code);
            };
            f.pc += 3;
            continue :sw try step(&f, code);
        },
        .athrow => {
            const eid = (try f.popRef()) orelse return error.NullPointer;
            if (try handleException(&f, class, exceptions, eid)) continue :sw try step(&f, code);
            f.budget.pending = eid;
            return error.JavaException;
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
    var loader = Loader.init(a);
    defer loader.deinit();
    var buf: [64]Value = undefined;
    if (args.len > buf.len) return error.BadLocal;
    for (args, 0..) |x, i| buf[i] = .{ .int = x };
    const r = try exec(a, null, null, &loader, &b, code, max_stack, max_locals, buf[0..args.len], &.{}, null);
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
    return Class.init(testing.allocator, arena.allocator(), cf, null);
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

test "arrays: sumSquares, firstLast, length, long array, byte truncation" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Arr.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    // sum of i*i for i in 0..9 = 285
    try testing.expectEqual(Value{ .int = 285 }, (try cls.callStatic("sumSquares", "(I)I", &.{10})).?);
    try testing.expectEqual(Value{ .int = 30 }, (try cls.callStatic("firstLast", "(I)I", &.{5})).?);
    try testing.expectEqual(Value{ .int = 7 }, (try cls.callStatic("len", "(I)I", &.{7})).?);
    try testing.expectEqual(Value{ .long = 1000000000001 }, (try cls.callStatic("larr", "()J", &.{})).?);
    // (byte)200 = -56, + 5 = -51
    try testing.expectEqual(Value{ .int = -51 }, (try cls.callStatic("bytes", "()I", &.{})).?);
}

test "arrays: index out of bounds and negative size are trapped" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Arr.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    // firstLast(0): new int[0], then a[0]=10 -> out of bounds
    try testing.expectError(error.ArrayIndexOutOfBounds, cls.callStatic("firstLast", "(I)I", &.{0}));
    // len(-1): negative array size
    try testing.expectError(error.NegativeArraySize, cls.callStatic("len", "(I)I", &.{-1}));
}

test "integration: bubble sort agrees with linear max, and sorts correctly" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Algo.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    // For many seeds/sizes, the sorted last element equals the independent max,
    // and the array is verified sorted. A broad workout of arrays+loops+calls.
    const seeds = [_]i32{ 1, 7, 42, 12345, 999, -3, 2147483, 88 };
    const sizes = [_]i32{ 1, 2, 5, 16, 40 };
    for (seeds) |seed| {
        for (sizes) |n| {
            const bmax = (try cls.callStatic("bubbleMax", "(II)I", &.{ seed, n })).?.int;
            const lmax = (try cls.callStatic("linearMax", "(II)I", &.{ seed, n })).?.int;
            try testing.expectEqual(lmax, bmax);
            try testing.expectEqual(Value{ .int = 1 }, (try cls.callStatic("isSorted", "(II)Z", &.{ seed, n })).?);
        }
    }
}

test "static fields: getstatic/putstatic and <clinit>" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Stat.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();
    // base initialized by <clinit> to 10
    try testing.expectEqual(Value{ .int = 10 }, (try cls.callStatic("getBase", "()I", &.{})).?);
    // counter defaults 0; addAndGet(7) -> 7
    try testing.expectEqual(Value{ .int = 7 }, (try cls.callStatic("addAndGet", "(I)I", &.{7})).?);
    // within one run, static state persists: 0 -> 5 -> 10
    try testing.expectEqual(Value{ .int = 10 }, (try cls.callStatic("bumpTwice", "()I", &.{})).?);
    // long static initialized by <clinit> to 1000; addTotal(234) -> 1234
    var b = Budget{};
    try testing.expectEqual(Value{ .long = 1234 }, (try cls.callStaticValues("addTotal", "(J)J", &.{.{ .long = 234 }}, &b)).?);
}

test "object-capable interpreter survives arbitrary bytecode (heap + class context)" {
    var cf: ClassFile = undefined;
    var arena: std.heap.ArenaAllocator = undefined;
    const cls = try loadClass("testdata/Point.class", &cf, &arena);
    defer cf.deinit();
    defer arena.deinit();

    var prng = std.Random.DefaultPrng.init(0x0B7EC7_FEED);
    const rand = prng.random();
    var buf: [128]u8 = undefined;
    var i: usize = 0;
    while (i < 20000) : (i += 1) {
        const len = rand.intRangeAtMost(usize, 0, buf.len);
        rand.bytes(buf[0..len]);
        const ms = rand.intRangeAtMost(u16, 0, 16);
        const ml = rand.intRangeAtMost(u16, 0, 8);
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 4 }; // GC aggressively
        defer heap.deinit();
        var loader = Loader.init(testing.allocator);
        defer loader.deinit();
        try loader.register(&cls);
        var b = Budget{ .max_steps = 3000 };
        // Random bytecode against a real class + heap. Any error is fine; a crash,
        // leak, or out-of-bounds is not. References cannot be forged from ints, so
        // heap access stays valid.
        _ = exec(testing.allocator, &cls, &heap, &loader, &b, buf[0..len], ms, ml, &.{}, &.{}, null) catch {};
    }
}

test "multi-class: A references B (static call, static field, new, instance method)" {
    const bytes_a = @embedFile("testdata/A.class");
    const bytes_b = @embedFile("testdata/B.class");
    var cfa = try ClassFile.parse(testing.allocator, bytes_a);
    defer cfa.deinit();
    var cfb = try ClassFile.parse(testing.allocator, bytes_b);
    defer cfb.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const clsA = try Class.init(testing.allocator, aa.allocator(), &cfa, null);
    const clsB = try Class.init(testing.allocator, aa.allocator(), &cfb, null);

    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    try loader.register(&clsA);
    try loader.register(&clsB);

    var b = Budget{};
    // A.useSquares(3) = B.square(3) + B.square(4) = 9 + 16 = 25  (cross-class invokestatic)
    try testing.expectEqual(Value{ .int = 25 }, (try runInLoader(&loader, &clsA, "useSquares", "(I)I", &.{.{ .int = 3 }}, &b)).?);
    // A.makeAndGet(42): new B(42).get() = 42  (cross-class new + <init> + invokevirtual)
    b = Budget{};
    try testing.expectEqual(Value{ .int = 42 }, (try runInLoader(&loader, &clsA, "makeAndGet", "(I)I", &.{.{ .int = 42 }}, &b)).?);
    // A.viaCounter(3,4): B.counter += 3 (=3), then += 4 (=7); returns 7  (cross-class static field)
    b = Budget{};
    try testing.expectEqual(Value{ .int = 7 }, (try runInLoader(&loader, &clsA, "viaCounter", "(II)I", &.{ .{ .int = 3 }, .{ .int = 4 } }, &b)).?);
}

test "inheritance: fields, super() ctor, override dispatch, super.method()" {
    const ba = @embedFile("testdata/Animal.class");
    const bd = @embedFile("testdata/Dog.class");
    var cfa = try ClassFile.parse(testing.allocator, ba);
    defer cfa.deinit();
    var cfd = try ClassFile.parse(testing.allocator, bd);
    defer cfd.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const animal = try Class.init(testing.allocator, aa.allocator(), &cfa, null);
    const dog = try Class.init(testing.allocator, aa.allocator(), &cfd, &animal); // Dog extends Animal

    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    try loader.register(&animal);
    try loader.register(&dog);

    // viaDog(): new Dog() -> super(4) sets legs=4, tail=1; describe() =
    //   super.describe() [= legs()*10, legs() virtual -> Dog.legs()=4 -> 40] + tail(1) = 41
    var b = Budget{};
    try testing.expectEqual(Value{ .int = 41 }, (try runInLoader(&loader, &dog, "viaDog", "()I", &.{}, &b)).?);
    // viaAnimalRef(): Animal a = new Dog(); a.describe() [virtual -> Dog.describe()=41] + a.legs() [virtual -> Dog.legs()=4] = 45
    b = Budget{};
    try testing.expectEqual(Value{ .int = 45 }, (try runInLoader(&loader, &dog, "viaAnimalRef", "()I", &.{}, &b)).?);
}

test "interfaces: invokeinterface dispatches on the concrete class" {
    const bs = @embedFile("testdata/Shape.class");
    const bq = @embedFile("testdata/Square.class");
    const bu = @embedFile("testdata/Uses.class");
    var cfs = try ClassFile.parse(testing.allocator, bs);
    defer cfs.deinit();
    var cfq = try ClassFile.parse(testing.allocator, bq);
    defer cfq.deinit();
    var cfu = try ClassFile.parse(testing.allocator, bu);
    defer cfu.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const shape = try Class.init(testing.allocator, aa.allocator(), &cfs, null);
    const square = try Class.init(testing.allocator, aa.allocator(), &cfq, null);
    const uses = try Class.init(testing.allocator, aa.allocator(), &cfu, null);

    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    try loader.register(&shape);
    try loader.register(&square);
    try loader.register(&uses);

    var b = Budget{};
    // sumAreas(3,4) = 3*3 + 4*4 = 25, both via invokeinterface Shape.area()
    try testing.expectEqual(Value{ .int = 25 }, (try runInLoader(&loader, &uses, "sumAreas", "(II)I", &.{ .{ .int = 3 }, .{ .int = 4 } }, &b)).?);
}

pub fn makeStub(gpa: std.mem.Allocator, arena: std.mem.Allocator, name: []const u8, super_name: ?[]const u8, super: ?*const Class) !Class {
    const methods = try arena.alloc(Class.Method, 1);
    methods[0] = .{
        .name = "<init>",
        .descriptor = "()V",
        .code = .{ .max_stack = 0, .max_locals = 1, .code = &stub_return_code, .exception_table = &.{}, .attributes = &.{} },
        .params = &.{},
        .arg_slots = 1,
        .ret = null,
        .is_static = false,
    };
    return Class{
        .gpa = gpa,
        .cp = .{ .entries = &stub_cp_entries },
        .name = name,
        .super = super,
        .super_name = super_name,
        .interfaces = &.{},
        .methods = methods,
        .instance_fields = &.{},
        .static_fields = &.{},
        .bootstrap_methods = &.{},
    };
}

test "exceptions: throw + same-frame catch (exact and superclass)" {
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const a = aa.allocator();
    const ga = testing.allocator;

    // Stub java.lang exception hierarchy: Object <- Throwable <- Exception <- RuntimeException
    const objS = try makeStub(ga, a, "java/lang/Object", null, null);
    const thrS = try makeStub(ga, a, "java/lang/Throwable", "java/lang/Object", &objS);
    const excS = try makeStub(ga, a, "java/lang/Exception", "java/lang/Throwable", &thrS);
    const rteS = try makeStub(ga, a, "java/lang/RuntimeException", "java/lang/Exception", &excS);

    var cfm = try ClassFile.parse(ga, @embedFile("testdata/MyErr.class"));
    defer cfm.deinit();
    var cfe = try ClassFile.parse(ga, @embedFile("testdata/Exc.class"));
    defer cfe.deinit();
    const myErr = try Class.init(ga, a, &cfm, &rteS); // MyErr extends RuntimeException
    const exc = try Class.init(ga, a, &cfe, null);

    var loader = Loader.init(ga);
    defer loader.deinit();
    for ([_]*const Class{ &objS, &thrS, &excS, &rteS, &myErr, &exc }) |c| try loader.register(c);

    var b = Budget{};
    // f(-3): throws MyErr(-3), caught by catch(MyErr) -> e.code() = -3
    try testing.expectEqual(Value{ .int = -3 }, (try runInLoader(&loader, &exc, "f", "(I)I", &.{.{ .int = -3 }}, &b)).?);
    // f(5): no throw -> 10
    b = Budget{};
    try testing.expectEqual(Value{ .int = 10 }, (try runInLoader(&loader, &exc, "f", "(I)I", &.{.{ .int = 5 }}, &b)).?);
    // g(-2): throws MyErr, caught by catch(RuntimeException) [superclass match] -> -1
    b = Budget{};
    try testing.expectEqual(Value{ .int = -1 }, (try runInLoader(&loader, &exc, "g", "(I)I", &.{.{ .int = -2 }}, &b)).?);
    // g(4): no throw -> 4
    b = Budget{};
    try testing.expectEqual(Value{ .int = 4 }, (try runInLoader(&loader, &exc, "g", "(I)I", &.{.{ .int = 4 }}, &b)).?);
}

test "exceptions: cross-frame propagation and uncaught escape" {
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const a = aa.allocator();
    const ga = testing.allocator;
    const objS = try makeStub(ga, a, "java/lang/Object", null, null);
    const thrS = try makeStub(ga, a, "java/lang/Throwable", "java/lang/Object", &objS);
    const excS = try makeStub(ga, a, "java/lang/Exception", "java/lang/Throwable", &thrS);
    const rteS = try makeStub(ga, a, "java/lang/RuntimeException", "java/lang/Exception", &excS);
    var cfm = try ClassFile.parse(ga, @embedFile("testdata/MyErr.class"));
    defer cfm.deinit();
    var cf2 = try ClassFile.parse(ga, @embedFile("testdata/Exc2.class"));
    defer cf2.deinit();
    const myErr = try Class.init(ga, a, &cfm, &rteS);
    const exc2 = try Class.init(ga, a, &cf2, null);
    var loader = Loader.init(ga);
    defer loader.deinit();
    for ([_]*const Class{ &objS, &thrS, &excS, &rteS, &myErr, &exc2 }) |c| try loader.register(c);

    var b = Budget{};
    // caller(-5): thrower(-5) throws MyErr(-5) up one frame; caught -> code()-1 = -6
    try testing.expectEqual(Value{ .int = -6 }, (try runInLoader(&loader, &exc2, "caller", "(I)I", &.{.{ .int = -5 }}, &b)).?);
    // caller(4): thrower(4)=4 *3 = 12
    b = Budget{};
    try testing.expectEqual(Value{ .int = 12 }, (try runInLoader(&loader, &exc2, "caller", "(I)I", &.{.{ .int = 4 }}, &b)).?);
    // uncaught(-1): propagates out with no handler -> error.JavaException at the boundary
    b = Budget{};
    try testing.expectError(error.JavaException, runInLoader(&loader, &exc2, "uncaught", "(I)I", &.{.{ .int = -1 }}, &b));
}

test "GC: garbage is reclaimed, reachable objects survive" {
    const bp = @embedFile("testdata/Point.class");
    const bg = @embedFile("testdata/GcTest.class");
    var cfp = try ClassFile.parse(testing.allocator, bp);
    defer cfp.deinit();
    var cfg = try ClassFile.parse(testing.allocator, bg);
    defer cfg.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const point = try Class.init(testing.allocator, aa.allocator(), &cfp, null);
    const gct = try Class.init(testing.allocator, aa.allocator(), &cfg, null);
    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    try loader.register(&point);
    try loader.register(&gct);

    // allocLoop(1000): result n*n; the 1000 Points are garbage, so with frequent
    // GC the object table stays tiny (slots reused from the free list).
    {
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 16 };
        defer heap.deinit();
        var b = Budget{};
        const r = try runInLoaderWithHeap(&loader, &gct, "allocLoop", "(I)I", &.{.{ .int = 1000 }}, &b, &heap);
        try testing.expectEqual(Value{ .int = 1000000 }, r.?);
        // 1000 objects allocated, but GC kept the table small.
        try testing.expect(heap.objects.items.len < 64);
    }

    // arraySum(500): all 500 Points are reachable via the array; GC must keep
    // them all, and the result must be correct.
    {
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 16 };
        defer heap.deinit();
        var b = Budget{};
        const r = try runInLoaderWithHeap(&loader, &gct, "arraySum", "(I)I", &.{.{ .int = 500 }}, &b, &heap);
        try testing.expectEqual(Value{ .int = 250000 }, r.?);
        // The array + its 500 live elements survived: live count is ~501.
        try testing.expect(heap.liveCount() >= 500);
    }
}

test "moving GC: linked-list survives compaction with correct next-pointer remap" {
    var cf = try ClassFile.parse(testing.allocator, @embedFile("testdata/Node.class"));
    defer cf.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const node = try Class.init(testing.allocator, aa.allocator(), &cf, null);
    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    try loader.register(&node);

    // listSum(100): builds a 100-node list while GC compacts (moving nodes and
    // rewriting every `next` pointer). Traversal must still visit all nodes -> 5050.
    {
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 8 };
        defer heap.deinit();
        var b = Budget{};
        try testing.expectEqual(Value{ .int = 5050 }, (try runInLoaderWithHeap(&loader, &node, "listSum", "(I)I", &.{.{ .int = 100 }}, &b, &heap)).?);
    }
    // listSumTwice(50): 2 * (50*51/2) = 2550; first list is reclaimed between builds.
    {
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 8 };
        defer heap.deinit();
        var b = Budget{};
        try testing.expectEqual(Value{ .int = 2550 }, (try runInLoaderWithHeap(&loader, &node, "listSumTwice", "(I)I", &.{.{ .int = 50 }}, &b, &heap)).?);
    }
}

test "instanceof / checkcast with interface-aware subtyping" {
    var cfsh = try ClassFile.parse(testing.allocator, @embedFile("testdata/Shape.class"));
    defer cfsh.deinit();
    var cfsq = try ClassFile.parse(testing.allocator, @embedFile("testdata/Square.class"));
    defer cfsq.deinit();
    var cfan = try ClassFile.parse(testing.allocator, @embedFile("testdata/Animal.class"));
    defer cfan.deinit();
    var cfc = try ClassFile.parse(testing.allocator, @embedFile("testdata/Cast.class"));
    defer cfc.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const shape = try Class.init(testing.allocator, aa.allocator(), &cfsh, null);
    const square = try Class.init(testing.allocator, aa.allocator(), &cfsq, null);
    const animal = try Class.init(testing.allocator, aa.allocator(), &cfan, null);
    const cast = try Class.init(testing.allocator, aa.allocator(), &cfc, null);
    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    for ([_]*const Class{ &shape, &square, &animal, &cast }) |c| try loader.register(c);

    var b = Budget{};
    try testing.expectEqual(Value{ .int = 25 }, (try runInLoader(&loader, &cast, "castArea", "()I", &.{}, &b)).?);
    b = Budget{};
    try testing.expectEqual(Value{ .int = 1 }, (try runInLoader(&loader, &cast, "isShape", "()I", &.{}, &b)).?); // Square implements Shape
    b = Budget{};
    try testing.expectEqual(Value{ .int = 0 }, (try runInLoader(&loader, &cast, "isAnimal", "()I", &.{}, &b)).?); // Square is not Animal
}

test "finally blocks (try-catch-finally, normal + exceptional + nested)" {
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const a = aa.allocator();
    const ga = testing.allocator;
    const objS = try makeStub(ga, a, "java/lang/Object", null, null);
    const thrS = try makeStub(ga, a, "java/lang/Throwable", "java/lang/Object", &objS);
    const excS = try makeStub(ga, a, "java/lang/Exception", "java/lang/Throwable", &thrS);
    const rteS = try makeStub(ga, a, "java/lang/RuntimeException", "java/lang/Exception", &excS);
    var cfm = try ClassFile.parse(ga, @embedFile("testdata/MyErr.class"));
    defer cfm.deinit();
    var cff = try ClassFile.parse(ga, @embedFile("testdata/Fin.class"));
    defer cff.deinit();
    const myErr = try Class.init(ga, a, &cfm, &rteS);
    const fin = try Class.init(ga, a, &cff, null);
    var loader = Loader.init(ga);
    defer loader.deinit();
    for ([_]*const Class{ &objS, &thrS, &excS, &rteS, &myErr, &fin }) |c| try loader.register(c);

    var b = Budget{};
    // f(5): r=5,+100=105, finally +1 = 106
    try testing.expectEqual(Value{ .int = 106 }, (try runInLoader(&loader, &fin, "f", "(I)I", &.{.{ .int = 5 }}, &b)).?);
    // f(-3): r=-3, throw, catch r=1000, finally +1 = 1001
    b = Budget{};
    try testing.expectEqual(Value{ .int = 1001 }, (try runInLoader(&loader, &fin, "f", "(I)I", &.{.{ .int = -3 }}, &b)).?);
    // nestedFinally(4): inner sets r=1, inner-finally +10 = 11 (no catch)
    b = Budget{};
    try testing.expectEqual(Value{ .int = 11 }, (try runInLoader(&loader, &fin, "nestedFinally", "(I)I", &.{.{ .int = 4 }}, &b)).?);
    // nestedFinally(-1): throw, inner-finally +10 = 10, outer catch +100 = 110
    b = Budget{};
    try testing.expectEqual(Value{ .int = 110 }, (try runInLoader(&loader, &fin, "nestedFinally", "(I)I", &.{.{ .int = -1 }}, &b)).?);
}

test "generational GC: write barrier keeps old->young references alive" {
    var cfn = try ClassFile.parse(testing.allocator, @embedFile("testdata/Node.class"));
    defer cfn.deinit();
    var cfg = try ClassFile.parse(testing.allocator, @embedFile("testdata/GenTest.class"));
    defer cfg.deinit();
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const node = try Class.init(testing.allocator, aa.allocator(), &cfn, null);
    const gt = try Class.init(testing.allocator, aa.allocator(), &cfg, null);
    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    try loader.register(&node);
    try loader.register(&gt);

    // oldToYoung(100): keeper is promoted to old, then keeper.next = young node.
    // With the barrier, the young node survives minor GCs -> 42. Without it, the
    // node would be swept and this would return garbage or crash.
    {
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 4, .minors_per_major = 6 };
        defer heap.deinit();
        var b = Budget{};
        try testing.expectEqual(Value{ .int = 42 }, (try runInLoaderWithHeap(&loader, &gt, "oldToYoung", "(I)I", &.{.{ .int = 100 }}, &b, &heap)).?);
    }
    // garbageStaysSmall(2000): all Nodes are garbage; minor GC keeps the table tiny.
    {
        var heap = Heap{ .gpa = testing.allocator, .gc_interval = 8, .minors_per_major = 6 };
        defer heap.deinit();
        var b = Budget{};
        const r = try runInLoaderWithHeap(&loader, &gt, "garbageStaysSmall", "(I)I", &.{.{ .int = 2000 }}, &b, &heap);
        // sum of i for i in 0..1999 = 1999000
        try testing.expectEqual(Value{ .int = 1999000 }, r.?);
        try testing.expect(heap.liveCount() < 64);
    }
}

test "inherited-method constant pool: Rect inherits Shape.describe (regression)" {
    // Rect does NOT override describe(); its code lives in Shape and its bytecode's
    // constant-pool indices must resolve against Shape's pool, not Rect's. Caught by
    // differential testing against real java.
    const names = [_][]const u8{ "Vec", "Shape", "Rect", "Circle", "OopTest" };
    const blobs = [_][]const u8{
        @embedFile("testdata/Vec.class"),     @embedFile("testdata/Shape.class"),
        @embedFile("testdata/Rect.class"),    @embedFile("testdata/Circle.class"),
        @embedFile("testdata/OopTest.class"),
    };
    var aa = std.heap.ArenaAllocator.init(testing.allocator);
    defer aa.deinit();
    const a = aa.allocator();
    var loader = Loader.init(testing.allocator);
    defer loader.deinit();
    const objS = try a.create(Class);
    objS.* = try makeStub(testing.allocator, a, "java/lang/Object", null, null);
    try loader.register(objS);

    var cfs: [names.len]ClassFile = undefined;
    var built: usize = 0;
    // parse all
    inline for (blobs, 0..) |blob, i| {
        cfs[i] = try ClassFile.parse(testing.allocator, blob);
    }
    defer for (0..names.len) |i| cfs[i].deinit();
    // build resolving supers (fixpoint)
    var done = [_]bool{false} ** names.len;
    while (built < names.len) {
        var progressed = false;
        inline for (0..names.len) |i| {
            if (!done[i]) {
                const sn: ?[]const u8 = if (cfs[i].super_class != 0) try cfs[i].constant_pool.classNameOf(cfs[i].super_class) else null;
                const super = if (sn) |n| loader.find(n) else null;
                if (sn == null or super != null) {
                    const c = try a.create(Class);
                    c.* = try Class.init(testing.allocator, a, &cfs[i], super);
                    try loader.register(c);
                    done[i] = true;
                    built += 1;
                    progressed = true;
                }
            }
        }
        if (!progressed) return error.Unresolved;
    }

    const oop = loader.find("OopTest").?;
    var b = Budget{};
    // poly(): Rect(3,4).describe()=24 + Circle(5).describe()=155 + Rect(2,2).describe()=8 = 187
    try testing.expectEqual(Value{ .int = 187 }, (try runInLoader(&loader, oop, "poly", "()I", &.{}, &b)).?);
    b = Budget{};
    try testing.expectEqual(Value{ .int = 31 }, (try runInLoader(&loader, oop, "vecs", "()I", &.{}, &b)).?);
}
