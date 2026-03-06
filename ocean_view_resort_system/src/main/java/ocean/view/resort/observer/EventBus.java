package ocean.view.resort.observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private final Map<String, List<EventBusListener>> listeners = new java.util.concurrent.ConcurrentHashMap<>();

    public void on(String event, EventBusListener listener) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void off(String event, EventBusListener listener) {
        List<EventBusListener> list = listeners.get(event);
        if (list != null) list.remove(listener);
    }

    public void emit(String event, Object data) {
        List<EventBusListener> list = listeners.get(event);
        if (list != null) {
            for (EventBusListener l : new ArrayList<>(list)) {
                try {
                    l.onEvent(event, data);
                } catch (Exception ignored) { }
            }
        }
    }

    @FunctionalInterface
    public interface EventBusListener {
        void onEvent(String event, Object data);
    }
}

