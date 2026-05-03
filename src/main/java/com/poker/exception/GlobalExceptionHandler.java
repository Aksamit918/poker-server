package com.poker.exception;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(TableFullException.class)
    public ResponseEntity<Map<String, String>> handleTableFull(TableFullException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("message", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlayerAlreadyJoinedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyJoined(PlayerAlreadyJoinedException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                ex.getArgs(),
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePlayerNotFound(PlayerNotFoundException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(GameInProgressException.class)
    public ResponseEntity<String> handleGameInProgress(GameInProgressException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(EmptyDeckException.class)
    public ResponseEntity<String> handleEmptyDeck(EmptyDeckException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalCheckException.class)
    public ResponseEntity<Map<String, String>> handleIllegalCheck(IllegalCheckException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                ex.getArgs(),
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalRaiseException.class)
    public ResponseEntity<Map<String, String>> handleIllegalRaise(IllegalRaiseException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                ex.getArgs(),
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalCallException.class)
    public ResponseEntity<Map<String, String>> handleIllegalCall(IllegalCallException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ChipAmountException.class)
    public ResponseEntity<String> handleInsufficientChips(ChipAmountException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ChipAmountException.class)
    public ResponseEntity<Map<String, String>> handleChipAmount(ChipAmountException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                ex.getArgs(),
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(NotYourTurnException.class)
    public ResponseEntity<Map<String, String>> handleNotYourTurn(NotYourTurnException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalTableStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalTableState(IllegalTableStateException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<String> handleAccountNotFound(AccountNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateResourceException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(InvalidInputException ex, Locale locale) {
        String errorMessage = messageSource.getMessage(
                ex.getMessage(),
                null,
                locale
        );

        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);

        String msg = messageSource.getMessage(ex.getMessage(), ex.getArgs(), locale);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }
}
