package com.poker.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private ResponseEntity<Map<String, String>> createErrorResponse(String message, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/json;charset=UTF-8"));

        Map<String, String> response = new HashMap<>();
        response.put("error", message);

        return new ResponseEntity<>(response, headers, status);
    }

    private String getLocalizedMessage(String errorCode, Object[] args, Locale locale) {
        try {
            return messageSource.getMessage(errorCode, args, locale);
        } catch (NoSuchMessageException e) {
            log.warn("Message key not found: {}", errorCode);
            return errorCode;
        }
    }

    @ExceptionHandler({
            IllegalRaiseException.class,
            IllegalCheckException.class,
            IllegalCallException.class,
            ChipAmountException.class,
            InvalidInputException.class
    })
    public ResponseEntity<Map<String, String>> handleBusinessLogicErrors(RuntimeException ex, Locale locale) {
        System.out.println("[DEBUG] GlobalExceptionHandler поймал бизнес-ошибку: " + ex.getClass().getSimpleName());
        System.out.println("[DEBUG] Сообщение ошибки: " + ex.getMessage());

        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());

        System.out.println("[DEBUG] Отправляем клиенту 400 Bad Request");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler({
            NotYourTurnException.class,
            IllegalTableStateException.class,
            PlayerAlreadyJoinedException.class,
            TableFullException.class,
            GameInProgressException.class,
            DuplicateResourceException.class
    })
    public ResponseEntity<Map<String, String>> handleConflictErrors(RuntimeException ex, Locale locale) {
        String message = getLocalizedMessage(ex.getMessage(), null, locale);
        return createErrorResponse(message, HttpStatus.CONFLICT); // 409
    }

    @ExceptionHandler({PlayerNotFoundException.class, AccountNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFoundErrors(RuntimeException ex, Locale locale) {
        String message = getLocalizedMessage(ex.getMessage(), null, locale);
        return createErrorResponse(message, HttpStatus.NOT_FOUND); // 404
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleAuthErrors(InvalidCredentialsException ex, Locale locale) {
        String message = getLocalizedMessage(ex.getMessage(), null, locale);
        return createErrorResponse(message, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler({EmptyDeckException.class})
    public ResponseEntity<Map<String, String>> handleForbiddenErrors(RuntimeException ex, Locale locale) {
        String message = getLocalizedMessage(ex.getMessage(), null, locale);
        return createErrorResponse(message, HttpStatus.FORBIDDEN); // 403
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String firstError = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return createErrorResponse(firstError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllOtherExceptions(Exception ex) {
        System.out.println("[DEBUG] !!! КРИТИЧЕСКАЯ НЕУЧТЕННАЯ ОШИБКА !!!");
        ex.printStackTrace();

        Map<String, String> response = new HashMap<>();
        response.put("error", "Внутренняя ошибка сервера: " + ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}