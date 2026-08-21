//! Jebena: a clean-room, spec-driven Java runtime. Library root.

const std = @import("std");

pub const Reader = @import("reader.zig").Reader;
pub const AccessFlags = @import("access_flags.zig").AccessFlags;
pub const constant_pool = @import("constant_pool.zig");
pub const ConstantPool = constant_pool.ConstantPool;
pub const Constant = constant_pool.Constant;
pub const attribute = @import("attribute.zig");
pub const AttributeInfo = attribute.AttributeInfo;
pub const class_file = @import("class_file.zig");
pub const ClassFile = class_file.ClassFile;
pub const MemberInfo = class_file.MemberInfo;

test {
    _ = @import("reader.zig");
    _ = @import("access_flags.zig");
    _ = @import("constant_pool.zig");
    _ = @import("attribute.zig");
    _ = @import("class_file.zig");
    _ = @import("fuzz.zig");
}
