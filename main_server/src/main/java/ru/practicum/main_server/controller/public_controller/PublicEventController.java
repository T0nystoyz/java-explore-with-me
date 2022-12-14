package ru.practicum.main_server.controller.public_controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main_server.model.dto.CommentDto;
import ru.practicum.main_server.model.dto.EventFullDto;
import ru.practicum.main_server.model.dto.EventShortDto;
import ru.practicum.main_server.service.public_service.PublicCommentService;
import ru.practicum.main_server.service.public_service.PublicEventService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping(path = "/events")
@Slf4j
public class PublicEventController {
    private final PublicEventService publicEventService;
    private final PublicCommentService commentService;

    public PublicEventController(PublicEventService publicEventService, PublicCommentService commentService) {
        this.publicEventService = publicEventService;
        this.commentService = commentService;
    }

    @GetMapping()
    public List<EventShortDto> readEvents(@RequestParam(required = false) String text,
                                          @RequestParam(required = false) List<Long> categories,
                                          @RequestParam(required = false) Boolean paid,
                                          @RequestParam(required = false) String rangeStart,
                                          @RequestParam(required = false) String rangeEnd,
                                          @RequestParam(required = false) Boolean onlyAvailable,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(defaultValue = "0") int from,
                                          @RequestParam(defaultValue = "10") int size,
                                          HttpServletRequest request) {
        log.info(":::GET /events получение списка событий по параметрам: text={}, categories={}, paid={}, " +
                        "rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);
        publicEventService.sentHitStat(request);
        return publicEventService
                .readEvents(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);
    }

    @GetMapping("/{id}")
    public EventFullDto readEvent(@PathVariable long id, HttpServletRequest request) {
        log.info(":::GET /events/{} чтение по id", id);
        publicEventService.sentHitStat(request);
        return publicEventService.readEvent(id);
    }

    @GetMapping("/{eventId}/comments")
    public List<CommentDto> readEventComments(@PathVariable Long eventId) {
        log.info(":::GET /events/{}/comments чтение комментариев по id события", eventId);
        return commentService.readEventComments(eventId);
    }
}
