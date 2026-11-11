package com.zuxy.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NotificationCompat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.InputStream;\nimport java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;

public class WhatsAppListener extends NotificationListenerService {
    private static final String CHANNEL = "zuxy_replies";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private String lastKey = "";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("en", "IN"));
        });
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!"com.whatsapp".equals(pkg)) return;

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;
        String sender = extras.getString(Notification.EXTRA_TITLE, "WhatsApp contact");
        CharSequence chars = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (chars == null) chars = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (chars == null) return;
        String message = chars.toString().trim();
        if (message.isEmpty() || message.equals(lastKey)) return;
        lastKey = message;

        if (isPaused() || isGameForeground()) return;

        speak("WhatsApp message from " + sender + ". " + message);
        RemoteInput[] inputs = null;
        PendingIntent replyIntent = null;
        Notification.Action[] actions = sbn.getNotification().actions;
        if (actions != null) {
            for (Notification.Action action : actions) {
                if (action != null && action.actionIntent != null && action.getRemoteInputs() != null
                        && action.getRemoteInputs().length > 0) {
                    inputs = action.getRemoteInputs();
                    replyIntent = action.actionIntent;
                    break;
                }
            }
        }
        final RemoteInput[] foundInputs = inputs;
        final PendingIntent foundIntent = replyIntent;
        worker.execute(() -> {
            String suggestion = generateReply(sender, message);
            if (suggestion == null || suggestion.isEmpty()) suggestion = "Thanks for your message. I will reply soon.";
            speak("ZUXY suggests: " + suggestion);
            if (foundInputs != null && foundIntent != null) {
                postApproval(sender, suggestion, foundIntent, foundInputs);
            } else {
                speak("WhatsApp quick reply is not available for this notification. Please reply manually.");
            }
        });
    }

    private boolean isPaused() {
        return getSharedPreferences("zuxy", MODE_PRIVATE).getBoolean("paused", false);
    }

    private boolean isGameForeground() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 15000, now);
            UsageEvents.Event event = new UsageEvents.Event();
            String foreground = "";
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= 29 && event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED)) {
                    foreground = event.getPackageName();
                }
            }
            String p = foreground.toLowerCase(Locale.ROOT);
            return p.contains("freefire") || p.contains("dts.freefiremax") ||
                   p.contains("pubg") || p.contains("bgmi") || p.contains("callofduty");
        } catch (Exception e) { return false; }
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "zuxy-" + System.currentTimeMillis());
    }

    private String generateReply(String sender, String message) {
        try {
            String key = getSharedPreferences("zuxy", MODE_PRIVATE).getString("gemini_key", "");
            if (key.isEmpty()) return "I received your message. I will reply soon.";
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("x-goog-api-key", key);
            JSONObject part = new JSONObject().put("text",
                "Write one short, natural Hinglish WhatsApp reply. Do not claim facts not in the message. " +
                "Sender: " + sender + "\nMessage: " + message);
            JSONObject body = new JSONObject().put("contents",
                new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(part))));
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            InputStream stream = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
            byte[] bytes = stream.readAllBytes();
            c.disconnect();
            JSONObject result = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            return result.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
        } catch (Exception e) { return "I saw your message. I will reply shortly."; }
    }

    private void postApproval(String sender, String reply, PendingIntent original, RemoteInput[] inputs) {
        String id = sender + System.currentTimeMillis();
        ReplyStore.put(id, original, inputs, reply);
        Intent yes = new Intent(this, ApprovalReceiver.class)
            .putExtra(ApprovalReceiver.EXTRA_ID, id).putExtra(ApprovalReceiver.EXTRA_APPROVE, true);
        Intent no = new Intent(this, ApprovalReceiver.class)
            .putExtra(ApprovalReceiver.EXTRA_ID, id).putExtra(ApprovalReceiver.EXTRA_APPROVE, false);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent yesPI = PendingIntent.getBroadcast(this, id.hashCode(), yes, flags);
        PendingIntent noPI = PendingIntent.getBroadcast(this, id.hashCode() + 1, no, flags);

        Notification n = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ZUXY AI reply for " + sender)
            .setContentText(reply)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(reply))
            .setAutoCancel(true)
            .addAction(0, "Approve & Send", yesPI)
            .addAction(0, "Reject", noPI)
            .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(id.hashCode(), n);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "ZUXY reply approvals", NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        worker.shutdown();
        super.onDestroy();
    }
}
