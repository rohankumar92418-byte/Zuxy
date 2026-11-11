package com.zuxy.ai;

import android.app.PendingIntent;
import android.app.RemoteInput;
import java.util.concurrent.ConcurrentHashMap;

public class ReplyStore {
    public static class Target {
        public final PendingIntent pendingIntent;
        public final RemoteInput[] remoteInputs;
        public final String reply;
        Target(PendingIntent p, RemoteInput[] r, String text) {
            pendingIntent = p; remoteInputs = r; reply = text;
        }
    }
    public static final ConcurrentHashMap<String, Target> TARGETS = new ConcurrentHashMap<>();
    public static void put(String id, PendingIntent p, RemoteInput[] r, String reply) {
        TARGETS.put(id, new Target(p, r, reply));
    }
}
