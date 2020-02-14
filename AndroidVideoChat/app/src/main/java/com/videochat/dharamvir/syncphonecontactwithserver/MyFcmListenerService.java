package com.videochat.dharamvir.syncphonecontactwithserver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.zip.Inflater;

/**
 * Created by dharamvir on 16/06/17.
 */
public class MyFcmListenerService extends FirebaseMessagingService {

    String mSessionID;
    String mToken;
    String mAPIKEY;
    String mFrom;
    String multi;

    @Override
    public void onMessageReceived(RemoteMessage message){
        String from = message.getFrom();
        Map data = message.getData();

        mSessionID = (String)data.get("SessionID");
        mToken = (String)data.get("Token");
        mFrom = (String)data.get("Name");
        mAPIKEY = (String)data.get("API_KEY");
        multi = (String)data.get("multi");


        Notification();
        Log.d("onMessageReceived", data.toString());
    }

    public void Notification() {

      /*  Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
        whatsappIntent.setType("text/plain");
        whatsappIntent.setPackage("com.whatsapp");
        whatsappIntent.putExtra(Intent.EXTRA_TEXT, "https://www.participateme.com/session/" + "1234");
        startActivity(whatsappIntent);*/


        Intent in = new Intent(this, OngoingCallActivity.class);
        in.putExtra("SESSION_ID", mSessionID);
        in.putExtra("API_KEY", mAPIKEY);
        in.putExtra("TOKEN", mToken);
        in.putExtra("From", mFrom);
        in.putExtra("multi", multi);

        PendingIntent pIntent = PendingIntent.getActivity(this, 0, in,
                PendingIntent.FLAG_UPDATE_CURRENT);

        //Create Notification using NotificationCompat.Builder
        NotificationCompat.Builder builder = (android.support.v7.app.NotificationCompat.Builder) new NotificationCompat.Builder(this)
                // Set Icon
                .setSmallIcon(R.drawable.app_icon_notification)
                // Set Ticker Message
                .setTicker("Incoming call from " + mFrom)
                // Set Title
                .setContentTitle(mFrom + " calling..")
                // Set Text
                .setContentText("Tap to pick the call")
                // Add an Action Button below Notification
                // Set PendingIntent into Notification
                .setContentIntent(pIntent)
                // Dismiss Notification
                .setAutoCancel(true);

        builder.setSound(Uri.parse("android.resource://"
                + getApplicationContext().getPackageName() + "/" + R.raw.tring_tring));

        // Create Notification Manager
        NotificationManager notificationmanager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Build Notification with Notification Manager
        notificationmanager.notify(0, builder.build());

    }

    @Override
    public void handleIntent(Intent intent) {

        if(intent.getExtras().getString("type") == null) {
            return;
        }

        Log.d("listener service", intent.getExtras().getString("type"));
        boolean isActive = true;

        isActive = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("isActive", false);

        if(intent.getExtras().getString("type").equals("declined") && isActive) {
            Intent i = new Intent();
            i.putExtra("action", "declined");
            i.setClass(this, OngoingCallActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        else if(intent.getExtras().getString("type").equals("missed") && isActive) {
            Intent i = new Intent();
            i.putExtra("action", "missed");
            i.setClass(this, OngoingCallActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        else if(intent.getExtras() != null && intent.getExtras().getString("type").equals("outgoing")) {
            //incoming call check

            Log.d("MyFcmListenerService", intent.getExtras().toString());
            Log.d("MyFcmListenerService", intent.getExtras().getString("SessionID"));
            Log.d("MyFcmListenerService", intent.getExtras().getString("Token"));
            Log.d("MyFcmListenerService", intent.getExtras().getString("multi"));
            Log.d("MyFcmListenerService", intent.getExtras().getString("type"));

            Intent in = new Intent(this, OngoingCallActivity.class);
            in.putExtra("SESSION_ID", intent.getExtras().getString("SessionID"));
            in.putExtra("API_KEY", intent.getExtras().getString("API_KEY"));
            in.putExtra("TOKEN", intent.getExtras().getString("Token"));
            in.putExtra("From", intent.getExtras().getString("Name"));
            in.putExtra("from_token", intent.getExtras().getString("from_token"));
            in.putExtra("multi", Boolean.parseBoolean(intent.getExtras().getString("multi")));
            // isMultiParty = getIntent().getBooleanExtra("multi", false);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        }
        Log.d("listener service", "notification received");

       // super.handleIntent(intent);
    }
}

