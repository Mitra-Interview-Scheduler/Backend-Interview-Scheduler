package com.nemal.converter;

import com.nemal.enums.PipelineStepStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PipelineStepStatusConverter implements AttributeConverter<PipelineStepStatus, String> {

    @Override
    public String convertToDatabaseColumn(PipelineStepStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public PipelineStepStatus convertToEntityAttribute(String dbData) {
        return PipelineStepStatus.fromCode(dbData);
    }
}
