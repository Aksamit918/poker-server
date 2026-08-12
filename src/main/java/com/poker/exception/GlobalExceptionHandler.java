package com.poker.exception;

import com.poker.dto.ErrorResponseDTO;
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

import java.util.Locale;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private ResponseEntity<ErrorResponseDTO> createErrorResponse(Exception ex, String messageKey, Object[] args, HttpStatus status, Locale locale) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/json;charset=UTF-8"));

        String localizedMessage = getLocalizedMessage(messageKey, args, locale);
        String errorType = ex.getClass().getSimpleName();

        ErrorResponseDTO response = new ErrorResponseDTO(errorType, localizedMessage);
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
            InvalidInputException.class,
            InsufficientFunds.class,
            EmoteNotFound.class,
            EmoteNotPurchasable.class
    })
    public ResponseEntity<ErrorResponseDTO> handleBusinessLogicErrors(PokerException ex, Locale locale) {
        log.debug("Business Error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        return createErrorResponse(ex, ex.getMessage(), ex.getArgs(), HttpStatus.BAD_REQUEST, locale);
    }

    @ExceptionHandler({
            NotYourTurnException.class,
            IllegalTableStateException.class,
            PlayerAlreadyJoinedException.class,
            TableFullException.class,
            AlreadyOwned.class
    })
    public ResponseEntity<ErrorResponseDTO> handleConflictErrors(PokerException ex, Locale locale) {
        return createErrorResponse(ex, ex.getMessage(), ex.getArgs(), HttpStatus.CONFLICT, locale);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthErrors(InvalidCredentialsException ex, Locale locale) {
        return createErrorResponse(ex, ex.getMessage(), ex.getArgs(), HttpStatus.UNAUTHORIZED, locale);
    }

    @ExceptionHandler(EmptyDeckException.class)
    public ResponseEntity<ErrorResponseDTO> handleSystemErrors(EmptyDeckException ex, Locale locale) {
        log.error("CRITICAL SYSTEM ERROR: ", ex);
        return createErrorResponse(ex, "error.internal.server.error", null, HttpStatus.INTERNAL_SERVER_ERROR, locale);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrors(MethodArgumentNotValidException ex) {
        String firstError = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        ErrorResponseDTO response = new ErrorResponseDTO("ValidationException", firstError);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleAllOtherExceptions(Exception ex) {
        log.error("!!! UNACCOUNTED-FOR ERROR !!!", ex);
        ErrorResponseDTO response = new ErrorResponseDTO("InternalServerError", "Внутренняя ошибка сервера");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}