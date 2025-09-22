package com.example;

import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.annotations.Trace;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private static final TraceRootLogger logger = TraceRootLogger.getLogger(TaskController.class);

  // In-memory store (thread-safe enough for a demo)
  private final List<Task> tasks = new CopyOnWriteArrayList<>();
  private final AtomicLong ids = new AtomicLong(0);

  // Simple DTOs
  public record Task(Long id, String title, boolean done) {}

  public record NewTask(@NotBlank String title) {}

  @GetMapping
  @Trace(spanName = "list-tasks")
  public List<Task> list() {
    logger.info("Listing all tasks, current count: {}", tasks.size());
    return tasks;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Trace(spanName = "create-task", traceParams = true)
  public Task create(@RequestBody NewTask req) {
    logger.info("Creating new task with title: {}", req.title());
    Task t = new Task(ids.incrementAndGet(), req.title(), false);
    tasks.add(t);
    logger.info("Task created successfully with id: {}", t.id());
    return t;
  }

  @PutMapping("/{id}/toggle")
  @Trace(spanName = "toggle-task", traceParams = true)
  public Task toggle(@PathVariable("id") Long id) {
    logger.info("Toggling task with id: {}", id);
    for (int i = 0; i < tasks.size(); i++) {
      Task t = tasks.get(i);
      if (t.id().equals(id)) {
        Task updated = new Task(t.id(), t.title(), !t.done());
        tasks.set(i, updated);
        logger.info("Task {} toggled to done: {}", id, updated.done());
        return updated;
      }
    }
    logger.warn("Task not found for toggle operation: {}", id);
    throw new TaskNotFound(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Trace(spanName = "delete-task", traceParams = true)
  public void delete(@PathVariable("id") Long id) {
    logger.info("Deleting task with id: {}", id);
    boolean removed = tasks.removeIf(t -> t.id().equals(id));
    if (!removed) {
      logger.warn("Task not found for deletion: {}", id);
      throw new TaskNotFound(id);
    }
    logger.info("Task {} deleted successfully", id);
  }

  // Minimal error handling
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(TaskNotFound.class)
  public String notFound(TaskNotFound ex) {
    logger.error("Task not found error: {}", ex.getMessage());
    return ex.getMessage();
  }

  static class TaskNotFound extends RuntimeException {
    TaskNotFound(Long id) {
      super("Task " + id + " not found");
    }
  }
}
