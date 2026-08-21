//! jebena CLI. Self-demo over embedded classes (parse + execute), proving the
//! pipeline end-to-end in the built binary. Arbitrary-file parsing awaits the
//! Zig 0.16 std.Io file API (TODO).

const std = @import("std");
const jebena = @import("jebena");

const hello_class = @embedFile("testdata/Hello.class");
const compute_class = @embedFile("testdata/Compute.class");
const recur_class = @embedFile("testdata/Recur.class");

pub fn main() !void {
    var gpa_state: std.heap.DebugAllocator(.{}) = .init;
    defer _ = gpa_state.deinit();
    const gpa = gpa_state.allocator();

    // 1. Parse a real class.
    var cf = try jebena.ClassFile.parse(gpa, hello_class);
    defer cf.deinit();
    const name = cf.constant_pool.classNameOf(cf.this_class) catch "<unknown>";
    std.debug.print("jebena 0.0.0\n", .{});
    std.debug.print("parsed {s}.class (v{d}, {d} methods)\n", .{ name, cf.major_version, cf.methods.len });

    // 2. Execute real bytecode.
    const n: i32 = 100;
    const r = try runStaticInt(gpa, compute_class, "sumTo", "(I)I", n);
    std.debug.print("executed Compute.sumTo({d}) = {?d}\n", .{ n, r });

    // 3. Execute recursive bytecode (invokestatic).
    const fib = try runStaticInt(gpa, recur_class, "fib", "(I)I", 10);
    std.debug.print("executed Recur.fib(10) = {?d}  (recursion via invokestatic)\n", .{fib});
}

fn runStaticInt(gpa: std.mem.Allocator, class_bytes: []const u8, method: []const u8, desc: []const u8, arg: i32) !?i32 {
    var cf = try jebena.ClassFile.parse(gpa, class_bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    const cls = try jebena.interp.Class.init(gpa, arena.allocator(), &cf);
    return cls.callStatic(method, desc, &.{arg});
}
