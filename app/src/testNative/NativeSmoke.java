package com.rnotlnglgq.netprobe.probe;

final class NativeIfEntry {
    final byte[] nameBytes, addressBytes, netmaskBytes;
    final long interfaceFlags, scopeId;
    final int family;
    NativeIfEntry(byte[] name, long flags, int family, byte[] address, byte[] mask, long scope) {
        this.nameBytes=name; this.interfaceFlags=flags; this.family=family;
        this.addressBytes=address; this.netmaskBytes=mask; this.scopeId=scope;
    }
}
final class NativeIfResult {
    final int errno;
    final NativeIfEntry[] entries;
    NativeIfResult(int error, NativeIfEntry[] entries) { this.errno=error; this.entries=entries; }
}
final class BionicBridge {
    private native NativeIfResult captureNative();
    static NativeIfResult readFixture() { return new BionicBridge().captureNative(); }
}
public final class NativeSmoke {
    public static void main(String[] args) {
        System.load(args[0]);
        NativeIfResult result=BionicBridge.readFixture();
        String mode=System.getenv("NETPROBE_NATIVE_FIXTURE");
        if ("denied".equals(mode) || "overflow".equals(mode)) {
            int expected="denied".equals(mode) ? 13 : 75;
            if (result.errno!=expected || result.entries!=null) throw new AssertionError("Error result mismatch");
        } else {
            if (result.errno!=0 || result.entries.length!=3) throw new AssertionError("Row count mismatch");
            NativeIfEntry v4=result.entries[0], v6=result.entries[1], ignored=result.entries[2];
            if (v4.family!=4 || v4.addressBytes.length!=4 || v4.netmaskBytes.length!=4) throw new AssertionError("IPv4 mismatch");
            if (v6.family!=6 || v6.scopeId!=7 || v6.addressBytes.length!=16 || v6.netmaskBytes.length!=16) throw new AssertionError("IPv6 mismatch");
            if (ignored.family!=0 || ignored.addressBytes.length!=0 || ignored.netmaskBytes!=null) throw new AssertionError("L2 data copied");
        }
        System.out.println("PASS: "+(mode==null ? "normal" : mode));
    }
}
