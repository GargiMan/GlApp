package com.glapp.data;

public class ControlData implements Data {
    private byte direction;
    private byte current;

    public ControlData(boolean direction, int current) {
        this.direction = (byte) (direction ? 0 : 255);
        this.current = (byte) current;
    }

    public ControlData(byte[] payload) {
        setPayload(payload);
    }

    @Override
    public Packet.Command getDataType() {
        return Packet.Command.CONTROL;
    }

    @Override
    public byte[] getPayload() {
        byte[] payload = new byte[2];
        payload[0] = direction;
        payload[1] = current;
        return payload;
    }

    @Override
    public void setPayload(byte[] payload) {
        this.direction = payload[0];
        this.current = payload[1];
    }
}
