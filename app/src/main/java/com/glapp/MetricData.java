package com.glapp;

public class MetricData {
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

    public MetricData() {
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
}
