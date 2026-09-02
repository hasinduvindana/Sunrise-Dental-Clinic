package service.notify;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps an in-memory snapshot of the number now being served in each session.
 *
 * The waiting-room screen and the patient portal poll GET /api/queue/{sessionId}
 * which reads this cache, so the "which number is going on" question is
 * answered without hitting the database on every poll.
 */
public class QueueDisplayListener implements ClinicEventListener {

    private static final QueueDisplayListener INSTANCE = new QueueDisplayListener();

    private final Map<Integer, Integer> nowServing = new ConcurrentHashMap<>();
    private final Map<Integer, String> lastAnnouncement = new ConcurrentHashMap<>();

    private QueueDisplayListener() { }

    public static QueueDisplayListener get() {
        return INSTANCE;
    }

    @Override
    public void onEvent(ClinicEvent event) {
        if (event.getType() != ClinicEvent.Type.QUEUE_ADVANCED) {
            return;
        }
        Object sessionId = event.get("sessionId");
        Object queueNo = event.get("queueNo");
        if (sessionId instanceof Integer && queueNo instanceof Integer) {
            nowServing.put((Integer) sessionId, (Integer) queueNo);
            lastAnnouncement.put((Integer) sessionId, event.getMessage());
        }
    }

    public Integer nowServing(int sessionId) {
        return nowServing.get(sessionId);
    }

    public String announcement(int sessionId) {
        return lastAnnouncement.get(sessionId);
    }

    public void clear(int sessionId) {
        nowServing.remove(sessionId);
        lastAnnouncement.remove(sessionId);
    }
}
