package ru.practicum.main_server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main_server.model.dto.ApiError;

import javax.persistence.EntityNotFoundException;
import javax.validation.ConstraintViolationException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Arrays;

@RestController
@ControllerAdvice
public class ErrorHandlingControllerAdvice {
    @ExceptionHandler({BadRequestException.class,
            UnsupportedEncodingException.class})
    public ResponseEntity<ApiError> handleException400(Exception e) {
        ApiError apiError = new ApiError();
        apiError.setMessage(e.getMessage());
        apiError.setReason("Запрос составлен с ошибкой");
        apiError.setStatus("BAD_REQUEST");
        Arrays.stream(e.getStackTrace())
                .map(StackTraceElement::toString)
                .forEach(apiError.getErrors()::add);
        apiError.setTimestamp(LocalDateTime.now());
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleException403(ForbiddenException e) {
        ApiError apiError = new ApiError();
        apiError.setMessage(e.getMessage());
        apiError.setReason("Не выполнены условия для совершения операции");
        apiError.setStatus("FORBIDDEN");
        Arrays.stream(e.getStackTrace())
                .map(StackTraceElement::toString)
                .forEach(apiError.getErrors()::add);
        apiError.setTimestamp(LocalDateTime.now());
        return new ResponseEntity<>(apiError, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({EntityNotFoundException.class, NotFoundException.class})
    public ResponseEntity<ApiError> handleException404(Exception e) {
        ApiError apiError = new ApiError();
        apiError.setMessage(e.getMessage());
        apiError.setReason("Объект не найден");
        apiError.setStatus("NOT_FOUND");
        Arrays.stream(e.getStackTrace())
                .map(StackTraceElement::toString)
                .forEach(apiError.getErrors()::add);
        apiError.setTimestamp(LocalDateTime.now());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({ConflictException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> handleException409(Exception e) {
        ApiError apiError = new ApiError();
        apiError.setMessage(e.getMessage());
        apiError.setReason("Нарушение целостности данных");
        apiError.setStatus("CONFLICT");
        Arrays.stream(e.getStackTrace())
                .map(StackTraceElement::toString)
                .forEach(apiError.getErrors()::add);
        apiError.setTimestamp(LocalDateTime.now());
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ApiError> handleException500(InternalServerErrorException e) {
        ApiError apiError = new ApiError();
        apiError.setMessage(e.getMessage());
        apiError.setReason("Внутренняя ошибка сервера");
        apiError.setStatus("INTERNAL_SERVER_ERROR");
        Arrays.stream(e.getStackTrace())
                .map(StackTraceElement::toString)
                .forEach(apiError.getErrors()::add);
        apiError.setTimestamp(LocalDateTime.now());
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
