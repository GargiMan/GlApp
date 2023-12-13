package com.glapp.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;

public class MetricsData implements Data {
    float tempMosfet;
    float tempMotor;
    float avgMotorCurrent;
    float avgInputCurrent;
    float dutyCycleNow;
    int rpm;
    float inpVoltage;
    float ampHours;
    float ampHoursCharged;
    float wattHours;
    float wattHoursCharged;
    int tachometer;
    int tachometerAbs;

    public MetricsData() {
        this.tempMosfet = 0;
        this.tempMotor = 0;
        this.avgMotorCurrent = 0;
        this.avgInputCurrent = 0;
        this.dutyCycleNow = 0;
        this.rpm = 0;
        this.inpVoltage = 0;
        this.ampHours = 0;
        this.ampHoursCharged = 0;
        this.wattHours = 0;
        this.wattHoursCharged = 0;
        this.tachometer = 0;
        this.tachometerAbs = 0;
    }

    public MetricsData(byte[] payload) {
        setPayload(payload);
    }

    public String tempMosfetFormatted() {
        return String.format("%.1f °C", tempMosfet);
    }

    public String tempMotorFormatted() {
        return String.format("%.1f °C", tempMotor);
    }

    public String avgMotorCurrentFormatted() {
        return String.format("%.1f A", avgMotorCurrent);
    }

    public String avgInputCurrentFormatted() {
        return String.format("%.1f A", avgInputCurrent);
    }

    public String dutyCycleNowFormatted() {
        return String.format("%.1f %%", dutyCycleNow);
    }

    public String rpmFormatted() {
        return rpm + " rpm";
    }

    public String inpVoltageFormatted() {
        return String.format("%.1f V", inpVoltage);
    }

    public String ampHoursFormatted() {
        return String.format("%.1f Ah", ampHours);
    }

    public String ampHoursChargedFormatted() {
        return String.format("%.1f Ah", ampHoursCharged);
    }

    public String wattHoursFormatted() {
        return String.format("%.1f Wh", wattHours);
    }

    public String wattHoursChargedFormatted() {
        return String.format("%.1f Wh", wattHoursCharged);
    }

    public String tachometerFormatted() {
        return tachometer + " rot";
    }

    public String tachometerAbsFormatted() {
        return tachometerAbs + " rot";
    }

    public String getConcatenatedData(Set<String> dataToConcatenate) {
        String str = "";
        if (dataToConcatenate == null) return str;

        if (dataToConcatenate.contains("tempMosfet")) {
            str += tempMosfetFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("tempMotor")) {
            str += tempMotorFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("avgMotorCurrent")) {
            str += avgMotorCurrentFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("avgInputCurrent")) {
            str += avgInputCurrentFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("dutyCycleNow")) {
            str += dutyCycleNowFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("rpm")) {
            str += rpmFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("inpVoltage")) {
            str += inpVoltageFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("ampHours")) {
            str += ampHoursFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("ampHoursCharged")) {
            str += ampHoursChargedFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("wattHours")) {
            str += wattHoursFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("wattHoursCharged")) {
            str += wattHoursChargedFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("tachometer")) {
            str += tachometerFormatted().concat("\n");
        }
        if (dataToConcatenate.contains("tachometerAbs")) {
            str += tachometerAbsFormatted().concat("\n");
        }

        return str;
    }

    @Override
    public Packet.Command getDataType() {
        return Packet.Command.METRICS;
    }

    @Override
    public byte[] getPayload() {
        byte[] payload = new byte[52];
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(tempMosfet);
        buffer.putFloat(tempMotor);
        buffer.putFloat(avgMotorCurrent);
        buffer.putFloat(avgInputCurrent);
        buffer.putFloat(dutyCycleNow);
        buffer.putInt(rpm);
        buffer.putFloat(inpVoltage);
        buffer.putFloat(ampHours);
        buffer.putFloat(ampHoursCharged);
        buffer.putFloat(wattHours);
        buffer.putFloat(wattHoursCharged);
        buffer.putInt(tachometer);
        buffer.putInt(tachometerAbs);
        return payload;
    }

    @Override
    public void setPayload(byte[] payload) {
        int startIndex = 0, dataIndex = 0;
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        this.tempMosfet = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.tempMotor = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.avgMotorCurrent = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.avgInputCurrent = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.dutyCycleNow = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.rpm = buffer.getInt(startIndex + dataIndex++ * 4);
        this.inpVoltage = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.ampHours = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.ampHoursCharged = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.wattHours = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.wattHoursCharged = buffer.getFloat(startIndex + dataIndex++ * 4);
        this.tachometer = buffer.getInt(startIndex + dataIndex++ * 4);
        this.tachometerAbs = buffer.getInt(startIndex + dataIndex++ * 4);
    }
}
