package com.example;

import ai.traceroot.sdk.logger.TraceRootLogger;
import ai.traceroot.sdk.tracer.annotations.Trace;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.validation.constraints.NotBlank;
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
  public static class Task {
    private final Long id;
    private final String title;
    private final boolean done;

    public Task(Long id, String title, boolean done) {
      this.id = id;
      this.title = title;
      this.done = done;
    }

    public Long getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public boolean isDone() {
      return done;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Task task = (Task) o;
      return done == task.done && Objects.equals(id, task.id) && Objects.equals(title, task.title);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, title, done);
    }
  }

  public static class NewTask {
    @NotBlank private String title;

    public NewTask() {}

    public NewTask(String title) {
      this.title = title;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }
  }

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
    logger.info("Creating new task with title: {}", req.getTitle());
    Task t = new Task(ids.incrementAndGet(), req.getTitle(), false);
    tasks.add(t);
    logger.info("Task created successfully with id: {}", t.getId());
    return t;
  }

  @PutMapping("/{id}/toggle")
  @Trace(spanName = "toggle-task", traceParams = true)
  public Task toggle(@PathVariable("id") Long id) {
    logger.info("Toggling task with id: {}", id);
    for (int i = 0; i < tasks.size(); i++) {
      Task t = tasks.get(i);
      if (t.getId().equals(id)) {
        Task updated = new Task(t.getId(), t.getTitle(), !t.isDone());
        tasks.set(i, updated);
        logger.info("Task {} toggled to done: {}", id, updated.isDone());
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
    boolean removed = tasks.removeIf(t -> t.getId().equals(id));
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
