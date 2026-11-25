package com.example.telecom.controller;

/**
 * Upper bounds for request fields, so one request cannot store unbounded text.
 */
final class FieldLimits {
    static final int NAME = 100;
    static final int EMAIL = 254;
    static final int PHONE = 30;
    static final int DEVICE_IDENTIFIER = 32;
    static final int SUBJECT = 200;
    static final int DESCRIPTION = 5000;
    // NUMERIC(10,2) in the schema.
    static final int MONEY_INTEGER_DIGITS = 8;
    static final int MONEY_FRACTION_DIGITS = 2;

    static final String TOO_LONG = "must be at most {max} characters";
    static final String MONEY = "must have at most {integer} digits before and {fraction} after the decimal point";

    private FieldLimits() {
    }
}
