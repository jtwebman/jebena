//! jebena CLI. Self-demo over embedded classes (parse + execute), proving the
//! pipeline end-to-end in the built binary. Arbitrary-file parsing awaits the
//! Zig 0.16 std.Io file API (TODO).

const std = @import("std");
const jebena = @import("jebena");

const hello_class = @embedFile("testdata/Hello.class");
const compute_class = @embedFile("testdata/Compute.class");

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
    const r = try runStaticInt(gpa, compute_class, "sumTo", n);
    std.debug.print("executed Compute.sumTo({d}) = {?d}\n", .{ n, r });
}

fn runStaticInt(gpa: std.mem.Allocator, class_bytes: []const u8, method: []const u8, arg: i32) !?i32 {
    var cf = try jebena.ClassFile.parse(gpa, class_bytes);
    defer cf.deinit();
    var arena = std.heap.ArenaAllocator.init(gpa);
    defer arena.deinit();
    for (cf.methods) |m| {
        if (std.mem.eql(u8, try cf.constant_pool.utf8(m.name_index), method)) {
            for (m.attributes) |ai| {
                if (std.mem.eql(u8, try cf.constant_pool.utf8(ai.name_index), "Code")) {
                    const c = (try jebena.attribute_decode.decode(arena.allocator(), cf.constant_pool, ai)).code;
                    return jebena.interp.runInt(gpa, c.code, c.max_stack, c.max_locals, &.{arg});
                }
            }
        }
    }
    return error.MethodNotFound;
}
