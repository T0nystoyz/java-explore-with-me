package ru.practicum.main_server.service.private_service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main_server.client.StatisticClient;
import ru.practicum.main_server.exception.BadRequestException;
import ru.practicum.main_server.exception.ForbiddenException;
import ru.practicum.main_server.exception.NotFoundException;
import ru.practicum.main_server.mapper.EventMapper;
import ru.practicum.main_server.model.*;
import ru.practicum.main_server.model.dto.EventFullDto;
import ru.practicum.main_server.model.dto.EventShortDto;
import ru.practicum.main_server.model.dto.NewEventDto;
import ru.practicum.main_server.model.dto.UpdateEventRequest;
import ru.practicum.main_server.repository.CategoryRepository;
import ru.practicum.main_server.repository.EventRepository;
import ru.practicum.main_server.repository.ParticipationRequestRepository;
import ru.practicum.main_server.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.practicum.main_server.model.Status.CONFIRMED;

@Service
@Slf4j
public class PrivateEventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final StatisticClient statClient;
    private final CategoryRepository categoryRepository;
    private final PrivateLocationService locationService;
    private final ParticipationRequestRepository participationRequestRepository;

    @Autowired
    public PrivateEventService(EventRepository eventRepository,
                               StatisticClient statClient, UserRepository userRepository,
                               CategoryRepository categoryRepository, PrivateLocationService locationService,
                               ParticipationRequestRepository participationRequestRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.statClient = statClient;
        this.categoryRepository = categoryRepository;
        this.locationService = locationService;
        this.participationRequestRepository = participationRequestRepository;
    }

    public List<EventShortDto> readEvents(long userId, int from, int size) {
        log.info("PrivateEventService: ???????????? ?????????????? userId={}, from={}, size={}", userId, from, size);
        List<Event> e = statClient.getEventsWithViews(eventRepository.findAllByInitiatorId(userId,
                PageRequest.of(from / size, size)).toList());
        List<Event> eventsWithRequests = getEventsWithConfirmedRequests(e);
        return eventsWithRequests.stream().map(EventMapper::toEventShortDto).collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEvent(Long userId, UpdateEventRequest updateEventRequest) {
        Event event = getEventFromRequest(userId, updateEventRequest);
        event.setConfirmedRequests(participationRequestRepository
                .countByEventIdAndStatus(event.getId(), Status.CONFIRMED));
        event = eventRepository.save(event);
        EventFullDto eventFullDto = EventMapper.toEventFullDto(event);
        eventFullDto.setViews(statClient.getViewsSingleEvent(updateEventRequest.getEventId()));
        log.info("PrivateEventService: ?????????????? ?????????????????? userId={}, newEvent={}", userId, updateEventRequest);
        return eventFullDto;
    }

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        Location location = newEventDto.getLocation();
        location = locationService.save(location);
        Event event = EventMapper.toNewEvent(newEventDto);
        validateEventDate(event);
        event.setInitiator(getUserFromDbOrThrow(userId));
        Category category = getCategoryFromDbOrThrow(newEventDto.getCategory());
        event.setCategory(category);
        event.setLocation(location);
        event.setCreatedOn(LocalDateTime.now());
        event = eventRepository.save(event);
        EventFullDto eventFullDto = EventMapper.toEventFullDto(event);
        log.info("PrivateEventService: ?????????????? ?????????????? ?? ???????????? {}", newEventDto.getTitle());
        return eventFullDto;
    }

    public EventFullDto readEvent(Long userId, Long eventId) {
        checkEventInitiator(userId, eventId);
        log.info("PrivateEventService: ???????????? ?????????????????????????? ?? id={} ?????????????? ?? id={}", userId, eventId);
        Event event = eventRepository.getReferenceById(eventId);
        event.setViews(statClient.getViewsSingleEvent(eventId));
        event.setConfirmedRequests(participationRequestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED));
        return EventMapper.toEventFullDto(event);
    }

    @Transactional
    public EventFullDto cancelEvent(Long userId, Long eventId) {
        Event event = getEventFromDbOrThrow(eventId);
        checkEventInitiator(userId, eventId);
        event.setState(State.CANCELED);
        event = eventRepository.save(event);
        event.setViews(statClient.getViewsSingleEvent(eventId));
        log.info("PrivateEventService: ?????????????? id={} ???????????????? ?????????????????????????? ?? id={}", eventId, userId);
        return EventMapper.toEventFullDto(event);
    }

    /**
     * ???????????????????? ?????????????? ???? ??????????????
     *
     * @param userId             ???????? ????????????????????????
     * @param updateEventRequest ???????????? ????????????????????????
     * @return Event.class
     */
    private Event getEventFromRequest(Long userId, UpdateEventRequest updateEventRequest) {
        Event event = getEventFromDbOrThrow(updateEventRequest.getEventId());
        if (!event.getInitiator().getId().equals(userId)) {
            throw new BadRequestException("only creator can update event");
        }
        if (event.getState().equals(State.PUBLISHED)) {
            throw new BadRequestException("you can`t update published event");
        }
        if (updateEventRequest.getAnnotation() != null) {
            event.setAnnotation(updateEventRequest.getAnnotation());
        }
        if (updateEventRequest.getCategory() != null) {
            Category category = getCategoryFromDbOrThrow(updateEventRequest.getCategory());
            event.setCategory(category);
        }
        if (updateEventRequest.getEventDate() != null) {
            LocalDateTime date = LocalDateTime.parse(updateEventRequest.getEventDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (date.isBefore(LocalDateTime.now().plusHours(2))) {
                throw new BadRequestException("???????? ?????????????? ???????????? ?????? ?????????? ?????? ???????? ???? ????????????????/????????????????????");
            }
            event.setEventDate(date);
        }
        if (updateEventRequest.getDescription() != null) {
            event.setDescription(updateEventRequest.getDescription());
        }
        if (updateEventRequest.getPaid() != null) {
            event.setPaid(updateEventRequest.getPaid());
        }
        if (updateEventRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateEventRequest.getParticipantLimit());
        }
        if (updateEventRequest.getTitle() != null) {
            event.setTitle(updateEventRequest.getTitle());
        }
        return event;
    }

    /**
     * @param events ???????????? ??????????????
     * @return List ?????????????? ?? ???????????? confirmedRequests
     */
    private List<Event> getEventsWithConfirmedRequests(List<Event> events) {
        Map<Long, Event> eventsWithRequests = events.stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        Map<Event, Long> countedRequests = participationRequestRepository
                .findByStatusAndEvent(CONFIRMED, events).stream()
                .collect(Collectors.groupingBy(ParticipationRequest::getEvent, Collectors.counting()));
        if (countedRequests.isEmpty()) {
            log.info("////countedRequests ????????????");
            return events;
        }
        for (Map.Entry<Event, Long> entry : countedRequests.entrySet()) {
            Event e = entry.getKey();
            e.setConfirmedRequests(entry.getValue());
            eventsWithRequests.put(e.getId(), e);
        }
        log.info("////eventsWithRequests{}",eventsWithRequests);
        return new ArrayList<>(eventsWithRequests.values());
    }

    /**
     * ?????????????????? ???????????????? ???? ???????????????????????? ?????????????????????? ??????????????
     *
     * @param userId  ???????? ????????????????????????
     * @param eventId ???????? ??????????????
     */
    private void checkEventInitiator(Long userId, Long eventId) {
        Event event = getEventFromDbOrThrow(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ForbiddenException("???????????? ?????????????????? ?????????? ?????????????????????? ???????????? ???????????????????? ?? ??????????????");
        }
    }

    private Event getEventFromDbOrThrow(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new NotFoundException(
                String.format("PrivateEventService: ?????????????? ???? id=%d ?????? ?? ????????", id)));
    }

    private Category getCategoryFromDbOrThrow(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException(
                String.format("PrivateEventService: ?????????????????? ???? id=%d ?????? ?? ????????", id)));
    }

    private User getUserFromDbOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException(
                String.format("PrivateEventService: ???????????????????????? ???? id=%d ?????? ?? ????????", id)));
    }

    private void validateEventDate(Event event) {
        if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("???????? ?????????????? ???????????? ???????? ?????????? ?????? ?????????? ?????? ???????? ???? ????????????????/????????????????????");
        }
    }
}
