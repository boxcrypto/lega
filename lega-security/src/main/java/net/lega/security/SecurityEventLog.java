package net.lega.security;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


final class SecurityEventLog {

    private final int maxSize;
    private final Deque<SecurityEvent> buffer;

    SecurityEventLog(int maxSize) {
        this.maxSize = maxSize;
        this.buffer = new ArrayDeque<>(maxSize);
    }

    synchronized void add(SecurityEvent event) {
        if (buffer.size() >= maxSize) {
            buffer.pollFirst();
        }
        buffer.addLast(event);
    }

    synchronized List<SecurityEvent> getLast(int count) {
        List<SecurityEvent> result = new ArrayList<>(Math.min(count, buffer.size()));
        SecurityEvent[] arr = buffer.toArray(new SecurityEvent[0]);
        int start = Math.max(0, arr.length - count);
        for (int i = start; i < arr.length; i++) {
            result.add(arr[i]);
        }
        return result;
    }

    synchronized int size() {
        return buffer.size();
    }
}
