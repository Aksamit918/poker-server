package com.poker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TableFullException.class)
    public ResponseEntity<String> handleTableFull(TableFullException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PlayerAlreadyJoinedException.class)
    public ResponseEntity<String> handleAlreadyJoined(PlayerAlreadyJoinedException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
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
    public ResponseEntity<String> handleIllegalCheck(IllegalCheckException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalRaiseException.class)
    public ResponseEntity<String> handleIllegalRaise(IllegalRaiseException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalCallException.class)
    public ResponseEntity<String> handleIllegalCall(IllegalCallException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ChipAmountException.class)
    public ResponseEntity<String> handleInsufficientChips(ChipAmountException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NotYourTurnException.class)
    public ResponseEntity<String> handleNotYourTurn(NotYourTurnException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalTableStateException.class)
    public ResponseEntity<String> handleIllegalTableState(IllegalTableStateException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }

    // ResponseStatusException is used in generic error(404 Not Found)
}
