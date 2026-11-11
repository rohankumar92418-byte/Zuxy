package com.zuxy.ai;

import android.app.NotificationManager;
import android.app.RemoteInput;\nimport android.os.Build;\nimport android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ApprovalReceiver extends BroadcastReceiver {
    public static final String EXTRA_ID = "reply_id";
    public static final String EXTRA_APPROVE = "approve";

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_ID);
        boolean approve = intent.getBooleanExtra(EXTRA_APPROVE, false);
        ReplyStore.Target target = ReplyStore.TARGETS.remove(id);
        if (approve && target != null) {
            try {
                Bundle results = new Bundle();
                for (RemoteInput input : target.remoteInputs) {
                    results.putCharSequence(input.getResultKey(), target.reply);
                }
                Intent fillIn = new Intent();
                RemoteInput.addResultsToIntent(target.remoteInputs, fillIn, results);
                target.pendingIntent.send(context, 0, fillIn);
                Toast.makeText(context, "ZUXY: reply sent", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, "Could not send reply. Reply manually in WhatsApp.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(context, "ZUXY: reply rejected", Toast.LENGTH_SHORT).show();
        }
        if (id != null) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(id.hashCode());
        }
    }
}
