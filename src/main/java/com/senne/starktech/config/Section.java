package com.senne.starktech.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Het "etiket" boven een hoofdstuk in {@link StarkConfig} (bv. Satellite): de uitleg die in
 * config/starktech.toml boven dat hoofdstuk komt, zoals {@code [satellite]}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Section {
    /** De uitleg in het configbestand (Engels). */
    String value();
}
