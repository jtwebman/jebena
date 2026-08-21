const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // The library module: the Jebena runtime, exposed for import as "jebena".
    const mod = b.addModule("jebena", .{
        .root_source_file = b.path("src/root.zig"),
        .target = target,
    });

    // The CLI executable (jebena parse <file.class>, etc.).
    const exe = b.addExecutable(.{
        .name = "jebena",
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/main.zig"),
            .target = target,
            .optimize = optimize,
            .imports = &.{
                .{ .name = "jebena", .module = mod },
            },
        }),
    });
    b.installArtifact(exe);

    const run_cmd = b.addRunArtifact(exe);
    run_cmd.step.dependOn(b.getInstallStep());
    if (b.args) |args| run_cmd.addArgs(args);
    const run_step = b.step("run", "Run the jebena CLI");
    run_step.dependOn(&run_cmd.step);

    // Tests: run every `test` block reachable from the library root module.
    const mod_tests = b.addTest(.{ .root_module = mod });
    const run_mod_tests = b.addRunArtifact(mod_tests);
    const test_step = b.step("test", "Run all tests");
    test_step.dependOn(&run_mod_tests.step);
}
