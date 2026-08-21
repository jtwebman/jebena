//! Deterministic pseudo-fuzzing of the class file parser.
//!
//! Invariant under test: for ANY input bytes, ClassFile.parse must return
//! cleanly — either a valid ClassFile or an error — and never crash, over-read,
//! overflow, or leak. This is the "treat the parser like a network protocol
//! parser" requirement from docs/research/01-classfile-verifier.md section 3.
//!
//! We use a fixed PRNG seed so runs are reproducible (good for CI and for the
//! regression-ratchet principle in docs/research/TESTING.md). Under the
//! safety-checked test build, any undefined behavior aborts the test.

const std = @import("std");
const ClassFile = @import("class_file.zig").ClassFile;
const attribute_decode = @import("attribute_decode.zig");
const cpmod = @import("constant_pool.zig");
const verify = @import("verify.zig").verify;
const testing = std.testing;

const seed: []const u8 = @embedFile("testdata/Hello.class");

/// Parse and, if it succeeded, tear down. The point is that this never crashes.
fn tryParse(bytes: []const u8) void {
    var cf = ClassFile.parse(testing.allocator, bytes) catch return;
    defer cf.deinit();
    verify(&cf) catch {};
}

test "parser survives arbitrary random bytes" {
    var prng = std.Random.DefaultPrng.init(0xC0FFEE_BABE_1234);
    const rand = prng.random();
    var i: usize = 0;
    while (i < 5000) : (i += 1) {
        const len = rand.intRangeAtMost(usize, 0, 700);
        const buf = try testing.allocator.alloc(u8, len);
        defer testing.allocator.free(buf);
        rand.bytes(buf);
        tryParse(buf);
    }
}

test "parser survives random mutations of a valid class" {
    var prng = std.Random.DefaultPrng.init(0xDEADBEEF_0F0F);
    const rand = prng.random();
    var i: usize = 0;
    while (i < 5000) : (i += 1) {
        const buf = try testing.allocator.dupe(u8, seed);
        defer testing.allocator.free(buf);
        const mutations = rand.intRangeAtMost(usize, 1, 12);
        var m: usize = 0;
        while (m < mutations) : (m += 1) {
            const pos = rand.intRangeLessThan(usize, 0, buf.len);
            buf[pos] = rand.int(u8);
        }
        tryParse(buf);
    }
}

test "parser survives truncation of a valid class at every length" {
    var n: usize = 0;
    while (n <= seed.len) : (n += 1) {
        tryParse(seed[0..n]);
    }
}

test "parser survives every single-byte value at every position" {
    // Exhaustive single-byte corruption of the seed: pos x byte-value.
    // Catches any position where a specific byte triggers bad arithmetic.
    var pos: usize = 0;
    while (pos < seed.len) : (pos += 1) {
        var v: usize = 0;
        while (v < 256) : (v += 1) {
            const buf = try testing.allocator.dupe(u8, seed);
            defer testing.allocator.free(buf);
            buf[pos] = @intCast(v);
            tryParse(buf);
        }
    }
}

test "attribute decoder survives arbitrary bytes for every attribute name" {
    const names = [_][]const u8{
        "ConstantValue",   "Code",          "Exceptions",       "SourceFile",
        "LineNumberTable", "StackMapTable", "BootstrapMethods", "SomethingUnknown",
    };
    var entries: [names.len + 1]cpmod.Constant = undefined;
    entries[0] = .unusable;
    inline for (names, 0..) |nm, i| entries[i + 1] = .{ .utf8 = nm };
    const cp = cpmod.ConstantPool{ .entries = &entries };

    var prng = std.Random.DefaultPrng.init(0xA11CE_5EED);
    const rand = prng.random();
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();

    var i: usize = 0;
    while (i < 8000) : (i += 1) {
        const len = rand.intRangeAtMost(usize, 0, 80);
        const buf = try arena.allocator().alloc(u8, len);
        rand.bytes(buf);
        const name_index: u16 = @intCast(rand.intRangeAtMost(usize, 1, names.len));
        _ = attribute_decode.decode(arena.allocator(), cp, .{ .name_index = name_index, .info = buf }) catch {};
        if (i % 256 == 0) _ = arena.reset(.retain_capacity);
    }
}
