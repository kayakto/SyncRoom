package ru.syncroom.study.dto;

import lombok.Data;

@Data
public class ActivateMotivationalBotRequest {
    private Integer goalCount;
    private Boolean autoSuggest;
    private Boolean suggestOnBreak;
}
