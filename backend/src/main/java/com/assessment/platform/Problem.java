package com.assessment.platform;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public class Problem extends RuntimeException {
  final int status;

  public Problem(int status, String message) {
    super(message);
    this.status = status;
  }

  @RestControllerAdvice
  public static class Handler {
    @ExceptionHandler(Problem.class)
    ResponseEntity<ProblemDetail> handle(Problem p) {
      return ResponseEntity.status(p.status)
          .body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(p.status), p.getMessage()));
    }

    @ExceptionHandler({
      IllegalArgumentException.class,
      org.springframework.web.bind.MethodArgumentNotValidException.class,
      org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    ResponseEntity<ProblemDetail> bad(Exception e) {
      return ResponseEntity.badRequest()
          .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict() {
      return ResponseEntity.status(409)
          .body(
              ProblemDetail.forStatusAndDetail(
                  HttpStatus.CONFLICT, "Conflicting resource or revision"));
    }
  }
}
