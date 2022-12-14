package ru.practicum.main_server.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventShortDto {
    private String annotation;
    private CategoryDto category;
    private Long confirmedRequests;
    private Long participationLimit;
    private String eventDate;
    private Long id;
    private UserShortDto initiator;
    private boolean paid;
    private String title;
    private Integer views;
}
