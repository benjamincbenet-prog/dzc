package com.tonya.dzcscale.ble;

/**
 * Parser for the 11-byte DZC-D18E3 FFF4 notification frame.
 *
 * CF [aux lo] [aux hi] [weight lo] [weight hi] [Z lo] [Z hi]
 *    [profile] [status] [mode] [xor]
 *
 * The captured DZC traffic uses byte 9 as the packet class/mode. In
 * particular, 0x01 is the live-weight stream and 0xA0 is the body-impedance
 * result stream. Byte 8 is retained as a device status field and is not used
 * by the collector to decide which payload interpretation to trust.
 */
public final class DzcPacketParser {
    // Stateless decoder: invalid frames return null and never enter the sample buffers.
    public record Packet(
            double weightKg,
            double impedanceOhm,
            int auxiliaryRaw,
            int profileId,
            int status,
            int modeFlags
    ) {}

    public static Packet parse(byte[] data) {
        if (data == null || data.length != 11 || (data[0] & 0xFF) != 0xCF) return null;
        int checksum = 0;
        for (int i = 0; i < 10; i++) checksum ^= data[i] & 0xFF;
        if (checksum != (data[10] & 0xFF)) return null;

        int auxiliary = ((data[2] & 0xFF) << 8) | (data[1] & 0xFF);
        int rawWeight = ((data[4] & 0xFF) << 8) | (data[3] & 0xFF);
        int rawImpedance = ((data[6] & 0xFF) << 8) | (data[5] & 0xFF);
        return new Packet(
                rawWeight / 100.0,
                rawImpedance / 10.0,
                auxiliary,
                data[7] & 0xFF,
                data[8] & 0xFF,
                data[9] & 0xFF
        );
    }

    private DzcPacketParser() {}
}
