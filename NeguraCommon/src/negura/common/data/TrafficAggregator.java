package negura.common.data;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Paul Nechifor
 */
public class TrafficAggregator {
    public static class TypeConverter implements
            JsonSerializer<TrafficAggregator>,
            JsonDeserializer<TrafficAggregator> {

        @Override
        public JsonElement serialize(TrafficAggregator t, Type type,
                JsonSerializationContext jsc) {
            JsonObject ret = new JsonObject();

            ret.addProperty("up", t.previousUp + t.sessionUp.longValue());
            ret.addProperty("down", t.previousDown + t.sessionDown.longValue());
            ret.addProperty("time", t.previousTime +
                    (System.nanoTime() - t.sessionStart));

            return ret;
        }

        @Override
        public TrafficAggregator deserialize(JsonElement je, Type type,
                JsonDeserializationContext jdc) throws JsonParseException {
            JsonObject o = je.getAsJsonObject();
            
            long start = System.nanoTime();
            long up = o.get("up").getAsLong();
            long down = o.get("down").getAsLong();
            long time = o.get("time").getAsLong();

            return new TrafficAggregator(start, up, down, time);
        }

    }
    private AtomicLong sessionUp;
    private AtomicLong sessionDown;
    private final long sessionStart;

    private final long previousUp;
    private final long previousDown;
    private final long previousTime;

    private TrafficAggregator(long ss, long pu, long pd, long pt) {
        sessionUp = new AtomicLong(0);
        sessionDown = new AtomicLong(0);
        sessionStart = ss;
        previousUp = pu;
        previousDown = pd;
        previousTime = pt;
    }

    public TrafficAggregator() {
        this(System.nanoTime(), 0, 0, 0);
    }

    public void addSessionUp(long value) {
        sessionUp.addAndGet(value);
    }

    public void addSessionDown(long value) {
        sessionDown.addAndGet(value);
    }

    public long getSessionUp() {
        return sessionUp.longValue();
    }

    public long getSessionDown() {
        return sessionDown.longValue();
    }

    public long getSessionStart() {
        return sessionStart;
    }

    public long getPreviousUp() {
        return previousUp;
    }

    public long getPreviousDown() {
        return previousDown;
    }

    public long getPreviousTime() {
        return previousTime;
    }
}
