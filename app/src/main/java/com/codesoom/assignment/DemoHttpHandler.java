package com.codesoom.assignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpHandler;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.codesoom.assignment.HttpMethod.*;

public class DemoHttpHandler implements HttpHandler {
    private static final int HTTP_OK_CODE = 200;
    private static final int HTTP_CREATE_CODE = 201;
    private static final int HTTP_NO_CONTENT_CODE = 204;
    final Map<Long, Task> tasks;
    static private Long maxId = 1L;

    private Long generateTaskId() {
        Long generatedId = maxId;
        maxId++;
        return generatedId;
    }

    public DemoHttpHandler(Map<Long, Task> tasks) {
        this.tasks = tasks;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException, IllegalArgumentException {
        String content = "basic content";
        ObjectMapper mapper = new ObjectMapper();

        HttpMethod method = getHttpMethod(exchange);
        String path = getHttpRequestPath(exchange);
        String body = getHttpRequestBody(exchange);

        if (method == GET && path.equals("/tasks")) {
            content = tasksToJson(mapper);
            exchange.sendResponseHeaders(HTTP_OK_CODE, content.getBytes().length);
        }

        if (method == POST && path.equals("/tasks")) {
            try {
                Task newTask = toTask(mapper, body);
                Long newId = generateTaskId();
                newTask.setId(newId);
                tasks.put(newId, newTask);
                content = taskToJson(mapper, newTask);
                exchange.sendResponseHeaders(HTTP_CREATE_CODE, content.getBytes().length);
            } catch (JsonProcessingException e){
                e.printStackTrace();
            }
        }

        if (method == PATCH && path.startsWith("/tasks")) {
            long taskId = extractTaskIdFromPath(path);
            Task foundTask = tasks.get(taskId);
            Task newTask = toTask(mapper, body);
            newTask.setId(foundTask.getId());
            tasks.replace(taskId, foundTask, newTask);

            content = taskToJson(mapper, newTask);
            exchange.sendResponseHeaders(HTTP_OK_CODE, content.getBytes().length);
        }

        if (method == DELETE && path.startsWith("/tasks")) {
            long taskId = extractTaskIdFromPath(path);
            tasks.remove(taskId);
            exchange.sendResponseHeaders(HTTP_NO_CONTENT_CODE, 0);
        }

        sendResponse(exchange, content);
    }

    private Task toTask(ObjectMapper mapper, String content) throws JsonProcessingException {
        return mapper.readValue(content, Task.class);
    }

    private String tasksToJson(ObjectMapper mapper) throws IOException {
        OutputStream outputStream = new ByteArrayOutputStream();
        mapper.writeValue(outputStream, tasks.values());

        return outputStream.toString();
    }

    private String taskToJson(ObjectMapper mapper, Task task) throws IOException {
        OutputStream outputStream = new ByteArrayOutputStream();
        mapper.writeValue(outputStream, task);

        return outputStream.toString();
    }

    private void sendResponse(HttpExchange exchange, String content) throws IOException {
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(content.getBytes());
        outputStream.flush();
        outputStream.close();
    }

    private long extractTaskIdFromPath(String path) {
        String taskId = path.split("/")[2];
        return Long.parseLong(taskId);
    }

    private String getHttpRequestBody(HttpExchange exchange) {
        InputStream inputStream = exchange.getRequestBody();
        return new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.joining("/n"));
    }

    private String getHttpRequestPath(HttpExchange exchange) {
        URI uri = exchange.getRequestURI();
        if (uri == null) {
            throw new IllegalArgumentException("failed to return URI");
        }

        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("failed to return request path");
        }

        return path;
    }

    private HttpMethod getHttpMethod(HttpExchange exchange) {
        String methodInString = exchange.getRequestMethod();
        return Arrays.stream(HttpMethod.values())
                .filter((method) ->
                        method.toString().equalsIgnoreCase(methodInString)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "failed to return HttpMethod enum value"
                        )
                );
    }
}
