package com.nemal.converter;

import com.nemal.enums.PipelineAuditActionType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PipelineAuditActionTypeConverter implements AttributeConverter<PipelineAuditActionType, String> {

    @Override
    public String convertToDatabaseColumn(PipelineAuditActionType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public PipelineAuditActionType convertToEntityAttribute(String dbData) {
        return PipelineAuditActionType.fromCode(dbData);
    }
}
