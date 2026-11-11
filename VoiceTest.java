package com.zuxy.ai;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class VoiceTest {
    private static TextToSpeech tts;
    public static void speak(Context context, String text) {
        if (tts == null) {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.getDefault());
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zuxy-test");
                }
            });
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zuxy-test");
        }
    }
}
