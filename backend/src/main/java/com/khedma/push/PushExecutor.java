package com.khedma.push;

import java.util.concurrent.Executor;

/**
 * Where push deliveries run, so a slow push service never holds up a request.
 * Its own type (not plain Executor) keeps it apart from Spring's other executors; tests swap in a direct one.
 */
public interface PushExecutor extends Executor {}
