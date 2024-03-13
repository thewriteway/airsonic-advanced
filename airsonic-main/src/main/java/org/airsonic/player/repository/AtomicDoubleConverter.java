package org.airsonic.player.repository;

import com.google.common.util.concurrent.AtomicDouble;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class AtomicDoubleConverter implements AttributeConverter<AtomicDouble, Double> {

    @Override
    public Double convertToDatabaseColumn(AtomicDouble attribute) {
        return attribute.get();
    }

    @Override
    public AtomicDouble convertToEntityAttribute(Double dbData) {
        return new AtomicDouble(dbData);
    }

}
