package com.glapp.data;

public class ConfigData implements Data {

    public enum Type {
        CFG_FRONT_LIGHT(0x01),
        CFG_REAR_LIGHT(0x02),
        CFG_MAX_THROTTLE_POWER(0x10),
        CFG_MAX_BRAKE_POWER(0x20);

        final int value;

        Type(int type) {
            this.value = type;
        }

        private static Packet.Command fromByte(byte b) {
            for (Packet.Command command : Packet.Command.values()) {
                if (command.value == b) {
                    return command;
                }
            }
            return null;
        }
    }

    byte[] payload = new byte[0];

    public void addData(Type type, byte[] data) {
        byte[] newPayload;
        if (data != null) {
            newPayload = new byte[payload.length + data.length + 1];
            System.arraycopy(payload, 0, newPayload, 0, payload.length);
            newPayload[payload.length] = (byte) type.value;
            System.arraycopy(data, 0, newPayload, payload.length + 1, data.length);
        } else {
            newPayload = new byte[payload.length + 1];
            System.arraycopy(payload, 0, newPayload, 0, payload.length);
            newPayload[payload.length] = (byte) type.value;
        }
        payload = newPayload;
    }

    @Override
    public Packet.Command getDataType() {
        return Packet.Command.CONFIG;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
