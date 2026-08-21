//! Access & property flags for classes, fields, and methods (JVMS 4.1, 4.5, 4.6).
//! Same 16-bit mask; the meaning of some bits depends on context, so accessors
//! are named by their bit and the caller applies context.

const std = @import("std");

pub const AccessFlags = struct {
    bits: u16,

    pub fn init(bits: u16) AccessFlags {
        return .{ .bits = bits };
    }

    fn has(self: AccessFlags, mask: u16) bool {
        return (self.bits & mask) != 0;
    }

    pub fn isPublic(self: AccessFlags) bool {
        return self.has(0x0001);
    }
    pub fn isPrivate(self: AccessFlags) bool {
        return self.has(0x0002);
    }
    pub fn isProtected(self: AccessFlags) bool {
        return self.has(0x0004);
    }
    pub fn isStatic(self: AccessFlags) bool {
        return self.has(0x0008);
    }
    pub fn isFinal(self: AccessFlags) bool {
        return self.has(0x0010);
    }
    /// 0x0020: ACC_SUPER (class) / ACC_SYNCHRONIZED (method).
    pub fn isSuper(self: AccessFlags) bool {
        return self.has(0x0020);
    }
    pub fn isSynchronized(self: AccessFlags) bool {
        return self.has(0x0020);
    }
    /// 0x0040: ACC_VOLATILE (field) / ACC_BRIDGE (method).
    pub fn isVolatile(self: AccessFlags) bool {
        return self.has(0x0040);
    }
    pub fn isBridge(self: AccessFlags) bool {
        return self.has(0x0040);
    }
    /// 0x0080: ACC_TRANSIENT (field) / ACC_VARARGS (method).
    pub fn isTransient(self: AccessFlags) bool {
        return self.has(0x0080);
    }
    pub fn isVarargs(self: AccessFlags) bool {
        return self.has(0x0080);
    }
    pub fn isNative(self: AccessFlags) bool {
        return self.has(0x0100);
    }
    pub fn isInterface(self: AccessFlags) bool {
        return self.has(0x0200);
    }
    pub fn isAbstract(self: AccessFlags) bool {
        return self.has(0x0400);
    }
    pub fn isStrict(self: AccessFlags) bool {
        return self.has(0x0800);
    }
    pub fn isSynthetic(self: AccessFlags) bool {
        return self.has(0x1000);
    }
    pub fn isAnnotation(self: AccessFlags) bool {
        return self.has(0x2000);
    }
    pub fn isEnum(self: AccessFlags) bool {
        return self.has(0x4000);
    }
    /// 0x8000: ACC_MODULE (class) / ACC_MANDATED (param/module).
    pub fn isModule(self: AccessFlags) bool {
        return self.has(0x8000);
    }
};

const testing = std.testing;

test "class flags: public final" {
    const f = AccessFlags.init(0x0001 | 0x0010);
    try testing.expect(f.isPublic());
    try testing.expect(f.isFinal());
    try testing.expect(!f.isPrivate());
    try testing.expect(!f.isInterface());
}

test "interface + abstract" {
    const f = AccessFlags.init(0x0200 | 0x0400);
    try testing.expect(f.isInterface());
    try testing.expect(f.isAbstract());
    try testing.expect(!f.isFinal());
}

test "overlapping bit 0x0020 means super or synchronized" {
    const f = AccessFlags.init(0x0020);
    try testing.expect(f.isSuper());
    try testing.expect(f.isSynchronized());
}

test "all high flags" {
    const f = AccessFlags.init(0xFFFF);
    try testing.expect(f.isPublic() and f.isPrivate() and f.isProtected() and
        f.isStatic() and f.isFinal() and f.isSuper() and f.isVolatile() and
        f.isTransient() and f.isNative() and f.isInterface() and f.isAbstract() and
        f.isStrict() and f.isSynthetic() and f.isAnnotation() and f.isEnum() and
        f.isModule());
}

test "no flags" {
    const f = AccessFlags.init(0);
    try testing.expect(!f.isPublic() and !f.isStatic() and !f.isModule());
}
