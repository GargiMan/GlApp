package com.glapp.data;

public interface Data {

    Packet.Command getDataType();

    byte[] getPayload();

    void setPayload(byte[] payload);
}
