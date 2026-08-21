//! jebena CLI.
//!
//! TODO(cli): `jebena parse <file.class>` once the Zig 0.16 std.Io file API is
//! wired up (filesystem reads are being routed through the colorless-IO model).
//! For now, `main` runs a self-demo over an embedded class, proving the parse
//! pipeline end-to-end in the built binary without needing file I/O.

const std = @import("std");
const jebena = @import("jebena");

const demo_class = @embedFile("testdata/Hello.class");

pub fn main() !void {
    var gpa_state: std.heap.DebugAllocator(.{}) = .init;
    defer _ = gpa_state.deinit();
    const gpa = gpa_state.allocator();

    var cf = try jebena.ClassFile.parse(gpa, demo_class);
    defer cf.deinit();

    const name = cf.constant_pool.classNameOf(cf.this_class) catch "<unknown>";
    std.debug.print("jebena 0.0.0 — parsed embedded {s}.class\n", .{name});
    std.debug.print("  version:    {d}.{d}\n", .{ cf.major_version, cf.minor_version });
    std.debug.print("  cp entries: {d}\n", .{cf.constant_pool.slotCount() - 1});
    std.debug.print("  interfaces: {d}\n", .{cf.interfaces.len});
    std.debug.print("  fields:     {d}\n", .{cf.fields.len});
    std.debug.print("  methods:    {d}\n", .{cf.methods.len});
}
