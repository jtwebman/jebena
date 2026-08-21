//! jebena CLI.
//!   jebena                 self-demo over embedded classes
//!   jebena parse <file>    structural summary of a .class file
//!   jebena disasm <file>   disassemble each method's bytecode

const std = @import("std");
const jebena = @import("jebena");

const hello_class = @embedFile("testdata/Hello.class");
const compute_class = @embedFile("testdata/Compute.class");
const recur_class = @embedFile("testdata/Recur.class");

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
    } else {
        return usage();
    }
}

fn usage() void {
    std.debug.print("usage: jebena [parse|disasm] <file.class>   (no args: self-demo)\n", .{});
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
}

fn runStaticInt(gpa: std.mem.Allocator, class_bytes: []const u8, method: []const u8, desc: []const u8, arg: i32) !?i32 {
    var cf = try jebena.ClassFile.parse(gpa, class_bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    const cls = try jebena.interp.Class.init(gpa, arena.allocator(), &cf);
    const r = try cls.callStatic(method, desc, &.{arg});
    return if (r) |v| v.int else null;
}
