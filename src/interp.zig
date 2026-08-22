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
    code: []const u8 = &.{},
    class: ?*const Class = null,
    exceptions: []const attribute_decode.ExceptionTableEntry = &.{},

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
pub const LambdaObj = struct {
    iface: []const u8,
    impl_class: []const u8,
    impl_name: []const u8,
    impl_desc: []const u8,
    impl_kind: u8,
    captures: []Value,
};
pub const BuilderObj = struct { class: *const Class, buf: []i32, len: usize }; // mutable StringBuilder
pub const BoxedObj = struct { class: *const Class, value: Value }; // boxed primitive (Integer/Long/...)
pub const HeapObj = union(enum) { instance: Instance, array: Array, string: StringObj, lambda: LambdaObj, builder: BuilderObj, boxed: BoxedObj };

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
    /// String literal pool: interned literal content (2 bytes/char, LE) -> instance
    /// id. Roots for GC (marked always; remapped by the compacting major collector).
    interned: std.StringHashMapUnmanaged(u32) = .empty,
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
            .lambda => |x| self.gpa.free(x.captures),
            .builder => |x| self.gpa.free(x.buf),
            .boxed => {},
        };
        self.objects.deinit(self.gpa);
        self.marked.deinit(self.gpa);
        self.old.deinit(self.gpa);
        self.remembered.deinit(self.gpa);
        self.free_list.deinit(self.gpa);
        var it = self.interned.keyIterator();
        while (it.next()) |k| self.gpa.free(k.*);
        self.interned.deinit(self.gpa);
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
    pub fn putLambda(self: *Heap, l: LambdaObj) !u32 {
        return self.put(.{ .lambda = l });
    }
    pub fn putBuilder(self: *Heap, class: *const Class) !u32 {
        const buf = try self.gpa.alloc(i32, 16);
        return self.put(.{ .builder = .{ .class = class, .buf = buf, .len = 0 } });
    }
    pub fn putBoxed(self: *Heap, class: *const Class, value: Value) !u32 {
        return self.put(.{ .boxed = .{ .class = class, .value = value } });
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
        .lambda => |x| for (x.captures) |v| markValue(heap, v),
        .builder => {},
        .boxed => {},
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
        .lambda => |*x| for (x.captures) |*v| remapValuePtr(v, forwarding),
        .builder => {},
        .boxed => {},
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
    {
        var it = heap.interned.valueIterator();
        while (it.next()) |vp| markObject(heap, vp.*);
    }
    for (f.loader.mirrors.items) |m| if (m) |id| markObject(heap, id);

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
                    .lambda => |x| heap.gpa.free(x.captures),
                    .builder => |x| heap.gpa.free(x.buf),
                    .boxed => {},
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
    {
        var it = heap.interned.valueIterator();
        while (it.next()) |vp| vp.* = forwarding[vp.*];
    }
    for (f.loader.mirrors.items) |*m| if (m.*) |id| {
        m.* = forwarding[id];
    };

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
        .lambda => |x| for (x.captures) |v| markYoungValue(heap, v),
        .builder => {},
        .boxed => {},
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
    {
        var it = heap.interned.valueIterator();
        while (it.next()) |vp| markYoungObject(heap, vp.*);
    }
    for (f.loader.mirrors.items) |m| if (m) |mid| markYoungObject(heap, mid);
    var id: u32 = 0;
    while (id < heap.objects.items.len) : (id += 1) {
        if (heap.remembered.items[id]) {
            if (heap.objects.items[id]) |obj| switch (obj) {
                .instance => |x| for (x.fields) |v| markYoungValue(heap, v),
                .array => |x| if (x.elem == .reference) for (x.data) |v| markYoungValue(heap, v),
                .string => {},
                .lambda => |x| for (x.captures) |v| markYoungValue(heap, v),
                .builder => {},
                .boxed => {},
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
                        .lambda => |x| heap.gpa.free(x.captures),
                        .builder => |x| heap.gpa.free(x.buf),
                        .boxed => {},
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
fn step(f: *Frame) RunError!Op {
    f.budget.steps += 1;
    if (f.budget.steps > f.budget.max_steps) return error.StepLimitExceeded;
    return opAt(f.code, f.pc);
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
/// Minimal in-memory zip reader: returns the uncompressed bytes of the archive
/// entry named "<want>.class", or null if absent. Handles STORED (method 0) and
/// DEFLATE (method 8), which is all a JDK-produced jar uses. Caller owns the
/// returned slice. Bounds-checked throughout; malformed input yields null.
fn zipReadEntryClass(gpa: std.mem.Allocator, zip: []const u8, want: []const u8) RunError!?[]u8 {
    const want_name = std.fmt.allocPrint(gpa, "{s}.class", .{want}) catch return error.OutOfMemory;
    defer gpa.free(want_name);
    if (zip.len < 22) return null;

    // Locate the End Of Central Directory record (sig 0x06054b50), scanning back
    // over the (usually empty) trailing comment.
    var eocd: usize = zip.len - 22;
    const min_eocd: usize = if (zip.len > 22 + 65535) zip.len - 22 - 65535 else 0;
    while (true) {
        if (std.mem.readInt(u32, zip[eocd..][0..4], .little) == 0x06054b50) break;
        if (eocd == min_eocd) return null;
        eocd -= 1;
    }
    const total = std.mem.readInt(u16, zip[eocd + 10 ..][0..2], .little);
    var cd = std.mem.readInt(u32, zip[eocd + 16 ..][0..4], .little);

    var i: usize = 0;
    while (i < total) : (i += 1) {
        const p: usize = cd;
        if (p + 46 > zip.len) return null;
        if (std.mem.readInt(u32, zip[p..][0..4], .little) != 0x02014b50) return null;
        const method = std.mem.readInt(u16, zip[p + 10 ..][0..2], .little);
        const comp_size = std.mem.readInt(u32, zip[p + 20 ..][0..4], .little);
        const name_len = std.mem.readInt(u16, zip[p + 28 ..][0..2], .little);
        const extra_len = std.mem.readInt(u16, zip[p + 30 ..][0..2], .little);
        const comment_len = std.mem.readInt(u16, zip[p + 32 ..][0..2], .little);
        const local_off = std.mem.readInt(u32, zip[p + 42 ..][0..4], .little);
        const nstart = p + 46;
        if (nstart + name_len > zip.len) return null;
        const ename = zip[nstart .. nstart + name_len];
        cd = @intCast(nstart + name_len + extra_len + comment_len);
        if (!std.mem.eql(u8, ename, want_name)) continue;

        // Found it: parse the local file header to find the data offset (its
        // name/extra lengths can differ from the central directory's).
        const lo: usize = local_off;
        if (lo + 30 > zip.len) return null;
        if (std.mem.readInt(u32, zip[lo..][0..4], .little) != 0x04034b50) return null;
        const lname_len = std.mem.readInt(u16, zip[lo + 26 ..][0..2], .little);
        const lextra_len = std.mem.readInt(u16, zip[lo + 28 ..][0..2], .little);
        const data = lo + 30 + @as(usize, lname_len) + @as(usize, lextra_len);
        if (data + comp_size > zip.len) return null;
        const comp = zip[data .. data + comp_size];

        if (method == 0) return gpa.dupe(u8, comp) catch error.OutOfMemory;
        if (method == 8) {
            const flate = std.compress.flate;
            const window = gpa.alloc(u8, flate.max_window_len) catch return error.OutOfMemory;
            defer gpa.free(window);
            var src = std.Io.Reader.fixed(comp);
            var dec = flate.Decompress.init(&src, .raw, window);
            return dec.reader.allocRemaining(gpa, .unlimited) catch return null;
        }
        return null; // unsupported compression method
    }
    return null;
}

const JarBytes = struct { path: []const u8, bytes: []u8 };

pub const Loader = struct {
    gpa: std.mem.Allocator,
    classes: std.ArrayList(*const Class) = .empty,
    statics: std.ArrayList([]Value) = .empty,
    initialized: std.ArrayList(bool) = .empty,
    /// java.lang.Class mirror id per class (lazy, one per class). A GC root:
    /// marked by both collectors and remapped by the compacting major collector.
    mirrors: std.ArrayList(?u32) = .empty,
    /// Portable IO handle for OS-boundary natives (System.out write, clocks).
    /// Null under unit tests, which never call those natives.
    io: ?std.Io = null,
    /// Classpath directories searched (in order) to lazily load a class by name
    /// on first resolution. Empty under unit tests and eager-file runs.
    classpath: std.ArrayList([]const u8) = .empty,
    /// Arena for Class structs built lazily from the classpath. Set by the CLI
    /// to the same arena that owns eagerly-built classes; null disables lazy load.
    class_arena: ?std.mem.Allocator = null,
    /// ClassFiles parsed on demand from the classpath; owned here so their
    /// constant pools outlive the Classes that reference them.
    owned_cfs: std.ArrayList(*ClassFile) = .empty,
    /// Cache of whole .jar archives read from the classpath, keyed by path, so
    /// each jar is read once and reused across lazy class loads.
    jar_cache: std.ArrayList(JarBytes) = .empty,

    pub fn init(gpa: std.mem.Allocator) Loader {
        return .{ .gpa = gpa };
    }
    pub fn deinit(self: *Loader) void {
        for (self.statics.items) |st| self.gpa.free(st);
        self.statics.deinit(self.gpa);
        self.classes.deinit(self.gpa);
        self.initialized.deinit(self.gpa);
        self.mirrors.deinit(self.gpa);
        for (self.owned_cfs.items) |cf| {
            cf.deinit();
            self.gpa.destroy(cf);
        }
        self.owned_cfs.deinit(self.gpa);
        self.classpath.deinit(self.gpa);
        for (self.jar_cache.items) |jc| {
            self.gpa.free(jc.path);
            self.gpa.free(jc.bytes);
        }
        self.jar_cache.deinit(self.gpa);
    }

    /// Lazily load a class by internal name from the classpath directories,
    /// parsing + building + registering it (and its superclass) on demand.
    /// Returns null if not found on the classpath; errors on parse/link failure.
    pub fn loadFromClasspath(self: *Loader, name: []const u8) RunError!?*const Class {
        const io = self.io orelse return null;
        if (self.class_arena == null) return null;
        if (self.classpath.items.len == 0) return null;
        for (self.classpath.items) |entry| {
            if (std.mem.endsWith(u8, entry, ".jar")) {
                const jar = (try self.jarBytes(io, entry)) orelse continue;
                const cls_bytes = (zipReadEntryClass(self.gpa, jar, name) catch continue) orelse continue;
                defer self.gpa.free(cls_bytes);
                if (try self.buildFromBytes(cls_bytes)) |c| return c;
            } else {
                const rel = std.fmt.allocPrint(self.gpa, "{s}/{s}.class", .{ entry, name }) catch return error.OutOfMemory;
                defer self.gpa.free(rel);
                const bytes = std.Io.Dir.cwd().readFileAlloc(io, rel, self.gpa, .unlimited) catch continue;
                defer self.gpa.free(bytes);
                if (try self.buildFromBytes(bytes)) |c| return c;
            }
        }
        return null;
    }

    /// Parse `bytes` (a .class image) and build+register the Class, resolving its
    /// superclass lazily. Returns null on parse failure (caller tries next entry).
    fn buildFromBytes(self: *Loader, bytes: []const u8) RunError!?*const Class {
        const arena = self.class_arena.?;
        const cf = self.gpa.create(ClassFile) catch return error.OutOfMemory;
        cf.* = ClassFile.parse(self.gpa, bytes) catch {
            self.gpa.destroy(cf);
            return null;
        };
        self.owned_cfs.append(self.gpa, cf) catch return error.OutOfMemory;
        const super_name: ?[]const u8 = if (cf.super_class != 0) (cf.constant_pool.classNameOf(cf.super_class) catch null) else null;
        const super: ?*const Class = if (super_name) |sn|
            (self.find(sn) orelse (try self.loadFromClasspath(sn)) orelse return error.LinkError)
        else
            null;
        const c = arena.create(Class) catch return error.OutOfMemory;
        c.* = Class.init(self.gpa, arena, cf, super) catch return error.LinkError;
        self.register(c) catch return error.OutOfMemory;
        return c;
    }

    /// Read (and cache) the full bytes of a .jar archive from the classpath.
    fn jarBytes(self: *Loader, io: std.Io, path: []const u8) RunError!?[]const u8 {
        for (self.jar_cache.items) |jc| {
            if (std.mem.eql(u8, jc.path, path)) return jc.bytes;
        }
        const bytes = std.Io.Dir.cwd().readFileAlloc(io, path, self.gpa, .unlimited) catch return null;
        const path_copy = self.gpa.dupe(u8, path) catch return error.OutOfMemory;
        self.jar_cache.append(self.gpa, .{ .path = path_copy, .bytes = bytes }) catch return error.OutOfMemory;
        return bytes;
    }
    pub fn register(self: *Loader, class: *const Class) !void {
        const st = try self.gpa.alloc(Value, class.static_fields.len);
        for (st, class.static_fields) |*sv, sf| sv.* = defaultValue(sf.kind);
        try self.classes.append(self.gpa, class);
        try self.statics.append(self.gpa, st);
        try self.initialized.append(self.gpa, false);
        try self.mirrors.append(self.gpa, null);
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

pub const AnnoVal = union(enum) {
    none,
    int: i32,
    long: i64,
    float: f32,
    double: f64,
    string: []const u8,
};
pub const AnnoElement = struct { name: []const u8, value: AnnoVal };
pub const AnnotationInfo = struct { name: []const u8, elements: []const AnnoElement };

fn annoReadU16(info: []const u8, cur: *usize) u16 {
    if (cur.* + 2 > info.len) {
        cur.* = info.len;
        return 0;
    }
    const v = std.mem.readInt(u16, info[cur.*..][0..2], .big);
    cur.* += 2;
    return v;
}
fn annoDecodeElementValue(arena: std.mem.Allocator, cp: anytype, info: []const u8, cur: *usize) AnnoVal {
    if (cur.* >= info.len) return .none;
    const tag = info[cur.*];
    cur.* += 1;
    switch (tag) {
        'B', 'C', 'S', 'Z', 'I' => {
            const ci = annoReadU16(info, cur);
            const ent = cp.get(ci) catch return .none;
            return switch (ent.*) {
                .integer => |v| .{ .int = v },
                else => .none,
            };
        },
        'J' => {
            const ci = annoReadU16(info, cur);
            const ent = cp.get(ci) catch return .none;
            return switch (ent.*) {
                .long => |v| .{ .long = v },
                else => .none,
            };
        },
        'F' => {
            const ci = annoReadU16(info, cur);
            const ent = cp.get(ci) catch return .none;
            return switch (ent.*) {
                .float => |v| .{ .float = v },
                else => .none,
            };
        },
        'D' => {
            const ci = annoReadU16(info, cur);
            const ent = cp.get(ci) catch return .none;
            return switch (ent.*) {
                .double => |v| .{ .double = v },
                else => .none,
            };
        },
        's' => {
            const ci = annoReadU16(info, cur);
            const b = cp.utf8(ci) catch "";
            return .{ .string = arena.dupe(u8, b) catch "" };
        },
        'e' => {
            _ = annoReadU16(info, cur);
            _ = annoReadU16(info, cur);
            return .none;
        },
        'c' => {
            _ = annoReadU16(info, cur);
            return .none;
        },
        '@' => {
            _ = annoReadU16(info, cur);
            const np = annoReadU16(info, cur);
            var i: usize = 0;
            while (i < np) : (i += 1) {
                _ = annoReadU16(info, cur);
                _ = annoDecodeElementValue(arena, cp, info, cur);
            }
            return .none;
        },
        '[' => {
            const n = annoReadU16(info, cur);
            var i: usize = 0;
            while (i < n) : (i += 1) _ = annoDecodeElementValue(arena, cp, info, cur);
            return .none;
        },
        else => return .none,
    }
}
fn decodeAnnotations(arena: std.mem.Allocator, cp: anytype, info: []const u8) ![]const AnnotationInfo {
    var out: std.ArrayListUnmanaged(AnnotationInfo) = .empty;
    var cur: usize = 0;
    const num = annoReadU16(info, &cur);
    var a: usize = 0;
    while (a < num) : (a += 1) {
        const type_index = annoReadU16(info, &cur);
        const desc = cp.utf8(type_index) catch "";
        var type_name: []const u8 = "";
        if (desc.len >= 2 and desc[0] == 'L' and desc[desc.len - 1] == ';') {
            type_name = desc[1 .. desc.len - 1];
        }
        const num_pairs = annoReadU16(info, &cur);
        var els: std.ArrayListUnmanaged(AnnoElement) = .empty;
        var pi: usize = 0;
        while (pi < num_pairs) : (pi += 1) {
            const name_index = annoReadU16(info, &cur);
            const ename = cp.utf8(name_index) catch "";
            const val = annoDecodeElementValue(arena, cp, info, &cur);
            try els.append(arena, .{ .name = ename, .value = val });
        }
        try out.append(arena, .{ .name = type_name, .elements = try els.toOwnedSlice(arena) });
    }
    return out.toOwnedSlice(arena);
}
fn decodeMemberAnnotations(arena: std.mem.Allocator, cp: anytype, attrs: anytype) []const AnnotationInfo {
    for (attrs) |ai| {
        const an = cp.utf8(ai.name_index) catch continue;
        if (std.mem.eql(u8, an, "RuntimeVisibleAnnotations")) {
            return decodeAnnotations(arena, cp, ai.info) catch &.{};
        }
    }
    return &.{};
}
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
    is_stub: bool = false,
    /// Decoded class-level RuntimeVisibleAnnotations (type name + element values).
    annotations: []const AnnotationInfo = &.{},
    /// True for a runtime-built java.lang.reflect.Proxy class (dispatch to handler).
    is_proxy: bool = false,
    is_interface: bool = false,
    is_primitive: bool = false,

    pub const Field = struct { name: []const u8, kind: Kind, annotations: []const AnnotationInfo = &.{} };
    pub const Param = struct { kind: Kind, slot: u16 };
    pub const Method = struct {
        name: []const u8,
        descriptor: []const u8,
        code: ?attribute_decode.CodeAttr,
        params: []Param,
        arg_slots: u16,
        ret: ?Kind,
        is_static: bool,
        is_native: bool,
        annotations: []const AnnotationInfo = &.{},
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
                .annotations = decodeMemberAnnotations(arena, cf.constant_pool, fld.attributes),
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
            const is_native = m.access_flags.isNative();
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
                .is_native = is_native,
                .annotations = decodeMemberAnnotations(arena, cf.constant_pool, m.attributes),
            };
        }
        var class_annos: []const AnnotationInfo = &.{};
        for (cf.attributes) |ai| {
            const an = cf.constant_pool.utf8(ai.name_index) catch continue;
            if (std.mem.eql(u8, an, "RuntimeVisibleAnnotations")) {
                class_annos = decodeAnnotations(arena, cf.constant_pool, ai.info) catch &.{};
                break;
            }
        }
        var bootstrap: []const attribute_decode.BootstrapMethod = &.{};
        for (cf.attributes) |ai| {
            if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "BootstrapMethods")) {
                bootstrap = (try attribute_decode.decode(arena, cf.constant_pool, ai)).bootstrap_methods;
            }
        }
        return .{ .gpa = gpa, .cp = cf.constant_pool, .name = cls_name, .super = super, .super_name = super_name, .interfaces = interfaces, .methods = methods, .instance_fields = instance_fields, .static_fields = static_fields, .bootstrap_methods = bootstrap, .annotations = class_annos, .is_interface = cf.access_flags.isInterface() };
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
    pub const Resolved = struct { method: *const Method, owner: *const Class };
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
fn fieldRefKind(cls: *const Class, cp_index: u16) RunError!Kind {
    const c = cls.cp.get(cp_index) catch return error.LinkError;
    const ref = switch (c.*) {
        .fieldref => |r| r,
        else => return error.LinkError,
    };
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const desc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;
    if (desc.len == 0) return error.LinkError;
    return switch (desc[0]) {
        'J' => .long,
        'D' => .double,
        'F' => .float,
        'L', '[' => .reference,
        else => .int, // B C S Z I
    };
}

fn doNew(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    maybeCollect(f);
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const tclass = try resolveClass(f, cls, try refClassName(cls, try u16At(code, f.pc + 1)));
    try f.loader.ensureInit(tclass, heap, f.budget);
    if (tclass.is_stub and (std.mem.eql(u8, tclass.name, "java/lang/StringBuilder") or std.mem.eql(u8, tclass.name, "java/lang/StringBuffer"))) {
        // Stub path: the Zig builder intrinsic backs StringBuilder via a BuilderObj.
        // A real (jbase) StringBuilder is an ordinary instance with char[] fields.
        try f.push(.{ .reference = try heap.putBuilder(tclass) });
        return;
    }
    try f.push(.{ .reference = try heap.allocInstance(tclass) });
}

fn doGetField(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const idx = try u16At(code, f.pc + 1);
    const fname = try fieldName(cls, idx);
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const inst = switch (heap.get(oid).*) {
        .instance => |*x| x,
        else => return error.LinkError,
    };
    const fi = inst.class.findField(fname) orelse return error.LinkError;
    // pushKind re-expands a long/double field to its two operand-stack slots.
    try f.pushKind(inst.fields[fi]);
}

fn doPutField(f: *Frame, cls: *const Class, code: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const idx = try u16At(code, f.pc + 1);
    const fname = try fieldName(cls, idx);
    // long/double values occupy two operand-stack slots; pop by the field's kind.
    const value = try f.popKind(try fieldRefKind(cls, idx));
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

const ParamLayout = struct { params: []Class.Param, arg_slots: u16 };
fn computeParams(gpa: std.mem.Allocator, mdesc: []const u8, is_static: bool) RunError!ParamLayout {
    const mt = descriptor.parseMethodDescriptor(gpa, mdesc) catch return error.LinkError;
    defer gpa.free(mt.params);
    const params = gpa.alloc(Class.Param, mt.params.len) catch return error.OutOfMemory;
    var slot: u16 = if (is_static) 0 else 1;
    for (mt.params, 0..) |pt, k| {
        const kind = fieldKind(pt);
        params[k] = .{ .kind = kind, .slot = slot };
        slot += if (kind == .long or kind == .double) 2 else 1;
    }
    return .{ .params = params, .arg_slots = slot };
}

fn findDefaultInInterface(f: *Frame, iface_name: []const u8, name: []const u8, desc: []const u8) ?Class.Resolved {
    const ic = f.loader.find(iface_name) orelse return null;
    for (ic.methods) |*m| {
        if (m.code != null and std.mem.eql(u8, m.name, name) and std.mem.eql(u8, m.descriptor, desc)) {
            return .{ .method = m, .owner = ic };
        }
    }
    for (ic.interfaces) |sup| {
        if (findDefaultInInterface(f, sup, name, desc)) |res| return res;
    }
    return null;
}
fn resolveInterfaceDefault(f: *Frame, start: *const Class, name: []const u8, desc: []const u8) ?Class.Resolved {
    var c: ?*const Class = start;
    while (c) |cc| {
        for (cc.interfaces) |ifn| {
            if (findDefaultInInterface(f, ifn, name, desc)) |res| return res;
        }
        c = cc.super;
    }
    return null;
}
/// A resolved Java-to-Java call the driver loop should execute: a new frame's
/// worth of context. Returned via an out-param by invokeInstance/invokeStatic
/// for the non-native, non-intrinsic path; ownership of `slots` transfers to
/// the caller (freed after the call). Enables converting recursion into an
/// explicit frame stack.
const PendingCall = struct {
    owner: *const Class,
    code: []const u8,
    max_stack: u16,
    max_locals: u16,
    exception_table: []const attribute_decode.ExceptionTableEntry,
    slots: []Value,
};

fn invokeInstance(f: *Frame, cls: *const Class, code: []const u8, is_special: bool, pending: *?PendingCall) RunError!void {
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

    const recv_is_real = if (f.loader.find(cname)) |rc| !rc.is_stub else false;
    if (!recv_is_real) {
        if (std.mem.eql(u8, cname, "java/lang/String")) return stringIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, cname, "java/lang/StringBuilder") or std.mem.eql(u8, cname, "java/lang/StringBuffer")) return builderIntrinsic(f, mname, mdesc);
    }

    // Bootstrap stub: java/lang/Object.<init> is a no-op (we have no JDK loaded).
    if (is_special and std.mem.eql(u8, mname, "<init>") and std.mem.eql(u8, cname, "java/lang/Object")) {
        _ = (try f.popRef()) orelse return error.NullPointer; // consume `this`
        return;
    }

    // Param layout from the descriptor (works even when the declared class/
    // interface is only a stub -- important for functional interfaces).
    const gpa = f.loader.gpa;
    const pl = try computeParams(gpa, mdesc, false);
    defer gpa.free(pl.params);
    const slots = try gpa.alloc(Value, pl.arg_slots);
    defer gpa.free(slots);
    var i: usize = pl.params.len;
    while (i > 0) {
        i -= 1;
        const p = pl.params[i];
        slots[p.slot] = try f.popKind(p.kind);
        if (p.kind == .long or p.kind == .double) slots[p.slot + 1] = .top;
    }
    const oid = (try f.popRef()) orelse return error.NullPointer;
    const heap = f.heap orelse return error.UnsupportedOpcode;
    switch (heap.get(oid).*) {
        .lambda => |lam| return dispatchLambda(f, lam, slots, pl.params),
        .boxed => return boxedMethod(f, oid, slots, pl.params, mname, mdesc),
        .array => {
            // Arrays expose Object.clone() as a shallow copy (used by enum values()).
            if (std.mem.eql(u8, mname, "clone")) return cloneArray(f, oid);
            return error.LinkError;
        },
        else => {},
    }
    slots[0] = .{ .reference = oid };

    // Dispatch: invokespecial uses the declared class; invokevirtual/interface use
    // the receiver's actual class.
    const rclass = if (is_special) try resolveClass(f, cls, cname) else switch (heap.get(oid).*) {
        .instance => |x| x.class,
        .string => |x| x.class,
        else => return error.LinkError,
    };
    if (rclass.is_proxy) return proxyDispatch(f, oid, cname, mname, mdesc, slots, pl.params);
    const tr = rclass.resolve(mname, mdesc) orelse (resolveInterfaceDefault(f, rclass, mname, mdesc) orelse return error.MethodNotFound);
    const target = tr.method;
    const owner = tr.owner;

    if (target.is_native) return nativeInvoke(f, owner, target, slots);

    const cc = target.code orelse return error.LinkError;
    pending.* = .{ .owner = owner, .code = cc.code, .max_stack = cc.max_stack, .max_locals = cc.max_locals, .exception_table = cc.exception_table, .slots = f.loader.gpa.dupe(Value, slots) catch return error.OutOfMemory };
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
    if (f.loader.find(name)) |c| return c;
    return (try f.loader.loadFromClasspath(name)) orelse error.LinkError;
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

fn objectsIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    if (eq2(name, desc, "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;")) {
        const r = try f.popRef();
        if (r == null) return error.NullPointer;
        return f.push(.{ .reference = r });
    }
    if (eq2(name, desc, "requireNonNull", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;")) {
        _ = try f.popRef();
        const r = try f.popRef();
        if (r == null) return error.NullPointer;
        return f.push(.{ .reference = r });
    }
    if (eq2(name, desc, "isNull", "(Ljava/lang/Object;)Z")) return f.pushInt(if ((try f.popRef()) == null) 1 else 0);
    if (eq2(name, desc, "nonNull", "(Ljava/lang/Object;)Z")) return f.pushInt(if ((try f.popRef()) != null) 1 else 0);
    return error.UnsupportedOpcode;
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
fn appendJavaFloat(out: *std.ArrayList(i32), gpa: std.mem.Allocator, comptime T: type, value: T) RunError!void {
    if (std.math.isNan(value)) return appendAscii(out, gpa, "NaN");
    if (std.math.isInf(value)) return appendAscii(out, gpa, if (value < 0) "-Infinity" else "Infinity");
    var buf: [64]u8 = undefined;
    const sci = std.fmt.float.render(&buf, value, .{ .mode = .scientific }) catch return error.OutOfMemory;
    // Parse "<d>[.<frac>]e<exp>" (Zig scientific = shortest round-trip) into digits D and decExp.
    var i: usize = 0;
    const neg = sci[0] == '-';
    if (neg) i = 1;
    const epos = std.mem.indexOfScalarPos(u8, sci, i, 'e') orelse return error.OutOfMemory;
    var digits: [32]u8 = undefined;
    var n: usize = 0;
    for (sci[i..epos]) |c| {
        if (c == '.') continue;
        digits[n] = c;
        n += 1;
    }
    const dec_exp = std.fmt.parseInt(i32, sci[epos + 1 ..], 10) catch return error.OutOfMemory;
    if (neg) try out.append(gpa, '-');
    const D = digits[0..n];
    if (dec_exp < -3 or dec_exp >= 7) {
        // scientific: D[0] "." (D[1..] or "0") "E" dec_exp
        try out.append(gpa, D[0]);
        try out.append(gpa, '.');
        if (n > 1) try appendAscii(out, gpa, D[1..]) else try out.append(gpa, '0');
        try out.append(gpa, 'E');
        try appendDecimalInt(out, gpa, dec_exp);
    } else if (dec_exp >= 0) {
        const int_digits: usize = @intCast(dec_exp + 1);
        if (n <= int_digits) {
            try appendAscii(out, gpa, D);
            var z: usize = int_digits - n;
            while (z > 0) : (z -= 1) try out.append(gpa, '0');
            try appendAscii(out, gpa, ".0");
        } else {
            try appendAscii(out, gpa, D[0..int_digits]);
            try out.append(gpa, '.');
            try appendAscii(out, gpa, D[int_digits..]);
        }
    } else {
        // dec_exp in -3..-1: "0." zeros(-dec_exp-1) D
        try appendAscii(out, gpa, "0.");
        var z: i32 = -dec_exp - 1;
        while (z > 0) : (z -= 1) try out.append(gpa, '0');
        try appendAscii(out, gpa, D);
    }
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
            .float => try appendJavaFloat(out, gpa, f32, v.float),
            .double => try appendJavaFloat(out, gpa, f64, v.double),
        },
        .object => switch (v) {
            .reference => |r| if (r) |id| try appendStringObj(out, heap, id) else try appendAscii(out, gpa, "null"),
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
    if (std.mem.eql(u8, bclass, "java/lang/invoke/LambdaMetafactory")) {
        return doLambda(f, cls, desc, bm);
    }
    return error.UnsupportedOpcode;
}
fn doLambda(f: *Frame, cls: *const Class, desc: []const u8, bm: attribute_decode.BootstrapMethod) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const mt = descriptor.parseMethodDescriptor(heap.gpa, desc) catch return error.LinkError;
    defer heap.gpa.free(mt.params);
    const ret_ft = mt.ret orelse return error.LinkError;
    const iface = switch (ret_ft.kind) {
        .object => |n| n,
        else => return error.LinkError,
    };
    if (bm.arguments.len < 2) return error.LinkError;
    const mh = switch ((cls.cp.get(bm.arguments[1]) catch return error.LinkError).*) {
        .method_handle => |x| x,
        else => return error.LinkError,
    };
    const iref = switch ((cls.cp.get(mh.reference_index) catch return error.LinkError).*) {
        .methodref => |r| r,
        .interface_methodref => |r| r,
        else => return error.LinkError,
    };
    const impl_class = try refClassName(cls, iref.class_index);
    const inat = switch ((cls.cp.get(iref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const impl_name = cls.cp.utf8(inat.name_index) catch return error.LinkError;
    const impl_desc = cls.cp.utf8(inat.descriptor_index) catch return error.LinkError;
    const ncap = mt.params.len;
    const captures = heap.gpa.alloc(Value, ncap) catch return error.OutOfMemory;
    errdefer heap.gpa.free(captures);
    var i: usize = ncap;
    while (i > 0) {
        i -= 1;
        captures[i] = try f.popKind(fieldKind(mt.params[i]));
    }
    try f.push(.{ .reference = try heap.putLambda(.{
        .iface = iface,
        .impl_class = impl_class,
        .impl_name = impl_name,
        .impl_desc = impl_desc,
        .impl_kind = mh.reference_kind,
        .captures = captures,
    }) });
}
fn runImplStatic(f: *Frame, owner: *const Class, impl: *const Class.Method, args: []const Value) RunError!void {
    if (args.len != impl.params.len) return error.LinkError;
    const cc = impl.code orelse return error.LinkError;
    const islots = try owner.gpa.alloc(Value, impl.arg_slots);
    defer owner.gpa.free(islots);
    for (impl.params, 0..) |p, k| {
        islots[p.slot] = args[k];
        if (p.kind == .long or p.kind == .double) islots[p.slot + 1] = .top;
    }
    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const ret = try exec(owner.gpa, owner, f.heap, f.loader, f.budget, cc.code, cc.max_stack, cc.max_locals, islots, cc.exception_table, f);
    if (ret) |rv| try f.pushKind(rv);
}
fn runImplInstance(f: *Frame, owner: *const Class, impl: *const Class.Method, recv_id: u32, args: []const Value) RunError!void {
    if (args.len != impl.params.len) return error.LinkError;
    const cc = impl.code orelse return error.LinkError;
    const islots = try owner.gpa.alloc(Value, impl.arg_slots);
    defer owner.gpa.free(islots);
    islots[0] = .{ .reference = recv_id };
    for (impl.params, 0..) |p, k| {
        islots[p.slot] = args[k];
        if (p.kind == .long or p.kind == .double) islots[p.slot + 1] = .top;
    }
    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const ret = try exec(owner.gpa, owner, f.heap, f.loader, f.budget, cc.code, cc.max_stack, cc.max_locals, islots, cc.exception_table, f);
    if (ret) |rv| try f.pushKind(rv);
}
fn dispatchLambda(f: *Frame, lam: LambdaObj, sam_slots: []const Value, sam_params: []const Class.Param) RunError!void {
    var logical: [128]Value = undefined;
    var n: usize = 0;
    for (lam.captures) |c| {
        logical[n] = c;
        n += 1;
    }
    for (sam_params) |p| {
        logical[n] = sam_slots[p.slot];
        n += 1;
    }
    if (lam.impl_kind == 6) {
        const impl_cls = f.loader.find(lam.impl_class) orelse return error.LinkError;
        const ir = impl_cls.resolve(lam.impl_name, lam.impl_desc) orelse return error.MethodNotFound;
        return runImplStatic(f, ir.owner, ir.method, logical[0..n]);
    }
    if (n == 0) return error.LinkError;
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const recv_id = switch (logical[0]) {
        .reference => |r| r orelse return error.NullPointer,
        else => return error.TypeMismatch,
    };
    const recv_class = if (lam.impl_kind == 7)
        (f.loader.find(lam.impl_class) orelse return error.LinkError)
    else switch (heap.get(recv_id).*) {
        .instance => |x| x.class,
        .string => |x| x.class,
        .builder => |x| x.class,
        else => return error.LinkError,
    };
    const ir = recv_class.resolve(lam.impl_name, lam.impl_desc) orelse return error.MethodNotFound;
    return runImplInstance(f, ir.owner, ir.method, recv_id, logical[1..n]);
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

fn makeString(f: *Frame, chars: []const i32) RunError!u32 {
    // Representation-aware String builder: a real char[]-backed instance when
    // java.lang.String is the real loaded class, else a StringObj for the Zig
    // intrinsic (bootstrap stub). GC is opcode-level, so ids stay valid here.
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
    if (!str_class.is_stub) {
        const aid = try heap.allocArray(.int, chars.len);
        const arr = try arrayOf(f, aid);
        for (chars, 0..) |c, i| arr.data[i] = .{ .int = c };
        const sid = try heap.allocInstance(str_class);
        const vi = str_class.findField("value") orelse return error.LinkError;
        switch (heap.get(sid).*) {
            .instance => |*inst| inst.fields[vi] = .{ .reference = aid },
            else => return error.LinkError,
        }
        return sid;
    }
    const dup = heap.gpa.dupe(i32, chars) catch return error.OutOfMemory;
    return heap.putString(str_class, dup);
}
fn appendStringObj(out: *std.ArrayList(i32), heap: *Heap, id: u32) RunError!void {
    // Append the chars of a string-like heap object (StringObj or a real
    // char[]-backed java.lang.String instance).
    switch (heap.get(id).*) {
        .string => |st| out.appendSlice(heap.gpa, st.chars) catch return error.OutOfMemory,
        .instance => |inst| {
            if (!std.mem.eql(u8, inst.class.name, "java/lang/String")) return error.UnsupportedOpcode;
            const vi = inst.class.findField("value") orelse return error.LinkError;
            const aid = switch (inst.fields[vi]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            switch (heap.get(aid).*) {
                .array => |arr| for (arr.data) |cv| out.append(heap.gpa, cv.int) catch return error.OutOfMemory,
                else => return error.LinkError,
            }
        },
        else => return error.UnsupportedOpcode,
    }
}
fn createString(f: *Frame, mutf8_bytes: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
    const chars = mutf8ToChars(heap.gpa, mutf8_bytes) catch return error.OutOfMemory;
    defer heap.gpa.free(chars);
    if (str_class.is_stub) {
        // Bootstrap stub path: no interning (keeps the differential suite identical).
        try f.push(.{ .reference = try makeString(f, chars) });
        return;
    }
    try f.push(.{ .reference = try internLiteral(f, chars) });
}
fn internLiteral(f: *Frame, chars: []const i32) RunError!u32 {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const key = heap.gpa.alloc(u8, chars.len * 2) catch return error.OutOfMemory;
    for (chars, 0..) |c, i| {
        const u: u16 = @truncate(@as(u32, @bitCast(c)));
        key[i * 2] = @truncate(u);
        key[i * 2 + 1] = @truncate(u >> 8);
    }
    if (heap.interned.get(key)) |id| {
        heap.gpa.free(key);
        return id;
    }
    const id = try makeString(f, chars);
    heap.interned.put(heap.gpa, key, id) catch {
        heap.gpa.free(key);
        return id;
    };
    return id;
}
fn strChars(heap: *Heap, id: u32) RunError![]i32 {
    return switch (heap.get(id).*) {
        .string => |s| s.chars,
        else => error.LinkError,
    };
}
fn builderRef(heap: *Heap, id: u32) RunError!*BuilderObj {
    return switch (heap.get(id).*) {
        .builder => |*b| b,
        else => error.LinkError,
    };
}
fn builderAppend(heap: *Heap, b: *BuilderObj, chars: []const i32) RunError!void {
    if (b.len + chars.len > b.buf.len) {
        const newcap = @max(b.len + chars.len, b.buf.len * 2 + 8);
        b.buf = heap.gpa.realloc(b.buf, newcap) catch return error.OutOfMemory;
    }
    @memcpy(b.buf[b.len .. b.len + chars.len], chars);
    b.len += chars.len;
}
fn builderIntrinsic(f: *Frame, mname: []const u8, mdesc: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    if (std.mem.eql(u8, mname, "<init>")) {
        const mt = descriptor.parseMethodDescriptor(heap.gpa, mdesc) catch return error.LinkError;
        defer heap.gpa.free(mt.params);
        var tmp: std.ArrayList(i32) = .empty;
        defer tmp.deinit(heap.gpa);
        if (mt.params.len == 1) {
            const v = try f.popKind(fieldKind(mt.params[0]));
            try appendArg(&tmp, heap, v, mt.params[0]);
        } else if (mt.params.len != 0) {
            // e.g. (I) initial-capacity: ignore the capacity
            _ = try f.popKind(fieldKind(mt.params[0]));
        }
        const oid = (try f.popRef()) orelse return error.NullPointer;
        const b = try builderRef(heap, oid);
        if (tmp.items.len > 0) try builderAppend(heap, b, tmp.items);
        return;
    }
    if (std.mem.eql(u8, mname, "append")) {
        const mt = descriptor.parseMethodDescriptor(heap.gpa, mdesc) catch return error.LinkError;
        defer heap.gpa.free(mt.params);
        if (mt.params.len != 1) return error.UnsupportedOpcode;
        var tmp: std.ArrayList(i32) = .empty;
        defer tmp.deinit(heap.gpa);
        const v = try f.popKind(fieldKind(mt.params[0]));
        try appendArg(&tmp, heap, v, mt.params[0]);
        const oid = (try f.popRef()) orelse return error.NullPointer;
        try builderAppend(heap, try builderRef(heap, oid), tmp.items);
        try f.push(.{ .reference = oid }); // append returns this
        return;
    }
    if (eq2(mname, mdesc, "toString", "()Ljava/lang/String;")) {
        const oid = (try f.popRef()) orelse return error.NullPointer;
        const b = try builderRef(heap, oid);
        try f.push(.{ .reference = try newString(f, b.buf[0..b.len]) });
        return;
    }
    if (eq2(mname, mdesc, "length", "()I")) {
        const b = try builderRef(heap, (try f.popRef()) orelse return error.NullPointer);
        return f.pushInt(@intCast(b.len));
    }
    if (eq2(mname, mdesc, "charAt", "(I)C")) {
        const idx = try f.popInt();
        const b = try builderRef(heap, (try f.popRef()) orelse return error.NullPointer);
        if (idx < 0 or idx >= b.len) return error.ArrayIndexOutOfBounds;
        return f.pushInt(b.buf[@intCast(idx)]);
    }
    if (eq2(mname, mdesc, "reverse", "()Ljava/lang/StringBuilder;")) {
        const oid = (try f.popRef()) orelse return error.NullPointer;
        const b = try builderRef(heap, oid);
        std.mem.reverse(i32, b.buf[0..b.len]);
        try f.push(.{ .reference = oid });
        return;
    }
    return error.UnsupportedOpcode;
}

fn pendingException(f: *Frame, name: []const u8) RunError {
    // Allocate a Java exception and hand it to the invoke-site catch, which runs
    // the current frame's handler search. Used by intrinsics (which have no frame
    // class/exception-table of their own).
    const heap = f.heap orelse return error.JavaException;
    const cls = f.loader.find(name) orelse return error.JavaException;
    const eid = heap.allocInstance(cls) catch return error.OutOfMemory;
    f.budget.pending = eid;
    return error.JavaException;
}
fn asciiUpperChar(c: i32) i32 {
    return if (c >= 'a' and c <= 'z') c - 32 else c;
}
fn asciiLowerChar(c: i32) i32 {
    return if (c >= 'A' and c <= 'Z') c + 32 else c;
}
fn isAsciiLetter(c: i32) bool {
    return (c >= 'A' and c <= 'Z') or (c >= 'a' and c <= 'z');
}
fn isJavaWhitespace(c: i32) bool {
    // ASCII subset of java.lang.Character.isWhitespace: HT..CR, FS..US, and SPACE.
    return (c >= 0x09 and c <= 0x0D) or (c >= 0x1C and c <= 0x1F) or c == 0x20;
}
fn charDigit(c: i32, radix: i32) i32 {
    var d: i32 = -1;
    if (c >= '0' and c <= '9') {
        d = c - '0';
    } else if (c >= 'a' and c <= 'z') {
        d = c - 'a' + 10;
    } else if (c >= 'A' and c <= 'Z') {
        d = c - 'A' + 10;
    }
    return if (d >= 0 and d < radix) d else -1;
}
fn characterIntrinsic(f: *Frame, name: []const u8, desc: []const u8) RunError!void {
    if (eq2(name, desc, "valueOf", "(C)Ljava/lang/Character;")) return boxWrapper(f, "java/lang/Character", .{ .int = try f.popInt() });
    if (eq2(name, desc, "toString", "(C)Ljava/lang/String;")) {
        const c = try f.popInt();
        return f.push(.{ .reference = try newString(f, &[_]i32{c}) });
    }
    if (eq2(name, desc, "isDigit", "(C)Z")) {
        const c = try f.popInt();
        return f.pushInt(if (c >= '0' and c <= '9') 1 else 0);
    }
    if (eq2(name, desc, "isLetter", "(C)Z")) {
        const c = try f.popInt();
        return f.pushInt(if (isAsciiLetter(c)) 1 else 0);
    }
    if (eq2(name, desc, "isLetterOrDigit", "(C)Z")) {
        const c = try f.popInt();
        return f.pushInt(if (isAsciiLetter(c) or (c >= '0' and c <= '9')) 1 else 0);
    }
    if (eq2(name, desc, "isWhitespace", "(C)Z")) {
        const c = try f.popInt();
        return f.pushInt(if (isJavaWhitespace(c)) 1 else 0);
    }
    if (eq2(name, desc, "isUpperCase", "(C)Z")) {
        const c = try f.popInt();
        return f.pushInt(if (c >= 'A' and c <= 'Z') 1 else 0);
    }
    if (eq2(name, desc, "isLowerCase", "(C)Z")) {
        const c = try f.popInt();
        return f.pushInt(if (c >= 'a' and c <= 'z') 1 else 0);
    }
    if (eq2(name, desc, "toUpperCase", "(C)C")) return f.pushInt(asciiUpperChar(try f.popInt()));
    if (eq2(name, desc, "toLowerCase", "(C)C")) return f.pushInt(asciiLowerChar(try f.popInt()));
    if (eq2(name, desc, "compare", "(CC)I")) {
        const b = try f.popInt();
        const a = try f.popInt();
        return f.pushInt(a - b);
    }
    if (eq2(name, desc, "digit", "(CI)I")) {
        const radix = try f.popInt();
        const c = try f.popInt();
        return f.pushInt(charDigit(c, radix));
    }
    if (eq2(name, desc, "getNumericValue", "(C)I")) {
        const c = try f.popInt();
        if (c >= '0' and c <= '9') return f.pushInt(c - '0');
        if (isAsciiLetter(c)) return f.pushInt(asciiLowerChar(c) - 'a' + 10);
        return f.pushInt(-1);
    }
    return error.UnsupportedOpcode;
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
    if (eq2(name, desc, "toUpperCase", "()Ljava/lang/String;")) {
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const buf = heap.gpa.alloc(i32, s.len) catch return error.OutOfMemory;
        for (s, 0..) |c, i| buf[i] = asciiUpperChar(c);
        const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
        return f.push(.{ .reference = try heap.putString(str_class, buf) });
    }
    if (eq2(name, desc, "toLowerCase", "()Ljava/lang/String;")) {
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const buf = heap.gpa.alloc(i32, s.len) catch return error.OutOfMemory;
        for (s, 0..) |c, i| buf[i] = asciiLowerChar(c);
        const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
        return f.push(.{ .reference = try heap.putString(str_class, buf) });
    }
    if (eq2(name, desc, "trim", "()Ljava/lang/String;")) {
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var start: usize = 0;
        var end: usize = s.len;
        while (start < end and s[start] <= ' ') start += 1;
        while (end > start and s[end - 1] <= ' ') end -= 1;
        return f.push(.{ .reference = try newString(f, s[start..end]) });
    }
    if (eq2(name, desc, "strip", "()Ljava/lang/String;")) {
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var start: usize = 0;
        var end: usize = s.len;
        while (start < end and isJavaWhitespace(s[start])) start += 1;
        while (end > start and isJavaWhitespace(s[end - 1])) end -= 1;
        return f.push(.{ .reference = try newString(f, s[start..end]) });
    }
    if (eq2(name, desc, "equalsIgnoreCase", "(Ljava/lang/String;)Z")) {
        const other = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var result: i32 = 1;
        if (s.len != other.len) {
            result = 0;
        } else for (s, other) |a, b| {
            if (asciiLowerChar(a) != asciiLowerChar(b)) {
                result = 0;
                break;
            }
        }
        return f.pushInt(result);
    }
    if (eq2(name, desc, "indexOf", "(II)I")) {
        const from = try f.popInt();
        const ch = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var i: usize = if (from < 0) 0 else @intCast(from);
        while (i < s.len) : (i += 1) if (s[i] == ch) return f.pushInt(@intCast(i));
        return f.pushInt(-1);
    }
    if (eq2(name, desc, "lastIndexOf", "(I)I")) {
        const ch = try f.popInt();
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        var i: usize = s.len;
        while (i > 0) {
            i -= 1;
            if (s[i] == ch) return f.pushInt(@intCast(i));
        }
        return f.pushInt(-1);
    }
    if (eq2(name, desc, "repeat", "(I)Ljava/lang/String;")) {
        const count = try f.popInt();
        if (count < 0) return pendingException(f, "java/lang/IllegalArgumentException");
        const s = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const n: usize = @intCast(count);
        const buf = heap.gpa.alloc(i32, s.len * n) catch return error.OutOfMemory;
        var k: usize = 0;
        while (k < n) : (k += 1) @memcpy(buf[k * s.len ..][0..s.len], s);
        const str_class = f.loader.find("java/lang/String") orelse return error.LinkError;
        return f.push(.{ .reference = try heap.putString(str_class, buf) });
    }
    if (eq2(name, desc, "toCharArray", "()[C")) {
        const rid = (try f.popRef()) orelse return error.NullPointer;
        const len = (try strChars(heap, rid)).len;
        const aid = try heap.allocArray(.int, len);
        const arr = try arrayOf(f, aid);
        const src = try strChars(heap, rid); // re-fetch: allocArray may have moved the string
        for (src, 0..) |c, i| arr.data[i] = .{ .int = c };
        return f.push(.{ .reference = aid });
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
    // Representation-aware: real char[]-backed String when String is the real
    // loaded class, else a StringObj for the bootstrap stub. All string producers
    // route through here so a jbase run never mixes StringObj into real String.
    return makeString(f, chars);
}

fn lessThanInt(_: void, a: Value, b: Value) bool {
    return a.int < b.int;
}
fn lessThanLong(_: void, a: Value, b: Value) bool {
    return a.long < b.long;
}
fn cloneArray(f: *Frame, oid: u32) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const src0 = try arrayOf(f, oid);
    const elem = src0.elem;
    const len = src0.data.len;
    const new_id = heap.allocArray(elem, len) catch return error.OutOfMemory;
    const src = try arrayOf(f, oid); // re-fetch: allocArray may have moved objects
    const dst = try arrayOf(f, new_id);
    @memcpy(dst.data, src.data);
    try f.push(.{ .reference = new_id });
}
fn arrayOf(f: *Frame, id: u32) RunError!*Array {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    return switch (heap.get(id).*) {
        .array => |*a| a,
        else => error.LinkError,
    };
}
fn callComparator(f: *Frame, cmp_ref: u32, a: Value, b: Value) RunError!i32 {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const lam = switch (heap.get(cmp_ref).*) {
        .lambda => |l| l,
        else => return error.TypeMismatch, // only lambda/method-ref comparators for now
    };
    const slots = [_]Value{ a, b };
    const params = [_]Class.Param{
        .{ .kind = .reference, .slot = 0 },
        .{ .kind = .reference, .slot = 1 },
    };
    try dispatchLambda(f, lam, &slots, &params);
    return f.popInt();
}
fn sortWithComparator(f: *Frame, arr: []Value, cmp_ref: u32) RunError!void {
    // Stable binary-free insertion sort; re-enters the interpreter for each compare.
    // Java's Arrays.sort(Object[],Comparator) is stable, so keeping equal elements in
    // input order (compare > 0 only) reproduces its observable output.
    var i: usize = 1;
    while (i < arr.len) : (i += 1) {
        const key = arr[i];
        var j: usize = i;
        while (j > 0) {
            const c = try callComparator(f, cmp_ref, arr[j - 1], key);
            if (c > 0) {
                arr[j] = arr[j - 1];
                j -= 1;
            } else break;
        }
        arr[j] = key;
    }
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
    if (eq2(name, desc, "sort", "([Ljava/lang/Object;Ljava/util/Comparator;)V")) {
        const cmp_ref = (try f.popRef()) orelse return error.NullPointer;
        const arr = try arrayOf(f, (try f.popRef()) orelse return error.NullPointer);
        return sortWithComparator(f, arr.data, cmp_ref);
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

fn boxWrapper(f: *Frame, name: []const u8, value: Value) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const cls = f.loader.find(name) orelse return error.LinkError;
    try f.push(.{ .reference = try heap.putBoxed(cls, value) });
}
fn parseIntChars(chars: []const i32) ?i64 {
    if (chars.len == 0) return null;
    var i: usize = 0;
    var neg = false;
    if (chars[0] == '-') {
        neg = true;
        i = 1;
    } else if (chars[0] == '+') {
        i = 1;
    }
    if (i >= chars.len) return null;
    var v: i64 = 0;
    while (i < chars.len) : (i += 1) {
        if (chars[i] < '0' or chars[i] > '9') return null;
        v = v * 10 + (chars[i] - '0');
    }
    return if (neg) -v else v;
}
fn valueToInt(v: Value) i32 {
    return switch (v) {
        .int => |x| x,
        .long => |x| @truncate(x),
        .double => |x| f2i(x),
        .float => |x| f2i(x),
        else => 0,
    };
}
fn valueToLong(v: Value) i64 {
    return switch (v) {
        .int => |x| x,
        .long => |x| x,
        .double => |x| f2l(x),
        .float => |x| f2l(x),
        else => 0,
    };
}
fn valueToDouble(v: Value) f64 {
    return switch (v) {
        .int => |x| @floatFromInt(x),
        .long => |x| @floatFromInt(x),
        .double => |x| x,
        .float => |x| x,
        else => 0,
    };
}
fn valueToFloat(v: Value) f32 {
    return switch (v) {
        .int => |x| @floatFromInt(x),
        .long => |x| @floatFromInt(x),
        .double => |x| @floatCast(x),
        .float => |x| x,
        else => 0,
    };
}
fn valueEquals(a: Value, b: Value) bool {
    return switch (a) {
        .int => |x| switch (b) {
            .int => |y| x == y,
            else => false,
        },
        .long => |x| switch (b) {
            .long => |y| x == y,
            else => false,
        },
        .double => |x| switch (b) {
            .double => |y| x == y,
            else => false,
        },
        .float => |x| switch (b) {
            .float => |y| x == y,
            else => false,
        },
        else => false,
    };
}
fn boxHash(bo: BoxedObj) i32 {
    if (std.mem.eql(u8, bo.class.name, "java/lang/Boolean")) return if (bo.value.int != 0) 1231 else 1237;
    if (std.mem.eql(u8, bo.class.name, "java/lang/Long")) {
        const u: u64 = @bitCast(bo.value.long);
        return @bitCast(@as(u32, @truncate(u ^ (u >> 32))));
    }
    if (std.mem.eql(u8, bo.class.name, "java/lang/Double")) {
        const u: u64 = @bitCast(bo.value.double);
        return @bitCast(@as(u32, @truncate(u ^ (u >> 32))));
    }
    return valueToInt(bo.value);
}
fn floatString(f: *Frame, comptime T: type, value: T) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    var tmp: std.ArrayList(i32) = .empty;
    defer tmp.deinit(heap.gpa);
    try appendJavaFloat(&tmp, heap.gpa, T, value);
    try f.push(.{ .reference = try newString(f, tmp.items) });
}
fn floatStringInt(f: *Frame, value: i64) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    var tmp: std.ArrayList(i32) = .empty;
    defer tmp.deinit(heap.gpa);
    try appendDecimalLong(&tmp, heap.gpa, value);
    try f.push(.{ .reference = try newString(f, tmp.items) });
}
fn boxStatic(f: *Frame, owner: []const u8, name: []const u8, desc: []const u8) RunError!void {
    if (std.mem.eql(u8, owner, "java/lang/Double")) {
        if (eq2(name, desc, "valueOf", "(D)Ljava/lang/Double;")) return boxWrapper(f, owner, .{ .double = try f.popDouble() });
        if (eq2(name, desc, "toString", "(D)Ljava/lang/String;")) return floatString(f, f64, try f.popDouble());
    } else if (std.mem.eql(u8, owner, "java/lang/Float")) {
        if (eq2(name, desc, "valueOf", "(F)Ljava/lang/Float;")) return boxWrapper(f, owner, .{ .float = try f.popFloat() });
        if (eq2(name, desc, "toString", "(F)Ljava/lang/String;")) return floatString(f, f32, try f.popFloat());
    } else if (std.mem.eql(u8, owner, "java/lang/Boolean")) {
        if (eq2(name, desc, "valueOf", "(Z)Ljava/lang/Boolean;")) return boxWrapper(f, owner, .{ .int = try f.popInt() });
    } else if (std.mem.eql(u8, owner, "java/lang/Character")) {
        if (eq2(name, desc, "valueOf", "(C)Ljava/lang/Character;")) return boxWrapper(f, owner, .{ .int = try f.popInt() });
    } else if (std.mem.eql(u8, owner, "java/lang/Short")) {
        if (eq2(name, desc, "valueOf", "(S)Ljava/lang/Short;")) return boxWrapper(f, owner, .{ .int = try f.popInt() });
    } else if (std.mem.eql(u8, owner, "java/lang/Byte")) {
        if (eq2(name, desc, "valueOf", "(B)Ljava/lang/Byte;")) return boxWrapper(f, owner, .{ .int = try f.popInt() });
    }
    return error.UnsupportedOpcode;
}
fn boxedMethod(f: *Frame, oid: u32, sam_slots: []const Value, sam_params: []const Class.Param, mname: []const u8, mdesc: []const u8) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const bo = switch (heap.get(oid).*) {
        .boxed => |b| b,
        else => return error.LinkError,
    };
    const bv = bo.value;
    if (eq2(mname, mdesc, "intValue", "()I")) return f.pushInt(valueToInt(bv));
    if (eq2(mname, mdesc, "longValue", "()J")) return f.pushLong(valueToLong(bv));
    if (eq2(mname, mdesc, "doubleValue", "()D")) return f.pushDouble(valueToDouble(bv));
    if (eq2(mname, mdesc, "floatValue", "()F")) return f.pushFloat(valueToFloat(bv));
    if (eq2(mname, mdesc, "shortValue", "()S")) return f.pushInt(@as(i16, @truncate(valueToInt(bv))));
    if (eq2(mname, mdesc, "byteValue", "()B")) return f.pushInt(@as(i8, @truncate(valueToInt(bv))));
    if (eq2(mname, mdesc, "booleanValue", "()Z")) return f.pushInt(bv.int);
    if (eq2(mname, mdesc, "charValue", "()C")) return f.pushInt(bv.int);
    if (eq2(mname, mdesc, "hashCode", "()I")) return f.pushInt(boxHash(bo));
    if (eq2(mname, mdesc, "equals", "(Ljava/lang/Object;)Z")) {
        const other = sam_slots[sam_params[0].slot];
        var eq: i32 = 0;
        switch (other) {
            .reference => |r| if (r) |oidr| switch (heap.get(oidr).*) {
                .boxed => |ob| if (std.mem.eql(u8, ob.class.name, bo.class.name) and valueEquals(ob.value, bv)) {
                    eq = 1;
                },
                else => {},
            },
            else => {},
        }
        return f.pushInt(eq);
    }
    if (eq2(mname, mdesc, "toString", "()Ljava/lang/String;")) {
        var tmp: std.ArrayList(i32) = .empty;
        defer tmp.deinit(heap.gpa);
        if (std.mem.eql(u8, bo.class.name, "java/lang/Boolean")) {
            try appendAscii(&tmp, heap.gpa, if (bv.int != 0) "true" else "false");
        } else if (std.mem.eql(u8, bo.class.name, "java/lang/Character")) {
            try tmp.append(heap.gpa, bv.int);
        } else if (std.mem.eql(u8, bo.class.name, "java/lang/Long")) {
            try appendDecimalLong(&tmp, heap.gpa, bv.long);
        } else if (std.mem.eql(u8, bo.class.name, "java/lang/Double")) {
            try appendJavaFloat(&tmp, heap.gpa, f64, bv.double);
        } else if (std.mem.eql(u8, bo.class.name, "java/lang/Float")) {
            try appendJavaFloat(&tmp, heap.gpa, f32, bv.float);
        } else {
            try appendDecimalInt(&tmp, heap.gpa, valueToInt(bv));
        }
        return f.push(.{ .reference = try newString(f, tmp.items) });
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
    if (eq2(name, desc, "valueOf", "(I)Ljava/lang/Integer;")) return boxWrapper(f, "java/lang/Integer", .{ .int = try f.popInt() });
    if (eq2(name, desc, "parseInt", "(Ljava/lang/String;)I")) {
        const heap = f.heap orelse return error.UnsupportedOpcode;
        const chars = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        const v = parseIntChars(chars) orelse return error.LinkError;
        return f.pushInt(@truncate(v));
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
    if (eq2(name, desc, "valueOf", "(J)Ljava/lang/Long;")) return boxWrapper(f, "java/lang/Long", .{ .long = try f.popLong() });
    if (eq2(name, desc, "parseLong", "(Ljava/lang/String;)J")) {
        const heap = f.heap orelse return error.UnsupportedOpcode;
        const chars = try strChars(heap, (try f.popRef()) orelse return error.NullPointer);
        return f.pushLong(parseIntChars(chars) orelse return error.LinkError);
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

/// Native-method registry: implementations for ACC_NATIVE methods in loaded
/// classes (our clean-room java.base). Dispatched by (owner, name, descriptor)
/// after normal method resolution. `slots` holds the arguments (slot 0 is the
/// receiver for instance methods). Returns error.UnsupportedOpcode for an
/// unregistered native (a genuine "not implemented", surfaced loudly).
fn writeStringToFd(f: *Frame, fd: i32, sid: u32) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    var chars: std.ArrayList(i32) = .empty;
    defer chars.deinit(heap.gpa);
    try appendStringObj(&chars, heap, sid);
    var bytes: std.ArrayList(u8) = .empty;
    defer bytes.deinit(heap.gpa);
    var ebuf: [4]u8 = undefined;
    for (chars.items) |c| {
        const cp: u21 = @intCast(@as(u32, @bitCast(c)) & 0xFFFF);
        if (cp >= 0xD800 and cp <= 0xDFFF) {
            bytes.append(heap.gpa, '?') catch return error.OutOfMemory;
            continue;
        }
        const n = std.unicode.utf8Encode(cp, &ebuf) catch {
            bytes.append(heap.gpa, '?') catch return error.OutOfMemory;
            continue;
        };
        bytes.appendSlice(heap.gpa, ebuf[0..n]) catch return error.OutOfMemory;
    }
    const io = f.loader.io orelse return; // no IO bound (unit tests): drop output
    const file = if (fd == 2) std.Io.File.stderr() else std.Io.File.stdout();
    file.writeStreamingAll(io, bytes.items) catch {};
}
fn classOf(heap: *Heap, id: u32) ?*const Class {
    return switch (heap.get(id).*) {
        .instance => |x| x.class,
        .string => |x| x.class,
        .boxed => |x| x.class,
        .builder => |x| x.class,
        else => null, // array / lambda: no user-visible class yet
    };
}
fn getMirror(f: *Frame, cls: *const Class) RunError!u32 {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const idx = f.loader.indexOf(cls) orelse return error.LinkError;
    if (f.loader.mirrors.items[idx]) |id| return id;
    const class_class = f.loader.find("java/lang/Class") orelse (try f.loader.loadFromClasspath("java/lang/Class")) orelse return error.LinkError;
    const mid = try heap.allocInstance(class_class);
    const vi = class_class.findField("vmIndex") orelse return error.LinkError;
    switch (heap.get(mid).*) {
        .instance => |*inst| inst.fields[vi] = .{ .int = @intCast(idx) },
        else => return error.LinkError,
    }
    f.loader.mirrors.items[idx] = mid;
    return mid;
}
fn mirrorClass(f: *Frame, mirror_id: u32) RunError!*const Class {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const class_class = f.loader.find("java/lang/Class") orelse return error.LinkError;
    const vi = class_class.findField("vmIndex") orelse return error.LinkError;
    const idx: usize = switch (heap.get(mirror_id).*) {
        .instance => |inst| @intCast(inst.fields[vi].int),
        else => return error.LinkError,
    };
    if (idx >= f.loader.classes.items.len) return error.LinkError;
    return f.loader.classes.items[idx];
}
fn pushDottedName(f: *Frame, internal: []const u8, simple: bool) RunError!void {
    // internal form uses '/'; Java getName uses '.'. simple=last component only.
    const heap = f.heap orelse return error.UnsupportedOpcode;
    var start: usize = 0;
    if (simple) {
        var i: usize = internal.len;
        while (i > 0) : (i -= 1) {
            if (internal[i - 1] == '/') {
                start = i;
                break;
            }
        }
    }
    var tmp: std.ArrayList(i32) = .empty;
    defer tmp.deinit(heap.gpa);
    for (internal[start..]) |c| tmp.append(heap.gpa, if (c == '/') '.' else c) catch return error.OutOfMemory;
    try f.push(.{ .reference = try makeString(f, tmp.items) });
}
const MemberRef = struct { class_idx: usize, member_idx: usize };
fn isCtorOrClinit(name: []const u8) bool {
    return std.mem.eql(u8, name, "<init>") or std.mem.eql(u8, name, "<clinit>");
}
fn stringFromBytes(f: *Frame, bytes: []const u8) RunError!u32 {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const chars = mutf8ToChars(heap.gpa, bytes) catch return error.OutOfMemory;
    defer heap.gpa.free(chars);
    return makeString(f, chars);
}
fn memberMirror(f: *Frame, member_class_name: []const u8, class_idx: usize, member_idx: usize) RunError!u32 {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const mc = f.loader.find(member_class_name) orelse return error.LinkError;
    const id = try heap.allocInstance(mc);
    const ci = mc.findField("vmClassIndex") orelse return error.LinkError;
    const vi = mc.findField("vmIndex") orelse return error.LinkError;
    switch (heap.get(id).*) {
        .instance => |*inst| {
            inst.fields[ci] = .{ .int = @intCast(class_idx) };
            inst.fields[vi] = .{ .int = @intCast(member_idx) };
        },
        else => return error.LinkError,
    }
    return id;
}
fn memberIndices(f: *Frame, mirror_id: u32) RunError!MemberRef {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const inst = switch (heap.get(mirror_id).*) {
        .instance => |x| x,
        else => return error.LinkError,
    };
    const ci = inst.class.findField("vmClassIndex") orelse return error.LinkError;
    const vi = inst.class.findField("vmIndex") orelse return error.LinkError;
    return .{ .class_idx = @intCast(inst.fields[ci].int), .member_idx = @intCast(inst.fields[vi].int) };
}
fn returnDescChar(desc: []const u8) u8 {
    const rp = std.mem.indexOfScalar(u8, desc, ')') orelse return 'V';
    if (rp + 1 >= desc.len) return 'V';
    return desc[rp + 1];
}
fn convertArgToValue(f: *Frame, argval: Value, kind: Kind) RunError!Value {
    if (kind == .reference) return argval;
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const wid = switch (argval) {
        .reference => |r| r orelse return error.NullPointer,
        else => return argval,
    };
    const inst = switch (heap.get(wid).*) {
        .instance => |x| x,
        else => return error.TypeMismatch,
    };
    const vi = inst.class.findField("value") orelse return error.TypeMismatch;
    return inst.fields[vi];
}
fn boxValueForDesc(f: *Frame, rc: u8, value: Value) RunError!u32 {
    const wname = switch (rc) {
        'I' => "java/lang/Integer",
        'J' => "java/lang/Long",
        'D' => "java/lang/Double",
        'F' => "java/lang/Float",
        'Z' => "java/lang/Boolean",
        'C' => "java/lang/Character",
        'S' => "java/lang/Short",
        'B' => "java/lang/Byte",
        else => return error.UnsupportedOpcode,
    };
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const wc = f.loader.find(wname) orelse return error.LinkError;
    const id = try heap.allocInstance(wc);
    const vi = wc.findField("value") orelse return error.LinkError;
    switch (heap.get(id).*) {
        .instance => |*inst| inst.fields[vi] = value,
        else => return error.LinkError,
    }
    return id;
}
fn boxAnnoVal(f: *Frame, v: AnnoVal) RunError!Value {
    return switch (v) {
        .none => .{ .reference = null },
        .int => |x| .{ .reference = try boxValueForDesc(f, 'I', .{ .int = x }) },
        .long => |x| .{ .reference = try boxValueForDesc(f, 'J', .{ .long = x }) },
        .float => |x| .{ .reference = try boxValueForDesc(f, 'F', .{ .float = x }) },
        .double => |x| .{ .reference = try boxValueForDesc(f, 'D', .{ .double = x }) },
        .string => |b| .{ .reference = try stringFromBytes(f, b) },
    };
}
fn reflectGetAnnotation(f: *Frame, class_mirror: u32, anno_mirror: u32) RunError!void {
    const cls = try mirrorClass(f, class_mirror);
    return buildAnnotationProxy(f, cls.annotations, anno_mirror);
}
fn buildAnnotationProxy(f: *Frame, annos: []const AnnotationInfo, anno_mirror: u32) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const anno_cls = try mirrorClass(f, anno_mirror);
    var found: ?AnnotationInfo = null;
    for (annos) |ai| {
        if (std.mem.eql(u8, ai.name, anno_cls.name)) {
            found = ai;
            break;
        }
    }
    const ai = found orelse return f.push(.{ .reference = null });
    // pairs Object[] = [name0, val0, name1, val1, ...]
    const n = ai.elements.len;
    const pairs_id = try heap.allocArray(.reference, 2 * n);
    const vals = f.loader.gpa.alloc(Value, 2 * n) catch return error.OutOfMemory;
    defer f.loader.gpa.free(vals);
    for (ai.elements, 0..) |el, i| {
        vals[2 * i] = .{ .reference = try stringFromBytes(f, el.name) };
        vals[2 * i + 1] = try boxAnnoVal(f, el.value);
    }
    const pa = try arrayOf(f, pairs_id);
    for (vals, 0..) |v, i| pa.data[i] = v;
    // handler = AnnotationInvocationHandler(pairs)
    const hc = f.loader.find("java/lang/reflect/AnnotationInvocationHandler") orelse return error.LinkError;
    const hid = try heap.allocInstance(hc);
    const pfi = hc.findField("pairs") orelse return error.LinkError;
    switch (heap.get(hid).*) {
        .instance => |*inst| inst.fields[pfi] = .{ .reference = pairs_id },
        else => return error.LinkError,
    }
    // proxy over [anno interface]
    const ifn = f.loader.gpa.alloc([]const u8, 1) catch return error.OutOfMemory;
    ifn[0] = anno_cls.name;
    const pc = try makeProxyClass(f, ifn);
    const proxy_id = try heap.allocInstance(pc);
    const hfi = pc.findField("h") orelse return error.LinkError;
    switch (heap.get(proxy_id).*) {
        .instance => |*inst| inst.fields[hfi] = .{ .reference = hid },
        else => return error.LinkError,
    }
    return f.push(.{ .reference = proxy_id });
}
fn reflectGetMembers(f: *Frame, class_mirror: u32, comptime kind: enum { methods, fields, ctors }) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const cls = try mirrorClass(f, class_mirror);
    const class_idx = f.loader.indexOf(cls) orelse return error.LinkError;
    const mirror_class = switch (kind) {
        .methods => "java/lang/reflect/Method",
        .fields => "java/lang/reflect/Field",
        .ctors => "java/lang/reflect/Constructor",
    };
    // count + collect member indices first (no held array pointer across allocs)
    var idxs: std.ArrayList(usize) = .empty;
    defer idxs.deinit(heap.gpa);
    switch (kind) {
        .fields => for (cls.instance_fields, 0..) |_, i| idxs.append(heap.gpa, i) catch return error.OutOfMemory,
        .methods => for (cls.methods, 0..) |m, i| {
            if (!isCtorOrClinit(m.name)) idxs.append(heap.gpa, i) catch return error.OutOfMemory;
        },
        .ctors => for (cls.methods, 0..) |m, i| {
            if (std.mem.eql(u8, m.name, "<init>")) idxs.append(heap.gpa, i) catch return error.OutOfMemory;
        },
    }
    const mirrors = heap.gpa.alloc(u32, idxs.items.len) catch return error.OutOfMemory;
    defer heap.gpa.free(mirrors);
    for (idxs.items, 0..) |mi, i| mirrors[i] = try memberMirror(f, mirror_class, class_idx, mi);
    const aid = try heap.allocArray(.reference, idxs.items.len);
    const arr = try arrayOf(f, aid);
    for (mirrors, 0..) |mid, i| arr.data[i] = .{ .reference = mid };
    return f.push(.{ .reference = aid });
}
fn reflectInvoke(f: *Frame, method_mirror: u32, slots: []const Value) RunError!void {
    const ref = try memberIndices(f, method_mirror);
    const owner = f.loader.classes.items[ref.class_idx];
    const method = &owner.methods[ref.member_idx];
    const cc = method.code orelse return error.LinkError;
    const islots = owner.gpa.alloc(Value, method.arg_slots) catch return error.OutOfMemory;
    defer owner.gpa.free(islots);
    for (islots) |*sv| sv.* = .{ .int = 0 };
    if (!method.is_static) islots[0] = slots[1];
    const args_arr: ?*Array = switch (slots[2]) {
        .reference => |r| if (r) |aid| (arrayOf(f, aid) catch null) else null,
        else => null,
    };
    for (method.params, 0..) |p, i| {
        const argval: Value = if (args_arr) |aa| (if (i < aa.data.len) aa.data[i] else Value{ .int = 0 }) else Value{ .int = 0 };
        islots[p.slot] = try convertArgToValue(f, argval, p.kind);
        if (p.kind == .long or p.kind == .double) islots[p.slot + 1] = .top;
    }
    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    const ret = try exec(owner.gpa, owner, f.heap, f.loader, f.budget, cc.code, cc.max_stack, cc.max_locals, islots, cc.exception_table, f);
    const rc = returnDescChar(method.descriptor);
    if (rc == 'V') return f.push(.{ .reference = null });
    if (rc == 'L' or rc == '[') return f.push(ret orelse Value{ .reference = null });
    return f.push(.{ .reference = try boxValueForDesc(f, rc, ret orelse Value{ .int = 0 }) });
}
fn reflectFieldGet(f: *Frame, field_mirror: u32, slots: []const Value) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const ref = try memberIndices(f, field_mirror);
    const dcls = f.loader.classes.items[ref.class_idx];
    const fld = dcls.instance_fields[ref.member_idx];
    const obj = switch (slots[1]) {
        .reference => |r| r orelse return error.NullPointer,
        else => return error.TypeMismatch,
    };
    const inst = switch (heap.get(obj).*) {
        .instance => |x| x,
        else => return error.LinkError,
    };
    const fi = inst.class.findField(fld.name) orelse return error.LinkError;
    const v = inst.fields[fi];
    if (fld.kind == .reference) return f.push(v);
    const rc: u8 = switch (fld.kind) {
        .int => 'I',
        .long => 'J',
        .double => 'D',
        .float => 'F',
        .reference => unreachable,
    };
    return f.push(.{ .reference = try boxValueForDesc(f, rc, v) });
}
fn reflectFieldSet(f: *Frame, field_mirror: u32, slots: []const Value) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const ref = try memberIndices(f, field_mirror);
    const dcls = f.loader.classes.items[ref.class_idx];
    const fld = dcls.instance_fields[ref.member_idx];
    const obj = switch (slots[1]) {
        .reference => |r| r orelse return error.NullPointer,
        else => return error.TypeMismatch,
    };
    const converted = if (fld.kind == .reference) slots[2] else try convertArgToValue(f, slots[2], fld.kind);
    switch (heap.get(obj).*) {
        .instance => |*inst| {
            const fi = inst.class.findField(fld.name) orelse return error.LinkError;
            inst.fields[fi] = converted;
        },
        else => return error.LinkError,
    }
    writeBarrier(heap, obj, converted);
}
fn reflectNewInstance(f: *Frame, ctor_mirror: u32, slots: []const Value) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const ref = try memberIndices(f, ctor_mirror);
    const owner = f.loader.classes.items[ref.class_idx];
    const ctor = &owner.methods[ref.member_idx];
    const cc = ctor.code orelse return error.LinkError;
    const id = try heap.allocInstance(owner);
    const islots = owner.gpa.alloc(Value, ctor.arg_slots) catch return error.OutOfMemory;
    defer owner.gpa.free(islots);
    for (islots) |*sv| sv.* = .{ .int = 0 };
    islots[0] = .{ .reference = id };
    const args_arr: ?*Array = switch (slots[1]) {
        .reference => |r| if (r) |aid| (arrayOf(f, aid) catch null) else null,
        else => null,
    };
    for (ctor.params, 0..) |p, i| {
        const argval: Value = if (args_arr) |aa| (if (i < aa.data.len) aa.data[i] else Value{ .int = 0 }) else Value{ .int = 0 };
        islots[p.slot] = try convertArgToValue(f, argval, p.kind);
        if (p.kind == .long or p.kind == .double) islots[p.slot + 1] = .top;
    }
    if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
    f.budget.depth += 1;
    defer f.budget.depth -= 1;
    _ = try exec(owner.gpa, owner, f.heap, f.loader, f.budget, cc.code, cc.max_stack, cc.max_locals, islots, cc.exception_table, f);
    return f.push(.{ .reference = id });
}
fn kindFromDescChar(rc: u8) Kind {
    return switch (rc) {
        'J' => .long,
        'D' => .double,
        'F' => .float,
        else => .int,
    };
}
fn boxArg(f: *Frame, val: Value, kind: Kind) RunError!Value {
    if (kind == .reference) return val;
    const rc: u8 = switch (kind) {
        .long => 'J',
        .double => 'D',
        .float => 'F',
        else => 'I',
    };
    return .{ .reference = try boxValueForDesc(f, rc, val) };
}
fn primitiveClass(f: *Frame, name: []const u8) RunError!*const Class {
    if (f.loader.find(name)) |c| return c;
    const gpa = f.loader.gpa;
    const nm = gpa.dupe(u8, name) catch return error.OutOfMemory;
    const pc = gpa.create(Class) catch return error.OutOfMemory;
    pc.* = Class{
        .gpa = gpa,
        .cp = .{ .entries = &stub_cp_entries },
        .name = nm,
        .super = null,
        .super_name = null,
        .interfaces = &.{},
        .methods = &.{},
        .instance_fields = &.{},
        .static_fields = &.{},
        .bootstrap_methods = &.{},
        .is_stub = false,
        .is_primitive = true,
    };
    f.loader.register(pc) catch return error.OutOfMemory;
    return pc;
}
fn makeProxyClass(f: *Frame, iface_names: [][]const u8) RunError!*const Class {
    const gpa = f.loader.gpa;
    const obj = f.loader.find("java/lang/Object") orelse return error.LinkError;
    const methods = gpa.alloc(Class.Method, 1) catch return error.OutOfMemory;
    methods[0] = .{
        .name = "<init>",
        .descriptor = "()V",
        .code = .{ .max_stack = 0, .max_locals = 1, .code = &stub_return_code, .exception_table = &.{}, .attributes = &.{} },
        .is_native = false,
        .params = &.{},
        .arg_slots = 1,
        .ret = null,
        .is_static = false,
    };
    const fields = gpa.alloc(Class.Field, 1) catch return error.OutOfMemory;
    fields[0] = .{ .name = "h", .kind = .reference };
    const name = std.fmt.allocPrint(gpa, "$Proxy{d}", .{f.loader.classes.items.len}) catch return error.OutOfMemory;
    const pc = gpa.create(Class) catch return error.OutOfMemory;
    pc.* = Class{
        .gpa = gpa,
        .cp = .{ .entries = &stub_cp_entries },
        .name = name,
        .super = obj,
        .super_name = "java/lang/Object",
        .interfaces = iface_names,
        .methods = methods,
        .instance_fields = fields,
        .static_fields = &.{},
        .bootstrap_methods = &.{},
        .is_stub = false,
        .is_proxy = true,
    };
    f.loader.register(pc) catch return error.OutOfMemory;
    return pc;
}
fn invokeHandler(f: *Frame, handler_id: u32, proxy_val: Value, method_val: Value, args_val: Value) RunError!Value {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    switch (heap.get(handler_id).*) {
        .lambda => |lam| {
            const ss = [_]Value{ proxy_val, method_val, args_val };
            const pp = [_]Class.Param{
                .{ .kind = .reference, .slot = 0 },
                .{ .kind = .reference, .slot = 1 },
                .{ .kind = .reference, .slot = 2 },
            };
            try dispatchLambda(f, lam, &ss, &pp);
            return f.pop();
        },
        .instance => |inst| {
            const tr = inst.class.resolve("invoke", "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;") orelse return error.MethodNotFound;
            try runImplInstance(f, tr.owner, tr.method, handler_id, &[_]Value{ proxy_val, method_val, args_val });
            return f.pop();
        },
        else => return error.LinkError,
    }
}
fn proxyDispatch(f: *Frame, proxy_id: u32, iface_name: []const u8, mname: []const u8, mdesc: []const u8, slots: []const Value, params: []const Class.Param) RunError!void {
    const heap = f.heap orelse return error.UnsupportedOpcode;
    const pinst = switch (heap.get(proxy_id).*) {
        .instance => |x| x,
        else => return error.LinkError,
    };
    // handler first (before any allocation)
    const hi = pinst.class.findField("h") orelse return error.LinkError;
    const handler_id = switch (pinst.fields[hi]) {
        .reference => |r| r orelse return error.NullPointer,
        else => return error.TypeMismatch,
    };
    // locate declaring interface + method index
    var decl: ?*const Class = null;
    var method_idx: usize = 0;
    if (f.loader.find(iface_name)) |ic| {
        for (ic.methods, 0..) |m, i| {
            if (eq2(m.name, m.descriptor, mname, mdesc)) {
                decl = ic;
                method_idx = i;
                break;
            }
        }
    }
    if (decl == null) {
        for (pinst.class.interfaces) |ifn| {
            if (f.loader.find(ifn)) |ic| {
                for (ic.methods, 0..) |m, i| {
                    if (eq2(m.name, m.descriptor, mname, mdesc)) {
                        decl = ic;
                        method_idx = i;
                        break;
                    }
                }
            }
            if (decl != null) break;
        }
    }
    const dc = decl orelse return error.MethodNotFound;
    const class_idx = f.loader.indexOf(dc) orelse return error.LinkError;
    const method_mirror = try memberMirror(f, "java/lang/reflect/Method", class_idx, method_idx);
    // box args into Object[]
    const args_id = try heap.allocArray(.reference, params.len);
    const boxed = f.loader.gpa.alloc(Value, params.len) catch return error.OutOfMemory;
    defer f.loader.gpa.free(boxed);
    for (params, 0..) |p, i| boxed[i] = try boxArg(f, slots[p.slot], p.kind);
    const aa = try arrayOf(f, args_id);
    for (boxed, 0..) |bv, i| aa.data[i] = bv;
    // dispatch to handler
    const result = try invokeHandler(f, handler_id, .{ .reference = proxy_id }, .{ .reference = method_mirror }, .{ .reference = args_id });
    // unbox / return per the interface method's return type
    const rc = returnDescChar(mdesc);
    if (rc == 'V') return;
    if (rc == 'L' or rc == '[') return f.push(result);
    return f.pushKind(try convertArgToValue(f, result, kindFromDescChar(rc)));
}
fn nativeInvoke(f: *Frame, owner: *const Class, method: *const Class.Method, slots: []const Value) RunError!void {
    const on = owner.name;
    const mn = method.name;
    const md = method.descriptor;
    if (std.mem.eql(u8, on, "java/lang/Object")) {
        if (eq2(mn, md, "getClass", "()Ljava/lang/Class;")) {
            const recv = switch (slots[0]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const rc = classOf(heap, recv) orelse return error.LinkError;
            return f.push(.{ .reference = try getMirror(f, rc) });
        }
        if (eq2(mn, md, "identityHashCode", "(Ljava/lang/Object;)I")) {
            const arg = slots[method.params[0].slot];
            const r = switch (arg) {
                .reference => |x| x,
                else => null,
            };
            return f.pushInt(if (r) |id| @bitCast(id) else 0);
        }
    }
    if (std.mem.eql(u8, on, "java/io/PrintStream")) {
        if (eq2(mn, md, "writeString", "(ILjava/lang/String;)V")) {
            const fd = slots[method.params[0].slot].int;
            const sref = switch (slots[method.params[1].slot]) {
                .reference => |r| r orelse return,
                else => return error.TypeMismatch,
            };
            return writeStringToFd(f, fd, sref);
        }
    }
    if (std.mem.eql(u8, on, "java/lang/System")) {
        if (eq2(mn, md, "currentTimeMillis", "()J")) {
            const io = f.loader.io orelse return f.pushLong(0);
            return f.pushLong(std.Io.Clock.now(.real, io).toMilliseconds());
        }
        if (eq2(mn, md, "nanoTime", "()J")) {
            const io = f.loader.io orelse return f.pushLong(0);
            return f.pushLong(@truncate(std.Io.Clock.now(.awake, io).toNanoseconds()));
        }
        if (eq2(mn, md, "identityHashCode", "(Ljava/lang/Object;)I")) {
            const r = switch (slots[method.params[0].slot]) {
                .reference => |x| x,
                else => null,
            };
            return f.pushInt(if (r) |id| @bitCast(id) else 0);
        }
        if (eq2(mn, md, "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const src = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const src_pos = slots[method.params[1].slot].int;
            const dest = switch (slots[method.params[2].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const dest_pos = slots[method.params[3].slot].int;
            const length = slots[method.params[4].slot].int;
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
    }
    if (std.mem.eql(u8, on, "java/lang/reflect/Proxy")) {
        if (eq2(mn, md, "newProxyInstance", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const ifaces_id = switch (slots[method.params[1].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const handler_id = switch (slots[method.params[2].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const iarr = try arrayOf(f, ifaces_id);
            const names = f.loader.gpa.alloc([]const u8, iarr.data.len) catch return error.OutOfMemory;
            for (iarr.data, 0..) |cv, i| {
                const mref = switch (cv) {
                    .reference => |r| r orelse return error.NullPointer,
                    else => return error.TypeMismatch,
                };
                names[i] = (try mirrorClass(f, mref)).name;
            }
            const pc = try makeProxyClass(f, names);
            const id = try heap.allocInstance(pc);
            const hfi = pc.findField("h") orelse return error.LinkError;
            switch (heap.get(id).*) {
                .instance => |*inst| inst.fields[hfi] = .{ .reference = handler_id },
                else => return error.LinkError,
            }
            return f.push(.{ .reference = id });
        }
    }
    if (std.mem.eql(u8, on, "java/lang/Class")) {
        if (eq2(mn, md, "getPrimitiveClass", "(Ljava/lang/String;)Ljava/lang/Class;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const sref = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            var chars: std.ArrayList(i32) = .empty;
            defer chars.deinit(heap.gpa);
            try appendStringObj(&chars, heap, sref);
            var nm = heap.gpa.alloc(u8, chars.items.len) catch return error.OutOfMemory;
            defer heap.gpa.free(nm);
            for (chars.items, 0..) |c, i| nm[i] = @intCast(@as(u32, @bitCast(c)) & 0xFF);
            const pc = try primitiveClass(f, nm);
            return f.push(.{ .reference = try getMirror(f, pc) });
        }
        if (eq2(mn, md, "forName", "(Ljava/lang/String;)Ljava/lang/Class;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const sref = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            var chars: std.ArrayList(i32) = .empty;
            defer chars.deinit(heap.gpa);
            try appendStringObj(&chars, heap, sref);
            var name = heap.gpa.alloc(u8, chars.items.len) catch return error.OutOfMemory;
            defer heap.gpa.free(name);
            for (chars.items, 0..) |c, i| name[i] = if (c == '.') '/' else @intCast(@as(u32, @bitCast(c)) & 0xFF);
            const target = f.loader.find(name) orelse return pendingException(f, "java/lang/ClassNotFoundException");
            return f.push(.{ .reference = try getMirror(f, target) });
        }
        const recv = switch (slots[0]) {
            .reference => |r| r orelse return error.NullPointer,
            else => return error.TypeMismatch,
        };
        const rc = try mirrorClass(f, recv);
        if (eq2(mn, md, "getName", "()Ljava/lang/String;")) return pushDottedName(f, rc.name, false);
        if (eq2(mn, md, "getSimpleName", "()Ljava/lang/String;")) return pushDottedName(f, rc.name, true);
        if (eq2(mn, md, "isInterface", "()Z")) return f.pushInt(if (rc.is_interface) 1 else 0);
        if (eq2(mn, md, "isPrimitive", "()Z")) return f.pushInt(if (rc.is_primitive) 1 else 0);
        if (eq2(mn, md, "getEnumConstants", "()[Ljava/lang/Object;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const vdesc = std.fmt.allocPrint(f.loader.gpa, "()[L{s};", .{rc.name}) catch return error.OutOfMemory;
            defer f.loader.gpa.free(vdesc);
            const vr = rc.resolve("values", vdesc) orelse return f.push(.{ .reference = null });
            const cc = vr.method.code orelse return f.push(.{ .reference = null });
            try f.loader.ensureInit(vr.owner, heap, f.budget);
            if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
            f.budget.depth += 1;
            defer f.budget.depth -= 1;
            const ret = try exec(f.loader.gpa, vr.owner, f.heap, f.loader, f.budget, cc.code, cc.max_stack, cc.max_locals, &.{}, cc.exception_table, f);
            return f.push(ret orelse Value{ .reference = null });
        }
        if (eq2(mn, md, "getSuperclass", "()Ljava/lang/Class;")) {
            if (rc.super) |sup| return f.push(.{ .reference = try getMirror(f, sup) });
            return f.push(.{ .reference = null });
        }
        if (eq2(mn, md, "getInterfaces", "()[Ljava/lang/Class;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            var mirrors: std.ArrayList(u32) = .empty;
            defer mirrors.deinit(heap.gpa);
            for (rc.interfaces) |ifn| {
                if (f.loader.find(ifn)) |ic| mirrors.append(heap.gpa, try getMirror(f, ic)) catch return error.OutOfMemory;
            }
            const aid = try heap.allocArray(.reference, mirrors.items.len);
            const arr = try arrayOf(f, aid);
            for (mirrors.items, 0..) |mid, i| arr.data[i] = .{ .reference = mid };
            return f.push(.{ .reference = aid });
        }
        if (eq2(mn, md, "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const sref = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            var chars: std.ArrayList(i32) = .empty;
            defer chars.deinit(heap.gpa);
            try appendStringObj(&chars, heap, sref);
            const class_idx = f.loader.indexOf(rc) orelse return error.LinkError;
            for (rc.instance_fields, 0..) |fld, i| {
                if (fld.name.len == chars.items.len) {
                    var match = true;
                    for (fld.name, 0..) |ch, j| if (@as(i32, ch) != chars.items[j]) {
                        match = false;
                        break;
                    };
                    if (match) return f.push(.{ .reference = try memberMirror(f, "java/lang/reflect/Field", class_idx, i) });
                }
            }
            return pendingException(f, "java/lang/NoSuchFieldException");
        }
        if (eq2(mn, md, "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;")) {
            const heap = f.heap orelse return error.UnsupportedOpcode;
            const sref = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const ptypes = switch (slots[method.params[1].slot]) {
                .reference => |r| r,
                else => null,
            };
            const pcount: usize = if (ptypes) |pid| (arrayOf(f, pid) catch return error.LinkError).data.len else 0;
            var chars: std.ArrayList(i32) = .empty;
            defer chars.deinit(heap.gpa);
            try appendStringObj(&chars, heap, sref);
            const class_idx = f.loader.indexOf(rc) orelse return error.LinkError;
            for (rc.methods, 0..) |mm, i| {
                if (mm.params.len == pcount and mm.name.len == chars.items.len) {
                    var match = true;
                    for (mm.name, 0..) |ch, j| if (@as(i32, ch) != chars.items[j]) {
                        match = false;
                        break;
                    };
                    if (match) return f.push(.{ .reference = try memberMirror(f, "java/lang/reflect/Method", class_idx, i) });
                }
            }
            return pendingException(f, "java/lang/NoSuchMethodException");
        }
        if (eq2(mn, md, "getDeclaredMethods", "()[Ljava/lang/reflect/Method;")) return reflectGetMembers(f, recv, .methods);
        if (eq2(mn, md, "getDeclaredFields", "()[Ljava/lang/reflect/Field;")) return reflectGetMembers(f, recv, .fields);
        if (eq2(mn, md, "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;")) return reflectGetMembers(f, recv, .ctors);
        if (eq2(mn, md, "getClassLoader", "()Ljava/lang/ClassLoader;")) return f.push(.{ .reference = null });
        if (eq2(mn, md, "getAnnotation", "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;")) {
            const arg = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            return reflectGetAnnotation(f, recv, arg);
        }
        if (eq2(mn, md, "isAnnotationPresent", "(Ljava/lang/Class;)Z")) {
            const arg = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const anno_cls = try mirrorClass(f, arg);
            for (rc.annotations) |an| {
                if (std.mem.eql(u8, an.name, anno_cls.name)) return f.pushInt(1);
            }
            return f.pushInt(0);
        }
    }
    if (std.mem.eql(u8, on, "java/lang/reflect/Method")) {
        const self = switch (slots[0]) {
            .reference => |r| r orelse return error.NullPointer,
            else => return error.TypeMismatch,
        };
        if (eq2(mn, md, "getName", "()Ljava/lang/String;")) {
            const ref = try memberIndices(f, self);
            return f.push(.{ .reference = try stringFromBytes(f, f.loader.classes.items[ref.class_idx].methods[ref.member_idx].name) });
        }
        if (eq2(mn, md, "getParameterCount", "()I")) {
            const ref = try memberIndices(f, self);
            return f.pushInt(@intCast(f.loader.classes.items[ref.class_idx].methods[ref.member_idx].params.len));
        }
        if (eq2(mn, md, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) return reflectInvoke(f, self, slots);
        if (eq2(mn, md, "getAnnotation", "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;")) {
            const ref = try memberIndices(f, self);
            const arg = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            return buildAnnotationProxy(f, f.loader.classes.items[ref.class_idx].methods[ref.member_idx].annotations, arg);
        }
        if (eq2(mn, md, "isAnnotationPresent", "(Ljava/lang/Class;)Z")) {
            const ref = try memberIndices(f, self);
            const arg = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const anno_cls = try mirrorClass(f, arg);
            for (f.loader.classes.items[ref.class_idx].methods[ref.member_idx].annotations) |an| {
                if (std.mem.eql(u8, an.name, anno_cls.name)) return f.pushInt(1);
            }
            return f.pushInt(0);
        }
    }
    if (std.mem.eql(u8, on, "java/lang/reflect/Field")) {
        const self = switch (slots[0]) {
            .reference => |r| r orelse return error.NullPointer,
            else => return error.TypeMismatch,
        };
        if (eq2(mn, md, "getName", "()Ljava/lang/String;")) {
            const ref = try memberIndices(f, self);
            return f.push(.{ .reference = try stringFromBytes(f, f.loader.classes.items[ref.class_idx].instance_fields[ref.member_idx].name) });
        }
        if (eq2(mn, md, "get", "(Ljava/lang/Object;)Ljava/lang/Object;")) return reflectFieldGet(f, self, slots);
        if (eq2(mn, md, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V")) return reflectFieldSet(f, self, slots);
        if (eq2(mn, md, "getAnnotation", "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;")) {
            const ref = try memberIndices(f, self);
            const arg = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            return buildAnnotationProxy(f, f.loader.classes.items[ref.class_idx].instance_fields[ref.member_idx].annotations, arg);
        }
        if (eq2(mn, md, "isAnnotationPresent", "(Ljava/lang/Class;)Z")) {
            const ref = try memberIndices(f, self);
            const arg = switch (slots[method.params[0].slot]) {
                .reference => |r| r orelse return error.NullPointer,
                else => return error.TypeMismatch,
            };
            const anno_cls = try mirrorClass(f, arg);
            for (f.loader.classes.items[ref.class_idx].instance_fields[ref.member_idx].annotations) |an| {
                if (std.mem.eql(u8, an.name, anno_cls.name)) return f.pushInt(1);
            }
            return f.pushInt(0);
        }
    }
    if (std.mem.eql(u8, on, "java/lang/reflect/Constructor")) {
        const self = switch (slots[0]) {
            .reference => |r| r orelse return error.NullPointer,
            else => return error.TypeMismatch,
        };
        if (eq2(mn, md, "getParameterCount", "()I")) {
            const ref = try memberIndices(f, self);
            return f.pushInt(@intCast(f.loader.classes.items[ref.class_idx].methods[ref.member_idx].params.len));
        }
        if (eq2(mn, md, "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;")) return reflectNewInstance(f, self, slots);
    }
    if (std.mem.eql(u8, on, "java/lang/Double")) {
        const d = slots[method.params[0].slot];
        if (eq2(mn, md, "doubleToLongBits", "(D)J")) {
            if (std.math.isNan(d.double)) return f.pushLong(@bitCast(@as(u64, 0x7ff8000000000000)));
            return f.pushLong(@bitCast(d.double));
        }
        if (eq2(mn, md, "doubleToRawLongBits", "(D)J")) return f.pushLong(@bitCast(d.double));
        if (eq2(mn, md, "longBitsToDouble", "(J)D")) return f.pushDouble(@bitCast(d.long));
    }
    if (std.mem.eql(u8, on, "java/lang/Float")) {
        const x = slots[method.params[0].slot];
        if (eq2(mn, md, "floatToIntBits", "(F)I")) {
            if (std.math.isNan(x.float)) return f.pushInt(@bitCast(@as(u32, 0x7fc00000)));
            return f.pushInt(@bitCast(x.float));
        }
        if (eq2(mn, md, "floatToRawIntBits", "(F)I")) return f.pushInt(@bitCast(x.float));
        if (eq2(mn, md, "intBitsToFloat", "(I)F")) return f.pushFloat(@bitCast(x.int));
    }
    if (std.mem.eql(u8, on, "java/lang/String")) {
        if (eq2(mn, md, "valueOf", "(I)Ljava/lang/String;")) return floatStringInt(f, @as(i64, slots[method.params[0].slot].int));
        if (eq2(mn, md, "valueOf", "(J)Ljava/lang/String;")) return floatStringInt(f, slots[method.params[0].slot].long);
        if (eq2(mn, md, "valueOf", "(D)Ljava/lang/String;")) return floatString(f, f64, slots[method.params[0].slot].double);
        if (eq2(mn, md, "valueOf", "(F)Ljava/lang/String;")) return floatString(f, f32, slots[method.params[0].slot].float);
    }
    if (std.mem.eql(u8, on, "java/lang/Math")) {
        // Double math is native in the spec (StrictMath). Same computations as the
        // Zig Math intrinsic, so results match the differential oracle.
        if (eq2(mn, md, "sqrt", "(D)D")) return f.pushDouble(@sqrt(slots[method.params[0].slot].double));
        if (eq2(mn, md, "floor", "(D)D")) return f.pushDouble(@floor(slots[method.params[0].slot].double));
        if (eq2(mn, md, "ceil", "(D)D")) return f.pushDouble(@ceil(slots[method.params[0].slot].double));
        if (eq2(mn, md, "abs", "(D)D")) return f.pushDouble(@abs(slots[method.params[0].slot].double));
        if (eq2(mn, md, "cbrt", "(D)D")) return f.pushDouble(std.math.cbrt(slots[method.params[0].slot].double));
        if (eq2(mn, md, "pow", "(DD)D")) return f.pushDouble(std.math.pow(f64, slots[method.params[0].slot].double, slots[method.params[1].slot].double));
    }
    return error.UnsupportedOpcode;
}
fn invokeStatic(f: *Frame, cls: *const Class, code: []const u8, pending: *?PendingCall) RunError!void {
    const idx = try u16At(code, f.pc + 1);
    const mref = cls.cp.get(idx) catch return error.LinkError;
    const ref = switch (mref.*) {
        .methodref => |r| r,
        .interface_methodref => |r| r, // static interface methods (Java 8+)
        else => return error.LinkError,
    };
    const nat = switch ((cls.cp.get(ref.name_and_type_index) catch return error.LinkError).*) {
        .name_and_type => |x| x,
        else => return error.LinkError,
    };
    const mname = cls.cp.utf8(nat.name_index) catch return error.LinkError;
    const mdesc = cls.cp.utf8(nat.descriptor_index) catch return error.LinkError;
    const owner_name = try refClassName(cls, ref.class_index);
    // Migration switch: intrinsics only stand in for a stub (or not-yet-loaded) class.
    // When our own clean-room class is loaded (real bytecode), skip the intrinsic and
    // run/dispatch the real method — intrinsics are an optional acceleration, not the
    // source of truth.
    const owner_is_real = if (f.loader.find(owner_name)) |oc| !oc.is_stub else false;
    if (!owner_is_real) {
        if (std.mem.eql(u8, owner_name, "java/lang/Math")) return mathIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/lang/System")) return systemIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/lang/Integer")) return integerIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/lang/Long")) return longIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/util/Arrays")) return arraysIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/util/Objects")) return objectsIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/lang/String")) {
            if (eq2(mname, mdesc, "valueOf", "(D)Ljava/lang/String;")) return floatString(f, f64, try f.popDouble());
            if (eq2(mname, mdesc, "valueOf", "(F)Ljava/lang/String;")) return floatString(f, f32, try f.popFloat());
            if (eq2(mname, mdesc, "valueOf", "(I)Ljava/lang/String;")) return floatStringInt(f, @as(i64, try f.popInt()));
            if (eq2(mname, mdesc, "valueOf", "(J)Ljava/lang/String;")) return floatStringInt(f, try f.popLong());
            if (eq2(mname, mdesc, "valueOf", "(Z)Ljava/lang/String;")) {
                const b = try f.popInt();
                const heap = f.heap orelse return error.UnsupportedOpcode;
                var tmp: std.ArrayList(i32) = .empty;
                defer tmp.deinit(heap.gpa);
                try appendAscii(&tmp, heap.gpa, if (b != 0) "true" else "false");
                return f.push(.{ .reference = try newString(f, tmp.items) });
            }
        }
        if (std.mem.eql(u8, owner_name, "java/lang/Character")) return characterIntrinsic(f, mname, mdesc);
        if (std.mem.eql(u8, owner_name, "java/lang/Double") or std.mem.eql(u8, owner_name, "java/lang/Float") or
            std.mem.eql(u8, owner_name, "java/lang/Boolean") or
            std.mem.eql(u8, owner_name, "java/lang/Short") or std.mem.eql(u8, owner_name, "java/lang/Byte"))
            return boxStatic(f, owner_name, mname, mdesc);
    }
    const tclass = try resolveClass(f, cls, try refClassName(cls, ref.class_index));
    if (f.heap) |h| try f.loader.ensureInit(tclass, h, f.budget);
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

    if (target.is_native) return nativeInvoke(f, owner, target, slots);

    const c = target.code orelse return error.LinkError;
    pending.* = .{ .owner = owner, .code = c.code, .max_stack = c.max_stack, .max_locals = c.max_locals, .exception_table = c.exception_table, .slots = f.loader.gpa.dupe(Value, slots) catch return error.OutOfMemory };
}

fn loadConstant(f: *Frame, index: u16) RunError!void {
    const cls = f.class orelse return error.UnsupportedOpcode;
    switch ((cls.cp.get(index) catch return error.LinkError).*) {
        .integer => |v| try f.pushInt(v),
        .float => |v| try f.pushFloat(v),
        .string => |si| try createString(f, cls.cp.utf8(si) catch return error.LinkError),
        .class => {
            const name = cls.cp.classNameOf(index) catch return error.LinkError;
            const target = try resolveClass(f, cls, name);
            try f.push(.{ .reference = try getMirror(f, target) });
        },
        else => return error.UnsupportedOpcode,
    }
}
fn loadConstant2(f: *Frame, index: u16) RunError!void {
    const cls = f.class orelse return error.UnsupportedOpcode;
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

    var frame_storage = Frame{ .stack = stack, .locals = locals, .budget = budget, .heap = heap, .loader = loader, .parent = parent, .code = code, .class = class, .exceptions = exceptions };
    const f: *Frame = &frame_storage;

    sw: switch (try opAt(code, f.pc)) {
        .nop => {
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- constants ----
        .iconst_m1, .iconst_0, .iconst_1, .iconst_2, .iconst_3, .iconst_4, .iconst_5 => |o| {
            try f.pushInt(@as(i32, @intFromEnum(o)) - @intFromEnum(Op.iconst_0));
            f.pc += 1;
            continue :sw try step(f);
        },
        .lconst_0, .lconst_1 => |o| {
            try f.pushLong(@intFromEnum(o) - @intFromEnum(Op.lconst_0));
            f.pc += 1;
            continue :sw try step(f);
        },
        .fconst_0, .fconst_1, .fconst_2 => |o| {
            try f.pushFloat(@floatFromInt(@intFromEnum(o) - @intFromEnum(Op.fconst_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        .dconst_0, .dconst_1 => |o| {
            try f.pushDouble(@floatFromInt(@intFromEnum(o) - @intFromEnum(Op.dconst_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        .bipush => {
            try f.pushInt(try s8(code, f.pc + 1));
            f.pc += 2;
            continue :sw try step(f);
        },
        .sipush => {
            try f.pushInt(try s16(code, f.pc + 1));
            f.pc += 3;
            continue :sw try step(f);
        },
        .ldc => {
            try loadConstant(f, @intCast(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(f);
        },
        .ldc_w => {
            try loadConstant(f, try u16At(code, f.pc + 1));
            f.pc += 3;
            continue :sw try step(f);
        },
        .ldc2_w => {
            try loadConstant2(f, try u16At(code, f.pc + 1));
            f.pc += 3;
            continue :sw try step(f);
        },
        // ---- loads ----
        .iload => {
            try f.pushInt(try f.localInt(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(f);
        },
        .iload_0, .iload_1, .iload_2, .iload_3 => |o| {
            try f.pushInt(try f.localInt(@intFromEnum(o) - @intFromEnum(Op.iload_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        .lload => {
            try f.pushLong(try f.localLong(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(f);
        },
        .lload_0, .lload_1, .lload_2, .lload_3 => |o| {
            try f.pushLong(try f.localLong(@intFromEnum(o) - @intFromEnum(Op.lload_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        .fload => {
            try f.pushFloat(try f.localFloat(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(f);
        },
        .fload_0, .fload_1, .fload_2, .fload_3 => |o| {
            try f.pushFloat(try f.localFloat(@intFromEnum(o) - @intFromEnum(Op.fload_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        .dload => {
            try f.pushDouble(try f.localDouble(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(f);
        },
        .dload_0, .dload_1, .dload_2, .dload_3 => |o| {
            try f.pushDouble(try f.localDouble(@intFromEnum(o) - @intFromEnum(Op.dload_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- stores ----
        .istore => {
            try f.setLocal1(try u8At(code, f.pc + 1), .{ .int = try f.popInt() });
            f.pc += 2;
            continue :sw try step(f);
        },
        .istore_0, .istore_1, .istore_2, .istore_3 => |o| {
            try f.setLocal1(@intFromEnum(o) - @intFromEnum(Op.istore_0), .{ .int = try f.popInt() });
            f.pc += 1;
            continue :sw try step(f);
        },
        .lstore => {
            try f.setLocal2(try u8At(code, f.pc + 1), .{ .long = try f.popLong() });
            f.pc += 2;
            continue :sw try step(f);
        },
        .lstore_0, .lstore_1, .lstore_2, .lstore_3 => |o| {
            try f.setLocal2(@intFromEnum(o) - @intFromEnum(Op.lstore_0), .{ .long = try f.popLong() });
            f.pc += 1;
            continue :sw try step(f);
        },
        .fstore => {
            try f.setLocal1(try u8At(code, f.pc + 1), .{ .float = try f.popFloat() });
            f.pc += 2;
            continue :sw try step(f);
        },
        .fstore_0, .fstore_1, .fstore_2, .fstore_3 => |o| {
            try f.setLocal1(@intFromEnum(o) - @intFromEnum(Op.fstore_0), .{ .float = try f.popFloat() });
            f.pc += 1;
            continue :sw try step(f);
        },
        .dstore => {
            try f.setLocal2(try u8At(code, f.pc + 1), .{ .double = try f.popDouble() });
            f.pc += 2;
            continue :sw try step(f);
        },
        .dstore_0, .dstore_1, .dstore_2, .dstore_3 => |o| {
            try f.setLocal2(@intFromEnum(o) - @intFromEnum(Op.dstore_0), .{ .double = try f.popDouble() });
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- stack ops ----
        .pop => {
            _ = try f.pop();
            f.pc += 1;
            continue :sw try step(f);
        },
        .dup => {
            const v = try f.pop();
            try f.push(v);
            try f.push(v);
            f.pc += 1;
            continue :sw try step(f);
        },
        .swap => {
            const b = try f.pop();
            const a2 = try f.pop();
            try f.push(b);
            try f.push(a2);
            f.pc += 1;
            continue :sw try step(f);
        },
        .pop2 => {
            if (f.sp < 2) return error.StackUnderflow;
            f.sp -= 2;
            f.pc += 1;
            continue :sw try step(f);
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
            continue :sw try step(f);
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
            continue :sw try step(f);
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
            continue :sw try step(f);
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
            continue :sw try step(f);
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
            continue :sw try step(f);
        },
        // ---- int arithmetic ----
        .iadd, .isub, .imul, .idiv, .irem, .iand, .ior, .ixor, .ishl, .ishr, .iushr => |o| {
            const y = try f.popInt();
            const x = try f.popInt();
            const res = intBinary(o, x, y) catch |e| {
                if (e == error.ArithmeticException) {
                    try raise(f, f.class, f.exceptions, "java/lang/ArithmeticException", e);
                    continue :sw try step(f);
                }
                return e;
            };
            try f.pushInt(res);
            f.pc += 1;
            continue :sw try step(f);
        },
        .ineg => {
            try f.pushInt(0 -% try f.popInt());
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- long arithmetic ----
        .ladd, .lsub, .lmul, .ldiv, .lrem, .land, .lor, .lxor => |o| {
            const y = try f.popLong();
            const x = try f.popLong();
            const res = longBinary(o, x, y) catch |e| {
                if (e == error.ArithmeticException) {
                    try raise(f, f.class, f.exceptions, "java/lang/ArithmeticException", e);
                    continue :sw try step(f);
                }
                return e;
            };
            try f.pushLong(res);
            f.pc += 1;
            continue :sw try step(f);
        },
        .lshl, .lshr, .lushr => |o| {
            const s = try f.popInt();
            const x = try f.popLong();
            try f.pushLong(longShift(o, x, s));
            f.pc += 1;
            continue :sw try step(f);
        },
        .lneg => {
            try f.pushLong(0 -% try f.popLong());
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- float arithmetic ----
        .fadd, .fsub, .fmul, .fdiv, .frem => |o| {
            const y = try f.popFloat();
            const x = try f.popFloat();
            try f.pushFloat(floatBinary(o, x, y));
            f.pc += 1;
            continue :sw try step(f);
        },
        .fneg => {
            try f.pushFloat(-try f.popFloat());
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- double arithmetic ----
        .dadd, .dsub, .dmul, .ddiv, .drem => |o| {
            const y = try f.popDouble();
            const x = try f.popDouble();
            try f.pushDouble(doubleBinary(o, x, y));
            f.pc += 1;
            continue :sw try step(f);
        },
        .dneg => {
            try f.pushDouble(-try f.popDouble());
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- conversions ----
        .i2l => {
            try f.pushLong(try f.popInt());
            f.pc += 1;
            continue :sw try step(f);
        },
        .i2f => {
            try f.pushFloat(@floatFromInt(try f.popInt()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .i2d => {
            try f.pushDouble(@floatFromInt(try f.popInt()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .l2i => {
            try f.pushInt(@truncate(try f.popLong()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .l2f => {
            try f.pushFloat(@floatFromInt(try f.popLong()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .l2d => {
            try f.pushDouble(@floatFromInt(try f.popLong()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .f2i => {
            try f.pushInt(f2i(try f.popFloat()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .f2l => {
            try f.pushLong(f2l(try f.popFloat()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .f2d => {
            try f.pushDouble(try f.popFloat());
            f.pc += 1;
            continue :sw try step(f);
        },
        .d2i => {
            try f.pushInt(f2i(try f.popDouble()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .d2l => {
            try f.pushLong(f2l(try f.popDouble()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .d2f => {
            try f.pushFloat(@floatCast(try f.popDouble()));
            f.pc += 1;
            continue :sw try step(f);
        },
        .i2b => {
            try f.pushInt(@as(i8, @truncate(try f.popInt())));
            f.pc += 1;
            continue :sw try step(f);
        },
        .i2c => {
            try f.pushInt(@as(u16, @truncate(@as(u32, @bitCast(try f.popInt())))));
            f.pc += 1;
            continue :sw try step(f);
        },
        .i2s => {
            try f.pushInt(@as(i16, @truncate(try f.popInt())));
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- comparisons ----
        .lcmp => {
            const y = try f.popLong();
            const x = try f.popLong();
            try f.pushInt(if (x > y) @as(i32, 1) else if (x < y) @as(i32, -1) else 0);
            f.pc += 1;
            continue :sw try step(f);
        },
        .fcmpl, .fcmpg => |o| {
            const y = try f.popFloat();
            const x = try f.popFloat();
            try f.pushInt(fcmp(o == .fcmpg, x, y));
            f.pc += 1;
            continue :sw try step(f);
        },
        .dcmpl, .dcmpg => |o| {
            const y = try f.popDouble();
            const x = try f.popDouble();
            try f.pushInt(dcmp(o == .dcmpg, x, y));
            f.pc += 1;
            continue :sw try step(f);
        },
        // ---- iinc ----
        .iinc => {
            const idx = try u8At(code, f.pc + 1);
            const c = try s8(code, f.pc + 2);
            try f.setLocal1(idx, .{ .int = (try f.localInt(idx)) +% c });
            f.pc += 3;
            continue :sw try step(f);
        },
        // ---- branches ----
        .ifeq, .ifne, .iflt, .ifge, .ifgt, .ifle => |o| {
            const x = try f.popInt();
            if (compareZero(o, x)) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(f);
        },
        .if_icmpeq, .if_icmpne, .if_icmplt, .if_icmpge, .if_icmpgt, .if_icmple => |o| {
            const y = try f.popInt();
            const x = try f.popInt();
            if (compareInt(o, x, y)) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(f);
        },
        .goto => {
            f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            continue :sw try step(f);
        },
        .ifnull, .ifnonnull => |o| {
            const r = try f.popRef();
            const take = if (o == .ifnull) (r == null) else (r != null);
            if (take) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(f);
        },
        .if_acmpeq, .if_acmpne => |o| {
            const b = try f.popRef();
            const a2 = try f.popRef();
            const eq = (a2 == null and b == null) or (a2 != null and b != null and a2.? == b.?);
            const take = if (o == .if_acmpeq) eq else !eq;
            if (take) {
                f.pc = try branch(f.pc, try s16(code, f.pc + 1), code.len);
            } else f.pc += 3;
            continue :sw try step(f);
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
            continue :sw try step(f);
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
            continue :sw try step(f);
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
                    continue :sw try step(f);
                },
                else => return error.UnsupportedOpcode,
            }
            f.pc += 4;
            continue :sw try step(f);
        },
        // ---- calls / returns ----
        .aconst_null => {
            try f.push(.{ .reference = null });
            f.pc += 1;
            continue :sw try step(f);
        },
        .aload => {
            try f.push(try f.localRaw(try u8At(code, f.pc + 1)));
            f.pc += 2;
            continue :sw try step(f);
        },
        .aload_0, .aload_1, .aload_2, .aload_3 => |o| {
            try f.push(try f.localRaw(@intFromEnum(o) - @intFromEnum(Op.aload_0)));
            f.pc += 1;
            continue :sw try step(f);
        },
        .astore => {
            try f.setLocal1(try u8At(code, f.pc + 1), try f.pop());
            f.pc += 2;
            continue :sw try step(f);
        },
        .astore_0, .astore_1, .astore_2, .astore_3 => |o| {
            try f.setLocal1(@intFromEnum(o) - @intFromEnum(Op.astore_0), try f.pop());
            f.pc += 1;
            continue :sw try step(f);
        },
        .areturn => return try f.pop(),
        .newarray => {
            doNewArray(f, code) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            f.pc += 2;
            continue :sw try step(f);
        },
        .anewarray => {
            doANewArray(f, code) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            f.pc += 3;
            continue :sw try step(f);
        },
        .multianewarray => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            try doMultiANewArray(f, cls, code);
            f.pc += 4;
            continue :sw try step(f);
        },
        .arraylength => {
            doArrayLength(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            f.pc += 1;
            continue :sw try step(f);
        },
        .iaload, .baload, .caload, .saload => {
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            try f.pushInt(ai.arr.data[ai.i].int);
            f.pc += 1;
            continue :sw try step(f);
        },
        .laload => {
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            try f.pushLong(ai.arr.data[ai.i].long);
            f.pc += 1;
            continue :sw try step(f);
        },
        .faload => {
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            try f.pushFloat(ai.arr.data[ai.i].float);
            f.pc += 1;
            continue :sw try step(f);
        },
        .daload => {
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            try f.pushDouble(ai.arr.data[ai.i].double);
            f.pc += 1;
            continue :sw try step(f);
        },
        .aaload => {
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            try f.push(ai.arr.data[ai.i]);
            f.pc += 1;
            continue :sw try step(f);
        },
        .iastore => {
            const v = try f.popInt();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .int = v };
            f.pc += 1;
            continue :sw try step(f);
        },
        .bastore => {
            const v = try f.popInt();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .int = @as(i8, @truncate(v)) };
            f.pc += 1;
            continue :sw try step(f);
        },
        .castore => {
            const v = try f.popInt();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .int = @as(u16, @truncate(@as(u32, @bitCast(v)))) };
            f.pc += 1;
            continue :sw try step(f);
        },
        .sastore => {
            const v = try f.popInt();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .int = @as(i16, @truncate(v)) };
            f.pc += 1;
            continue :sw try step(f);
        },
        .lastore => {
            const v = try f.popLong();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .long = v };
            f.pc += 1;
            continue :sw try step(f);
        },
        .fastore => {
            const v = try f.popFloat();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .float = v };
            f.pc += 1;
            continue :sw try step(f);
        },
        .dastore => {
            const v = try f.popDouble();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = .{ .double = v };
            f.pc += 1;
            continue :sw try step(f);
        },
        .aastore => {
            const v = try f.pop();
            const ai = arrayIndex(f) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            ai.arr.data[ai.i] = v;
            if (f.heap) |hp| writeBarrier(hp, ai.oid, v);
            f.pc += 1;
            continue :sw try step(f);
        },
        .new => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            try doNew(f, cls, code);
            f.pc += 3;
            continue :sw try step(f);
        },
        .getfield => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            doGetField(f, cls, code) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            f.pc += 3;
            continue :sw try step(f);
        },
        .putfield => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            doPutField(f, cls, code) catch |e| {
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            f.pc += 3;
            continue :sw try step(f);
        },
        .invokespecial => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            var pending: ?PendingCall = null;
            invokeInstance(f, cls, code, true, &pending) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(f);
                    }
                    return e;
                }
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            if (pending) |p| {
                defer f.loader.gpa.free(p.slots);
                if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
                f.budget.depth += 1;
                defer f.budget.depth -= 1;
                const ret = exec(p.owner.gpa, p.owner, f.heap, f.loader, f.budget, p.code, p.max_stack, p.max_locals, p.slots, p.exception_table, f) catch |e| {
                    if (e == error.JavaException) {
                        if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                            f.budget.pending = null;
                            continue :sw try step(f);
                        }
                        return e;
                    }
                    try mapTrap(f, f.class, f.exceptions, e);
                    continue :sw try step(f);
                };
                if (ret) |rv| try f.pushKind(rv);
            }
            f.pc += 3;
            continue :sw try step(f);
        },
        .invokevirtual => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            var pending: ?PendingCall = null;
            invokeInstance(f, cls, code, false, &pending) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(f);
                    }
                    return e;
                }
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            if (pending) |p| {
                defer f.loader.gpa.free(p.slots);
                if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
                f.budget.depth += 1;
                defer f.budget.depth -= 1;
                const ret = exec(p.owner.gpa, p.owner, f.heap, f.loader, f.budget, p.code, p.max_stack, p.max_locals, p.slots, p.exception_table, f) catch |e| {
                    if (e == error.JavaException) {
                        if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                            f.budget.pending = null;
                            continue :sw try step(f);
                        }
                        return e;
                    }
                    try mapTrap(f, f.class, f.exceptions, e);
                    continue :sw try step(f);
                };
                if (ret) |rv| try f.pushKind(rv);
            }
            f.pc += 3;
            continue :sw try step(f);
        },
        .invokeinterface => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            var pending: ?PendingCall = null;
            invokeInstance(f, cls, code, false, &pending) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(f);
                    }
                    return e;
                }
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            if (pending) |p| {
                defer f.loader.gpa.free(p.slots);
                if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
                f.budget.depth += 1;
                defer f.budget.depth -= 1;
                const ret = exec(p.owner.gpa, p.owner, f.heap, f.loader, f.budget, p.code, p.max_stack, p.max_locals, p.slots, p.exception_table, f) catch |e| {
                    if (e == error.JavaException) {
                        if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                            f.budget.pending = null;
                            continue :sw try step(f);
                        }
                        return e;
                    }
                    try mapTrap(f, f.class, f.exceptions, e);
                    continue :sw try step(f);
                };
                if (ret) |rv| try f.pushKind(rv);
            }
            f.pc += 5;
            continue :sw try step(f);
        },
        .invokedynamic => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            doInvokeDynamic(f, cls, code) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(f);
                    }
                    return e;
                }
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            f.pc += 5;
            continue :sw try step(f);
        },
        .instanceof => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            const hp = f.heap orelse return error.UnsupportedOpcode;
            const target = try refClassName(cls, try u16At(code, f.pc + 1));
            const r = try f.popRef();
            var result: i32 = 0;
            if (r) |id| switch (hp.get(id).*) {
                .instance => |x| result = if (isInstanceOf(x.class, target)) 1 else 0,
                .string => |x| result = if (isInstanceOf(x.class, target)) 1 else 0,
                .lambda => |x| result = if (std.mem.eql(u8, x.iface, target)) 1 else 0,
                .builder => |x| result = if (isInstanceOf(x.class, target)) 1 else 0,
                .boxed => |x| result = if (isInstanceOf(x.class, target)) 1 else 0,
                .array => {}, // array instanceof: not modeled -> 0
            };
            try f.pushInt(result);
            f.pc += 3;
            continue :sw try step(f);
        },
        .checkcast => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            const hp = f.heap orelse return error.UnsupportedOpcode;
            const target = try refClassName(cls, try u16At(code, f.pc + 1));
            const r = try f.popRef();
            if (r) |id| switch (hp.get(id).*) {
                .instance => |x| if (!isInstanceOf(x.class, target)) return error.LinkError, // ClassCastException (no JDK class yet)
                .string => |x| if (!isInstanceOf(x.class, target)) return error.LinkError,
                .lambda => {},
                .builder => {},
                .boxed => {},
                .array => {},
            };
            try f.push(.{ .reference = r });
            f.pc += 3;
            continue :sw try step(f);
        },
        .monitorenter, .monitorexit => {
            _ = (try f.popRef()) orelse return error.NullPointer; // single-threaded: null-check only
            f.pc += 1;
            continue :sw try step(f);
        },
        .getstatic => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            const fr = try fieldRef(cls, try u16At(code, f.pc + 1));
            const dcls = try resolveClass(f, cls, fr.class_name);
            if (f.heap) |h| try f.loader.ensureInit(dcls, h, f.budget);
            const si = dcls.findStatic(fr.field_name) orelse return error.LinkError;
            try f.pushKind((try f.loader.staticsOf(dcls))[si]);
            f.pc += 3;
            continue :sw try step(f);
        },
        .putstatic => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            const fr = try fieldRef(cls, try u16At(code, f.pc + 1));
            const dcls = try resolveClass(f, cls, fr.class_name);
            if (f.heap) |h| try f.loader.ensureInit(dcls, h, f.budget);
            const si = dcls.findStatic(fr.field_name) orelse return error.LinkError;
            const kind = dcls.static_fields[si].kind;
            (try f.loader.staticsOf(dcls))[si] = try f.popKind(kind);
            f.pc += 3;
            continue :sw try step(f);
        },
        .invokestatic => {
            const cls = f.class orelse return error.UnsupportedOpcode;
            var pending: ?PendingCall = null;
            invokeStatic(f, cls, code, &pending) catch |e| {
                if (e == error.JavaException) {
                    if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                        f.budget.pending = null;
                        continue :sw try step(f);
                    }
                    return e;
                }
                try mapTrap(f, f.class, f.exceptions, e);
                continue :sw try step(f);
            };
            if (pending) |p| {
                defer f.loader.gpa.free(p.slots);
                if (f.budget.depth >= f.budget.max_depth) return error.CallDepthExceeded;
                f.budget.depth += 1;
                defer f.budget.depth -= 1;
                const ret = exec(p.owner.gpa, p.owner, f.heap, f.loader, f.budget, p.code, p.max_stack, p.max_locals, p.slots, p.exception_table, f) catch |e| {
                    if (e == error.JavaException) {
                        if (try handleException(f, f.class, f.exceptions, f.budget.pending.?)) {
                            f.budget.pending = null;
                            continue :sw try step(f);
                        }
                        return e;
                    }
                    try mapTrap(f, f.class, f.exceptions, e);
                    continue :sw try step(f);
                };
                if (ret) |rv| try f.pushKind(rv);
            }
            f.pc += 3;
            continue :sw try step(f);
        },
        .athrow => {
            const eid = (try f.popRef()) orelse return error.NullPointer;
            if (try handleException(f, f.class, f.exceptions, eid)) continue :sw try step(f);
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
        .is_native = false,
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
        .is_stub = true,
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
