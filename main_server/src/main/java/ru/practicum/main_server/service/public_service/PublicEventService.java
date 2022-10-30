package ru.practicum.main_server.service.public_service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main_server.client.StatisticClient;
import ru.practicum.main_server.exception.BadRequestException;
import ru.practicum.main_server.exception.InternalServerErrorException;
import ru.practicum.main_server.exception.NotFoundException;
import ru.practicum.main_server.mapper.EventMapper;
import ru.practicum.main_server.model.Event;
import ru.practicum.main_server.model.State;
import ru.practicum.main_server.model.Status;
import ru.practicum.main_server.model.dto.EndpointHitDto;
import ru.practicum.main_server.model.dto.EventFullDto;
import ru.practicum.main_server.model.dto.EventShortDto;
import ru.practicum.main_server.repository.EventRepository;
import ru.practicum.main_server.repository.ParticipationRequestRepository;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class PublicEventService {
    private final EventRepository eventRepository;
    private final ParticipationRequestRepository participationRepository;
    private final StatisticClient statClient;


    @Autowired
    public PublicEventService(EventRepository eventRepository, ParticipationRequestRepository participationRepository,
                              StatisticClient statClient) {
        this.eventRepository = eventRepository;
        this.participationRepository = participationRepository;
        this.statClient = statClient;
    }

    public List<EventShortDto> readEvents(String text, List<Long> categories, Boolean paid, String rangeStart,
                                          String rangeEnd, Boolean onlyAvailable, String sort, int from, int size) {
        LocalDateTime start = getStartTime(rangeStart);
        LocalDateTime end = getEndTime(rangeEnd);

        List<Event> events = eventRepository.searchEvents(text, categories, paid, start, end,
                        PageRequest.of(from / size, size))
                .stream()
                .collect(Collectors.toList());
        if (sort.equals("EVENT_DATE")) {
            events = events.stream()
                    .sorted(Comparator.comparing(Event::getEventDate))
                    .collect(Collectors.toList());
        }

        List<EventShortDto> listShortDto = events.stream()
                .filter(event -> event.getState().equals(State.PUBLISHED))
                .map(EventMapper::toEventShortDto)
                .map(this::setConfirmedRequestsAndViewsEventShortDto)
                .collect(Collectors.toList());
        if (sort.equals("VIEWS")) {
            listShortDto = listShortDto.stream()
                    .sorted(Comparator.comparing(EventShortDto::getViews))
                    .collect(Collectors.toList());
        }
        if (onlyAvailable) {
            listShortDto = listShortDto.stream()
                    .filter(eventShortDto -> eventShortDto.getConfirmedRequests()
                            <= eventRepository.getReferenceById(eventShortDto.getId()).getParticipantLimit())
                    .collect(Collectors.toList());
        }
        return listShortDto;
    }

    public EventFullDto readEvent(long id) {
        checkEventInDb(id);
        EventFullDto dto = EventMapper.toEventFullDto(eventRepository.getReferenceById(id));
        if (!(dto.getState().equals(State.PUBLISHED.toString()))) {
            throw new BadRequestException("можно посмотреть только опубликованные события");
        }
        return setConfirmedRequestsAndViewsEventFullDto(dto);
    }

    /**
     * Возвращает событие с полями просмотров и подтвержденных учатсников
     *
     * @param eventFullDto - полный DTO события
     * @return EventFullDto.class
     */
    public EventFullDto setConfirmedRequestsAndViewsEventFullDto(EventFullDto eventFullDto) {
        Long confirmedRequests = participationRepository
                .countByEventIdAndStatus(eventFullDto.getId(), Status.CONFIRMED);
        eventFullDto.setConfirmedRequests(confirmedRequests);
        eventFullDto.setViews(getViews(eventFullDto.getId()));
        return eventFullDto;
    }

    /**
     * Возвращает краткое представление события с полями просмотров и подтвержденных учатсников
     *
     * @param eventShortDto - краткое представление события
     * @return EventShortDto.class
     */
    public EventShortDto setConfirmedRequestsAndViewsEventShortDto(EventShortDto eventShortDto) {
        Long confirmedRequests = participationRepository
                .countByEventIdAndStatus(eventShortDto.getId(), Status.CONFIRMED);
        eventShortDto.setConfirmedRequests(confirmedRequests);
        eventShortDto.setViews(getViews(eventShortDto.getId()));
        return eventShortDto;
    }

    /**
     * Отправляет данные в сервис статистики
     *
     * @param request - запрос http
     */
    public void sentHitStat(HttpServletRequest request) {
        log.info("request URL {}", request.getRequestURI());
        EndpointHitDto endpointHit = EndpointHitDto.builder()
                .app("main_server")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
        statClient.createHit(endpointHit);
    }

    private LocalDateTime getEndTime(String rangeEnd) {
        LocalDateTime end;
        if (rangeEnd == null) {
            end = LocalDateTime.MAX;
        } else {
            end = LocalDateTime.parse(rangeEnd, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return end;
    }

    private LocalDateTime getStartTime(String rangeStart) {
        LocalDateTime start;
        if (rangeStart == null) {
            start = LocalDateTime.now();
        } else {
            start = LocalDateTime.parse(rangeStart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return start;
    }

    /**
     * Обращается к серверу статистики для получения кол-ва просмотров события
     *
     * @param eventId айди события
     * @return int - количество просмотров
     */
    private int getViews(long eventId) {
        ResponseEntity<Object> responseEntity;
        try {
            responseEntity = statClient.getStats(
                    eventRepository.getReferenceById(eventId).getCreatedOn()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    List.of("/events/" + eventId),
                    false);
        } catch (UnsupportedEncodingException e) {
            throw new InternalServerErrorException("неудачная кодировка");
        }
        if (Objects.requireNonNull(responseEntity.getBody()).equals("")) {
            return (Integer) ((LinkedHashMap<?, ?>) responseEntity.getBody()).get("hits");
        }

        return 0;
    }

    private void checkEventInDb(long id) {
        if (!eventRepository.existsById(id)) {
            throw new NotFoundException(String.format("по даному id=%d данных в базе нет", id));
        }
    }

}