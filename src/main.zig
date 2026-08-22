//! jebena CLI.
//!   jebena                 self-demo over embedded classes
//!   jebena parse <file>    structural summary of a .class file
//!   jebena disasm <file>   disassemble each method's bytecode

const std = @import("std");
const jebena = @import("jebena");

const hello_class = @embedFile("testdata/Hello.class");
const compute_class = @embedFile("testdata/Compute.class");
const recur_class = @embedFile("testdata/Recur.class");
const point_class = @embedFile("testdata/Point.class");

pub fn main(init: std.process.Init) !void {
    const gpa = init.gpa;
    const io = init.io;
    var args = std.process.Args.iterate(init.minimal.args);
    _ = args.next(); // argv0

    const cmd = args.next() orelse return demo(gpa);
    if (std.mem.eql(u8, cmd, "parse")) {
        const path = args.next() orelse return usage();
        const bytes = try readFile(io, gpa, path);
        defer gpa.free(bytes);
        try parseAndPrint(gpa, path, bytes);
    } else if (std.mem.eql(u8, cmd, "disasm")) {
        const path = args.next() orelse return usage();
        const bytes = try readFile(io, gpa, path);
        defer gpa.free(bytes);
        try disasm(gpa, bytes);
    } else if (std.mem.eql(u8, cmd, "run")) {
        try cmdRun(gpa, io, &args);
    } else {
        return usage();
    }
}

fn usage() void {
    std.debug.print(
        \\usage:
        \\  jebena parse  <file.class>
        \\  jebena disasm <file.class>
        \\  jebena run    <MainClass> <method> <file.class>...
        \\  jebena                 (no args: self-demo)
        \\
    , .{});
}

const IC = jebena.interp;

const RunResult = struct { value: ?IC.Value = null, err: ?anyerror = null };
fn runEntry(loader: *IC.Loader, cls: *const IC.Class, method: []const u8, desc: []const u8, out: *RunResult) void {
    var b = IC.Budget{};
    out.value = IC.runInLoader(loader, cls, method, desc, &.{}, &b) catch |e| {
        out.err = e;
        return;
    };
}

/// Load a compiled multi-class program from disk and run a static no-arg method.
/// Provides a minimal java.lang stub chain so user classes can extend
/// Object/Throwable/Exception/RuntimeException.
fn cmdRun(gpa: std.mem.Allocator, io: std.Io, it: *std.process.Args.Iterator) !void {
    const main_class = it.next() orelse return usage();
    const method = it.next() orelse return usage();

    // Trailing args are either explicit .class files (parsed eagerly, as before)
    // or classpath directories (searched lazily to load classes by name on first
    // use). This keeps the file-list form working while unblocking large apps.
    var files: std.ArrayList([]const u8) = .empty;
    defer files.deinit(gpa);
    var cpdirs: std.ArrayList([]const u8) = .empty;
    defer cpdirs.deinit(gpa);
    while (it.next()) |pth| {
        if (std.mem.endsWith(u8, pth, ".class")) {
            try files.append(gpa, pth);
        } else {
            try cpdirs.append(gpa, pth); // directory classpath entry
        }
    }
    if (files.items.len == 0 and cpdirs.items.len == 0) return usage();

    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    const a = arena.allocator();

    var loader = IC.Loader.init(gpa);
    loader.io = io; // portable IO handle for System.out / clocks
    loader.class_arena = a; // arena for lazily-built Classes
    try loader.classpath.appendSlice(gpa, cpdirs.items);
    defer loader.deinit();

    // Parse the provided class files first. Any class supplied as real bytecode
    // (e.g. our own clean-room jbase/out/java/lang/Object.class) overrides the
    // built-in Zig stub of the same name — this is the stub -> real migration path.
    var cfs: std.ArrayList(*jebena.ClassFile) = .empty;
    defer {
        for (cfs.items) |cf| cf.deinit();
        cfs.deinit(gpa);
    }
    for (files.items) |path| {
        const bytes = try readFile(io, gpa, path);
        defer gpa.free(bytes);
        const cf = try a.create(jebena.ClassFile);
        cf.* = jebena.ClassFile.parse(gpa, bytes) catch |e| {
            std.debug.print("{s}: parse error: {s}\n", .{ path, @errorName(e) });
            return e;
        };
        try cfs.append(gpa, cf);
    }
    const provided = struct {
        fn has(list: []const *jebena.ClassFile, name: []const u8) bool {
            for (list) |cf| {
                const n = cf.constant_pool.classNameOf(cf.this_class) catch continue;
                if (std.mem.eql(u8, n, name)) return true;
            }
            return false;
        }
    }.has;

    // Core java.lang stub hierarchy (skipped for any class provided as real bytecode).
    inline for (.{
        .{ "java/lang/Object", @as(?[]const u8, null) },
        .{ "java/lang/String", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/Runnable", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/StringBuilder", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/StringBuffer", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/CharSequence", @as(?[]const u8, "java/lang/Object") },
        .{ "java/util/Comparator", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/Number", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/Integer", @as(?[]const u8, "java/lang/Number") },
        .{ "java/lang/Long", @as(?[]const u8, "java/lang/Number") },
        .{ "java/lang/Short", @as(?[]const u8, "java/lang/Number") },
        .{ "java/lang/Byte", @as(?[]const u8, "java/lang/Number") },
        .{ "java/lang/Double", @as(?[]const u8, "java/lang/Number") },
        .{ "java/lang/Float", @as(?[]const u8, "java/lang/Number") },
        .{ "java/lang/Boolean", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/Character", @as(?[]const u8, "java/lang/Object") },

        .{ "java/lang/Throwable", @as(?[]const u8, "java/lang/Object") },
        .{ "java/lang/Error", @as(?[]const u8, "java/lang/Throwable") },
        .{ "java/lang/Exception", @as(?[]const u8, "java/lang/Throwable") },
        .{ "java/lang/RuntimeException", @as(?[]const u8, "java/lang/Exception") },
        .{ "java/lang/ArithmeticException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/NullPointerException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/IllegalArgumentException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/IllegalStateException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/ClassCastException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/NegativeArraySizeException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/IndexOutOfBoundsException", @as(?[]const u8, "java/lang/RuntimeException") },
        .{ "java/lang/ArrayIndexOutOfBoundsException", @as(?[]const u8, "java/lang/IndexOutOfBoundsException") },
    }) |pair| {
        if (!provided(cfs.items, pair[0])) { // skip if real bytecode supplied for this class
            // Prefer the real clean-room class from the classpath over a stub when
            // one is available (e.g. jbase/out on the classpath) — the stub is only
            // a fallback for pure-app runs that don't ship java.base.
            const existing = loader.find(pair[0]);
            const real = if (existing == null) (try loader.loadFromClasspath(pair[0])) else existing;
            if (real == null) {
                const super = if (pair[1]) |sn| loader.find(sn) else null;
                const c = try a.create(IC.Class);
                c.* = try IC.makeStub(gpa, a, pair[0], pair[1], super);
                try loader.register(c);
            }
        }
    }

    // Build Class metadata, resolving supers before subclasses (iterate to a fixpoint).
    var pending: std.ArrayList(*jebena.ClassFile) = .empty;
    defer pending.deinit(gpa);
    try pending.appendSlice(gpa, cfs.items);
    while (pending.items.len > 0) {
        var progress = false;
        var i: usize = 0;
        while (i < pending.items.len) {
            const cf = pending.items[i];
            const super_name: ?[]const u8 = if (cf.super_class != 0) try cf.constant_pool.classNameOf(cf.super_class) else null;
            const super: ?*const IC.Class = if (super_name) |sn| loader.find(sn) else null;
            if (super_name != null and super == null) {
                i += 1; // super not built yet; try next round
                continue;
            }
            const c = try a.create(IC.Class);
            c.* = try IC.Class.init(gpa, a, cf, super);
            try loader.register(c);
            _ = pending.orderedRemove(i);
            progress = true;
        }
        if (!progress) {
            std.debug.print("error: unresolved class hierarchy (missing superclass or cycle)\n", .{});
            return error.UnresolvedHierarchy;
        }
    }

    // Resolve and run the entry method (lazy-loading the main class from the
    // classpath if it was not supplied as an explicit .class file).
    const cls = loader.find(main_class) orelse (try loader.loadFromClasspath(main_class)) orelse {
        std.debug.print("class not found: {s}\n", .{main_class});
        return error.ClassNotFound;
    };
    var desc: ?[]const u8 = null;
    for (cls.methods) |m| {
        if (std.mem.eql(u8, m.name, method) and m.params.len == 0 and m.is_static) {
            desc = m.descriptor;
            break;
        }
    }
    const d = desc orelse {
        std.debug.print("no static no-arg method '{s}' in {s}\n", .{ method, main_class });
        return error.MethodNotFound;
    };

    // Run on a thread with a large stack: the interpreter uses native recursion,
    // so deep Java recursion needs a big native stack (like a real JVM's -Xss).
    var result = RunResult{};
    const t = try std.Thread.spawn(.{ .stack_size = 512 * 1024 * 1024 }, runEntry, .{ &loader, cls, method, d, &result });
    t.join();
    if (result.err) |e| {
        std.debug.print("execution error: {s}\n", .{@errorName(e)});
        return e;
    }
    const r = result.value;
    if (r) |v| switch (v) {
        .int => |x| std.debug.print("{s}.{s}() = {d}\n", .{ main_class, method, x }),
        .long => |x| std.debug.print("{s}.{s}() = {d}L\n", .{ main_class, method, x }),
        .float => |x| std.debug.print("{s}.{s}() = {d}f\n", .{ main_class, method, x }),
        .double => |x| std.debug.print("{s}.{s}() = {d}d\n", .{ main_class, method, x }),
        else => std.debug.print("{s}.{s}() returned a reference\n", .{ main_class, method }),
    } else std.debug.print("{s}.{s}() returned void\n", .{ main_class, method });
}

fn readFile(io: std.Io, gpa: std.mem.Allocator, path: []const u8) ![]u8 {
    return std.Io.Dir.cwd().readFileAlloc(io, path, gpa, .unlimited) catch |e| {
        std.debug.print("cannot read {s}: {s}\n", .{ path, @errorName(e) });
        return e;
    };
}

fn parseAndPrint(gpa: std.mem.Allocator, path: []const u8, bytes: []const u8) !void {
    var cf = jebena.ClassFile.parse(gpa, bytes) catch |e| {
        std.debug.print("{s}: parse error: {s}\n", .{ path, @errorName(e) });
        return e;
    };
    defer cf.deinit();
    jebena.verify(&cf) catch |e| std.debug.print("(verify warning: {s})\n", .{@errorName(e)});

    const name = cf.constant_pool.classNameOf(cf.this_class) catch "<unknown>";
    std.debug.print("class {s}  (major {d})\n", .{ name, cf.major_version });
    std.debug.print("  fields: {d}, methods: {d}, interfaces: {d}\n", .{ cf.fields.len, cf.methods.len, cf.interfaces.len });
    for (cf.methods) |m| {
        const mn = cf.constant_pool.utf8(m.name_index) catch "?";
        const md = cf.constant_pool.utf8(m.descriptor_index) catch "?";
        std.debug.print("  method {s}{s}\n", .{ mn, md });
    }
}

fn disasm(gpa: std.mem.Allocator, bytes: []const u8) !void {
    var cf = try jebena.ClassFile.parse(gpa, bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    for (cf.methods) |m| {
        const mn = cf.constant_pool.utf8(m.name_index) catch "?";
        const md = cf.constant_pool.utf8(m.descriptor_index) catch "?";
        std.debug.print("{s}{s}:\n", .{ mn, md });
        for (m.attributes) |ai| {
            if (std.mem.eql(u8, cf.constant_pool.utf8(ai.name_index) catch "", "Code")) {
                const c = (try jebena.attribute_decode.decode(arena.allocator(), cf.constant_pool, ai)).code;
                var it = jebena.bytecode.iterate(c.code);
                while (it.next() catch null) |insn| {
                    std.debug.print("  {d:>4}: {s}\n", .{ insn.pc, @tagName(insn.op) });
                }
            }
        }
    }
}

fn demo(gpa: std.mem.Allocator) !void {
    var cf = try jebena.ClassFile.parse(gpa, hello_class);
    defer cf.deinit();
    const name = cf.constant_pool.classNameOf(cf.this_class) catch "<unknown>";
    std.debug.print("jebena 0.0.0\n", .{});
    std.debug.print("parsed {s}.class (v{d}, {d} methods)\n", .{ name, cf.major_version, cf.methods.len });
    std.debug.print("executed Compute.sumTo(100) = {?d}\n", .{try runStaticInt(gpa, compute_class, "sumTo", "(I)I", 100)});
    std.debug.print("executed Recur.fib(10) = {?d}  (recursion via invokestatic)\n", .{try runStaticInt(gpa, recur_class, "fib", "(I)I", 10)});
    std.debug.print("executed Point.make(3,4) = {?d}  (new + fields + invokevirtual)\n", .{try runStaticII(gpa, point_class, "make", 3, 4)});
}

fn runStaticII(gpa: std.mem.Allocator, class_bytes: []const u8, method: []const u8, a: i32, b: i32) !?i32 {
    var cf = try jebena.ClassFile.parse(gpa, class_bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    const cls = try jebena.interp.Class.init(gpa, arena.allocator(), &cf, null);
    const r = try cls.callStatic(method, "(II)I", &.{ a, b });
    return if (r) |v| v.int else null;
}

fn runStaticInt(gpa: std.mem.Allocator, class_bytes: []const u8, method: []const u8, desc: []const u8, arg: i32) !?i32 {
    var cf = try jebena.ClassFile.parse(gpa, class_bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    const cls = try jebena.interp.Class.init(gpa, arena.allocator(), &cf, null);
    const r = try cls.callStatic(method, desc, &.{arg});
    return if (r) |v| v.int else null;
}
