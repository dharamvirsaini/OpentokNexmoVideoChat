package com.videochat.dharamvir.syncphonecontactwithserver;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.iid.FirebaseInstanceId;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Connection;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class OngoingCallActivity extends AppCompatActivity
        implements
        Publisher.PublisherListener,
        Session.SessionListener, View.OnClickListener, Session.SignalListener, EasyPermissions.PermissionCallbacks {

    private static final String TAG = OngoingCallActivity.class.getSimpleName();

    private final int MAX_NUM_SUBSCRIBERS = 4;

    private static final int RC_SETTINGS_SCREEN_PERM = 123;
    private static final int RC_VIDEO_APP_PERM = 124;

    private Session mSession;
    private Publisher mPublisher;
    private String shareID;
    private BroadcastReceiver receiver;

    private int numUserConnected;
    private boolean endCall = false;
    private int seconds, minutes, hours;

   // private ArrayList<Subscriber> mSubscribers = new ArrayList<Subscriber>();
    private HashMap<Stream, Subscriber> mSubscriberStreams = new HashMap<Stream, Subscriber>();

    private RelativeLayout mPublisherViewContainer;
    private RelativeLayout mPublisherViewContainer_FrameLayout;

    private RelativeLayout mSubscriberViewContainer;
    private Subscriber mSubscriber;

    boolean isMultiParty = true;
    boolean isIncoming = false;
    boolean isWebOnly = false;
    private final int PICK_IMAGE_REQUEST = 574;

    String callerName;
    List<String> names, tokens;
    Boolean noResponse = false;
    List<SignalMessage> mMessages;
    private RecyclerView mRecList;
    private String mFrom_Token;
    private MediaPlayer mp;
    private Vibrator vib;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_bg);
        Uri defaultRintoneUri = RingtoneManager.getActualDefaultRingtoneUri(this.getApplicationContext(), RingtoneManager.TYPE_RINGTONE);
       // Ringtone defaultRingtone = RingtoneManager.getRingtone(this, defaultRintoneUri);
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("isActive", true).commit();

        mp = MediaPlayer.create(OngoingCallActivity.this, defaultRintoneUri);

        mMessages = new ArrayList<>();
        if(getIntent().getExtras() != null && getIntent().getBooleanExtra("web", false) != false)
        {
            isMultiParty = false;
            isWebOnly = true;
        }
        else if(getIntent().getExtras() != null && getIntent().getStringExtra("API_KEY") != null)
        {
            //incoming call request
            isIncoming = true;
            Log.d("apikey", getIntent().getStringExtra("API_KEY") + "  " + getIntent().getStringExtra("SESSION_ID") + "   " + getIntent().getStringExtra("TOKEN"));
            OpenTokConfig.API_KEY = getIntent().getStringExtra("API_KEY");
            OpenTokConfig.SESSION_ID = getIntent().getStringExtra("SESSION_ID");
            OpenTokConfig.TOKEN = getIntent().getStringExtra("TOKEN");
            callerName = getIntent().getStringExtra("From");
            isMultiParty = getIntent().getBooleanExtra("multi", false);
            mFrom_Token = getIntent().getStringExtra("from_token");

            registerRingtoneChangeReceiver();
            setRingtoneMode();

            ((RelativeLayout) findViewById(R.id.multi_party)).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mp != null) {
                        mp.stop();
                        mp.release();
                        mp = null;
                    }
                    if(vib != null) {
                        vib.cancel();
                        vib = null;
                    }
                    if(!endCall)
                    {
                       onCallMissed();
                    }
                }
            }, 40000);

        }
        else {
            names = getIntent().getStringArrayListExtra("names");
            tokens = getIntent().getStringArrayListExtra("tokens");
            isMultiParty = getIntent().getBooleanExtra("multi", false);
        }

        if (isMultiParty) {
            ((RelativeLayout) findViewById(R.id.single_party)).setVisibility(View.GONE);
          //  mPublisherViewContainer = (RelativeLayout) findViewById(R.id.publisherview);
        }
        else {
            ((RelativeLayout) findViewById(R.id.multi_party)).setVisibility(View.GONE);

            mSubscriberViewContainer = (RelativeLayout) findViewById(R.id.subscriber_container);
        }

        mPublisherViewContainer_FrameLayout = (RelativeLayout) findViewById(R.id.publisher_container_framelayout);
        findViewById(R.id.toggle_text).setOnClickListener(this);
        findViewById(R.id.swap_camera).setOnClickListener(this);
        findViewById(R.id.end_call_image).setOnClickListener(this);
        findViewById(R.id.send_text).setOnClickListener(this);
        findViewById(R.id.send_picture).setOnClickListener(this);
        findViewById(R.id.minimize_chat).setOnClickListener(this);
        findViewById(R.id.share_image).setOnClickListener(this);
        findViewById(R.id.end_call).setOnClickListener(this);
        findViewById(R.id.accept_call).setOnClickListener(this);


        mRecList = (RecyclerView)findViewById(R.id.text_chat);
        mRecList.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        mRecList.setLayoutManager(llm);



        mRecList.setAdapter(new MessageAdapter(mMessages, this));

        if (isIncoming == true) {
           // connectToSession();

            findViewById(R.id.end_button_layout).setVisibility(View.INVISIBLE);
            findViewById(R.id.call_accept_layout).setVisibility(View.VISIBLE);
            ((TextView)findViewById(R.id.textView2)).setText(callerName);
            //((TextView)findViewById(R.id.textView2)).setTextSize(32);
            ((TextView)findViewById(R.id.textView3)).setText("Incoming call..");
            //((TextView)findViewById(R.id.textView3)).setTextSize(22);

            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            + WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | +WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | +WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
           // requestPermissions();
        }
        else {
            findViewById(R.id.end_button_layout).setVisibility(View.VISIBLE);
            findViewById(R.id.call_accept_layout).setVisibility(View.GONE);
            if(isWebOnly)
            {
                ((TextView)findViewById(R.id.textView2)).setText("Waiting for users..");
                findViewById(R.id.share_image).setVisibility(View.GONE);
            }

           else if(names.size() > 1)
            ((TextView)findViewById(R.id.textView2)).setText(names.get(0) + " and " + Integer.toString(names.size() - 1) + " others");
            else
                ((TextView)findViewById(R.id.textView2)).setText(names.get(0));

           // connectToSession();
            requestPermissions();
        }

        final ToggleButton  toggle = (ToggleButton) findViewById(R.id.toggle);
        final ToggleButton  toggle_video = (ToggleButton) findViewById(R.id.toggle_video);


        toggle_video.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d("DisplayContactsActivity", "checked");
                    toggle_video.setBackgroundResource(R.drawable.no_video);
                    if(mPublisher != null)
                        mPublisher.setPublishVideo(false);
                    // The toggle is enabled
                } else {
                    // The toggle is disabled
                    Log.d("DisplayContactsActivity", "unchecked");
                    toggle_video.setBackgroundResource(R.drawable.video);
                    if(mPublisher != null)
                        mPublisher.setPublishVideo(true);
                }
            }
        });

        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d("DisplayContactsActivity", "checked");
                    toggle.setBackgroundResource(R.drawable.mute);
                    if(mPublisher != null)
                        mPublisher.setPublishAudio(false);
                    // The toggle is enabled
                } else {
                    // The toggle is disabled
                    Log.d("DisplayContactsActivity", "unchecked");
                    toggle.setBackgroundResource(R.drawable.unmute);
                    if(mPublisher != null)
                        mPublisher.setPublishAudio(true);
                }
            }
        });



    }

    private void setRingtoneMode() {

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:

                Log.i("MyApp","Silent mode");
                break;
            case AudioManager.RINGER_MODE_VIBRATE:



                vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                long[] pattern = {0, 100, 1000};
                vib.vibrate(pattern, 0);
                Log.i("MyApp","Vibrate mode");
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                Log.i("MyApp","Normal mode");


               // mp = MediaPlayer.create(OngoingCallActivity.this, R.raw.tring_tring);
                mp.setLooping(true);
                mp.start();
                break;
        }

    }
    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        if(intent.getStringExtra("action").equals("declined"))
        {
            ((TextView)findViewById(R.id.textView3)).setText("Call declined..");
            this.finish();
        }
        else if(intent.getStringExtra("action").equals("missed")) {
            endCall = true;
            onCallMissed();
        }
    }

    private void onCallMissed() {

        if(mp != null) {
            mp.stop();
            mp.release();
            mp = null;
        }
        if(vib != null) {
            vib.cancel();
            vib = null;
        }

        ArrayList<String> tokens = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();

        tokens.add(mFrom_Token);
        names.add(callerName);

        Intent in = new Intent(OngoingCallActivity.this, OngoingCallActivity.class);

        in.putStringArrayListExtra("tokens", tokens);
        in.putStringArrayListExtra("names", names);
        in.putExtra("multi", false);

        PendingIntent pIntent = PendingIntent.getActivity(OngoingCallActivity.this, 0, in,
                PendingIntent.FLAG_UPDATE_CURRENT);

        //Create Notification using NotificationCompat.Builder
        NotificationCompat.Builder builder = (android.support.v7.app.NotificationCompat.Builder) new NotificationCompat.Builder(OngoingCallActivity.this)
                // Set Icon
                .setSmallIcon(R.drawable.app_icon_sky_notification)
                // Set Ticker Message
                .setContentTitle("Missed call from " + callerName)
                // Set Text
                .setContentText("Tap to callback")
                // Set Title
                // Add an Action Button below Notification
                // Set PendingIntent into Notification
                .setContentIntent(pIntent)
                // Dismiss Notification
                .setAutoCancel(true);

        //   builder.setSound(Uri.parse("android.resource://"
        //      + getApplicationContext().getPackageName() + "/" + R.raw.tring_tring));

        // Create Notification Manager
        NotificationManager notificationmanager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Build Notification with Notification Manager
        notificationmanager.notify(0, builder.build());


        OngoingCallActivity.this.finish();
    }

    private void notifyEndCall()
    {
       // new Thread(new Runnable() {
          //  @Override
            //public void run() {
                String requestUrl = Constants.NOTIFY_CALLER_REQUEST_URL;

                JSONObject jsonObject = new JSONObject();
                ArrayList<String> to_tokens = new ArrayList<String>();
                to_tokens.add(mFrom_Token);

                try {
                    jsonObject.put("type", "declined");
                    jsonObject.put("to", to_tokens);


                } catch (JSONException j) {
                    j.printStackTrace();
                }

                postJSON(requestUrl, jsonObject, "finish");
               // OngoingCallActivity.this.finish();
           // }
      //  }).start();
    }
    private void registerRingtoneChangeReceiver() {

         receiver=new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {

                AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

                switch (am.getRingerMode()) {
                    case AudioManager.RINGER_MODE_SILENT:
                        if(mp != null)
                            mp.stop();

                        if(vib != null)
                            vib.cancel();
                        Log.i("MyApp","Silent mode");
                        break;
                    case AudioManager.RINGER_MODE_VIBRATE:
                        if(mp != null)
                            mp.stop();
                        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                        long[] pattern = {0, 100, 1000};
                        vib.vibrate(pattern, 0);
                        Log.i("MyApp","Vibrate mode");
                        break;
                    case AudioManager.RINGER_MODE_NORMAL:
                        if(vib != null)
                            vib.cancel();

                        Log.i("MyAppReceiver","Normal mode");
                       // mp = MediaPlayer.create(OngoingCallActivity.this, R.raw.tring_tring);
                        mp.setLooping(true);
                        mp.start();
                        break;
                }

            }
        };

        IntentFilter filter=new IntentFilter(
                AudioManager.RINGER_MODE_CHANGED_ACTION);

        registerReceiver(receiver,filter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

        Log.d(TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size());
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

        Log.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());

        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this)
                    .setTitle(getString(R.string.title_settings_dialog))
                    .setRationale(getString(R.string.rationale_ask_again))
                    .setPositiveButton(getString(R.string.setting))
                    .setNegativeButton(getString(R.string.cancel))
                    .setRequestCode(RC_SETTINGS_SCREEN_PERM)
                    .build()
                    .show();
        }
    }

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private void requestPermissions() {

        String[] perms = {android.Manifest.permission.INTERNET, android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO };
        if (EasyPermissions.hasPermissions(this, perms)) {
            // if there is no server URL set
            connectToSession();
        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.rationale_video_app), RC_VIDEO_APP_PERM, perms);
        }
    }

    private void connectToSession() {

        final Long time = System.currentTimeMillis();
        Log.d("time is " , Long.toString(time));
        Cache cache = new DiskBasedCache(getCacheDir(), 1024 * 1024); // 1MB cap

// Set up the network to use HttpURLConnection as the HTTP client.
        Network network = new BasicNetwork(new HurlStack());

// Instantiate the RequestQueue with the cache and network.
        RequestQueue requestQueue = new RequestQueue(cache, network);
        requestQueue.start();

        String url = null;

        if(!isIncoming) {
            url = Constants.SERVER_URL + Long.toString(time);
            shareID = Long.toString(time);
        }
        else {
            shareID = OpenTokConfig.TOKEN;
            url = Constants.SERVER_URL + OpenTokConfig.TOKEN;
        }


        Log.d("url is " , url);

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>(){
                    @Override
                    public void onResponse(String response) {
                        Log.d("Response is ", response);
                       // mRequestQueue.stop();

                        JSONObject obj = null;
                        try {
                            obj = new JSONObject(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            //return null;
                        }

                        try {
                            OpenTokConfig.API_KEY = obj.getString("apiKey");
                            OpenTokConfig.SESSION_ID = obj.getString("sessionId");
                            OpenTokConfig.TOKEN = obj.getString("token");
                            OpenTokConfig.time = Long.toString(time);

                            if(!isIncoming){

                                mSession = new Session.Builder(OngoingCallActivity.this, OpenTokConfig.API_KEY, OpenTokConfig.SESSION_ID).build();
                                mSession.setSessionListener(OngoingCallActivity.this);
                                mSession.connect(OpenTokConfig.TOKEN);

                                if(!isWebOnly)
                                notifyCaller();
                                else
                                    shareLink();
                            }

                            else
                            {
                                mSession = new Session.Builder(OngoingCallActivity.this, OpenTokConfig.API_KEY, OpenTokConfig.SESSION_ID).build();
                                mSession.setSessionListener(OngoingCallActivity.this);
                                mSession.connect(OpenTokConfig.TOKEN);

                                ((TextView)findViewById(R.id.textView2)).setText(callerName);
                            }

                            mSession.setSignalListener(OngoingCallActivity.this);
                            Log.d("credentials are ", obj.getString("apiKey") + obj.getString("sessionId") + obj.getString("token"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        // Display the first 500 characters of the response string.
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

                Toast.makeText(OngoingCallActivity.this, "Internal error occured! Please try again!", Toast.LENGTH_SHORT).show();
            }
        });
// Add the request to the RequestQueue.
        requestQueue.add(stringRequest);
    }

    @Override
    public void onClick(View v) {

        switch(v.getId())
        {
            case R.id.swap_camera:
                if(mPublisher != null)
                    mPublisher.cycleCamera();
                break;

            case R.id.end_call_image:

                if(numUserConnected == 0){
                    notifyMissedCall();
                    break;
                }
                endCall();

                break;

            case R.id.share_image:
                shareLink();
                break;

            case R.id.toggle_text:
                startChat();
                break;

            case R.id.send_text:
                sendTextMessage();
                break;

            case R.id.send_picture:
                sendPictureMessage();
                break;

            case R.id.end_call:
                endCall = true;

                if(!isMultiParty)
                notifyEndCall();

                    if(mp != null) {
                        mp.stop();
                        mp.release();
                        mp = null;
                    }
                    if(vib != null) {
                        vib.cancel();
                        vib = null;
                    }
                    if(isMultiParty)
                this.finish();

                break;

            case R.id.accept_call:

                if(mp != null) {
                    mp.stop();
                    mp.release();
                    mp = null;
                }
                if(vib != null) {
                    vib.cancel();
                    vib = null;
                }
                endCall = true;

                findViewById(R.id.end_button_layout).setVisibility(View.VISIBLE);
                findViewById(R.id.call_accept_layout).setVisibility(View.GONE);
                ((TextView)findViewById(R.id.textView3)).setText("Connecting..");

                //AudioManager am1 = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

               /* if(am1.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                    if(mp != null)
                        mp.stop();
                }
                else if(am1.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                    if(vib != null)
                    vib.cancel();
                }*/

                requestPermissions();
                break;

            case R.id.minimize_chat:
                ((LinearLayout)findViewById(R.id.end_button_layout)).setVisibility(View.VISIBLE);
                ((LinearLayout)findViewById(R.id.text_chat_layout)).setVisibility(View.GONE);
                break;


        }

    }

    private void notifyMissedCall() {


        new Thread(new Runnable() {
            @Override
            public void run() {
                String requestUrl = Constants.NOTIFY_CALLER_REQUEST_URL;

                JSONObject jsonObject = new JSONObject();

                try {
                    jsonObject.put("type", "missed");
                    jsonObject.put("to", tokens);


                } catch (JSONException j) {
                    j.printStackTrace();
                }

                postJSON(requestUrl, jsonObject, "finish");



              //  Log.e("response is", "" + response);

            }
        }).start();

       // OngoingCallActivity.this.finish();

    }

    private void shareLink() {

        Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
        whatsappIntent.setType("text/plain");
        whatsappIntent.setPackage("com.whatsapp");
        whatsappIntent.putExtra(Intent.EXTRA_TEXT, "https://www.participateme.com/session/" + shareID);
        try {
            startActivity(whatsappIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Please install Whatsapp app to share link with Whatsapp users", Toast.LENGTH_SHORT).show();
            this.finish();
            e.printStackTrace();
        }

    }

    private void sendTextMessage() {

        EditText text = (EditText)findViewById(R.id.chat_text);

        if(!text.getText().toString().trim().isEmpty()) {

            JSONObject msg = new JSONObject();

            try {
                msg.put("name", getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("name", null));
                msg.put("code", getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("phone", null));
                msg.put("image", "n");
                msg.put("data", text.getText().toString().trim());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mSession.sendSignal("default", msg.toString());

            SignalMessage message = new SignalMessage();
            message.setData(text.getText().toString().trim());
            message.setType(Constants.TYPE_SELF_TEXT);
            mMessages.add(message);
            text.setText("");
            mRecList.getAdapter().notifyDataSetChanged();
        }

    }

    private void sendPictureMessage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data!=null && data.getData()!=null){

            Uri imagePath = data.getData();

            try {
                //Getting the Bitmap from Gallery
                Bitmap bitmap2 = MediaStore.Images.Media.getBitmap(getContentResolver(), imagePath);

                //Scaling the bitmap as it might cause issues OPENGL RENDERING
                //  Bitmap bitmap1= new Bitma(getResources() , bitmap2).getBitmap();
                int nh = (int) ( bitmap2.getHeight() * (128.0 / bitmap2.getWidth()) );
                String image = getStringImage(Bitmap.createScaledBitmap(bitmap2, 128, nh, true));
               // String image = getStringImage(bitmap2);
                JSONObject msg = new JSONObject();

                try {
                    msg.put("name", getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("name", null));
                    msg.put("image", "y");
                    msg.put("data", image);
                    msg.put("code", getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("phone", null));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mSession.sendSignal("default", msg.toString());

                SignalMessage message = new SignalMessage();
                message.setData(image);
                message.setType(Constants.TYPE_SELF_IMAGE);
                mMessages.add(message);
                mRecList.getAdapter().notifyDataSetChanged();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getStringImage(Bitmap bmp){
        if (bmp != null){

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);
        }

        return "noimage";
    }

    private void startChat() {

        ((LinearLayout)findViewById(R.id.end_button_layout)).setVisibility(View.GONE);

        ((LinearLayout)findViewById(R.id.text_chat_layout)).setVisibility(View.VISIBLE);

       /* if(textLayout.getVisibility() == View.VISIBLE)
        {
            textLayout.setVisibility(View.GONE);
        }
        else {
            textLayout.setVisibility(View.VISIBLE);
        }*/

    }

    @Override
    public void onSignalReceived(Session session, String type, String message, Connection connection) {
        Log.d(TAG, "message received is " + message);

        if(mMessages.size() > 2) {
            mRecList.scrollToPosition(mMessages.size() - 1);
        }

        try {
            JSONObject json = new JSONObject(message);
            String phone = json.getString("code");
            String name = json.getString("name");
            String image = json.getString("image");
            String data = json.getString("data");

            if (!phone.equals(getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("phone", null))) {

                SignalMessage signal = new SignalMessage();
                signal.setData(data);
                signal.setName(name);
                signal.setCode(phone);

                if (image.equals("y")) {
                    signal.setType(Constants.TYPE_REMOTE_IMAGE);
                } else {
                    signal.setType(Constants.TYPE_REMOTE_TEXT);
                }

                mMessages.add(signal);

                if(findViewById(R.id.text_chat_layout).getVisibility() == View.GONE)
                    findViewById(R.id.notification_text).setVisibility(View.VISIBLE);
                else
                    findViewById(R.id.notification_text).setVisibility(View.GONE);


                mRecList.getAdapter().notifyDataSetChanged();
            }
            }
        catch(Exception e){
                e.printStackTrace();
            }
        }


void notifyCaller() {

    String requestUrl = Constants.NOTIFY_CALLER_REQUEST_URL;

    JSONObject jsonObject = new JSONObject();
    // JSONObject js = new JSONObject();

    try {

        jsonObject.put("from", getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("name", null));
        jsonObject.put("device_tokens", tokens);
        jsonObject.put("SessionID", OpenTokConfig.SESSION_ID);
        jsonObject.put("Token", OpenTokConfig.time);
        jsonObject.put("API_KEY", OpenTokConfig.API_KEY);
        jsonObject.put("multi", Boolean.toString(isMultiParty));
        jsonObject.put("type", "outgoing");
        jsonObject.put("from_token", FirebaseInstanceId.getInstance().getToken());


    } catch (JSONException j) {
        j.printStackTrace();
    }

    //  String response = DisplayContactsActivity.postObject(requestUrl, jsonObject);
    postJSON(requestUrl, jsonObject, "notfinish");

}

    protected class NotifyCaller extends AsyncTask<String,Void,Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(String... params) {

            String requestUrl = Constants.NOTIFY_CALLER_REQUEST_URL;

            JSONObject jsonObject = new JSONObject();
           // JSONObject js = new JSONObject();

            try {

                jsonObject.put("from", getSharedPreferences(PhoneAuthActivity.MyPREFERENCES, MODE_PRIVATE).getString("name", null));
                jsonObject.put("device_tokens", tokens);
                jsonObject.put("SessionID", OpenTokConfig.SESSION_ID);
                jsonObject.put("Token", OpenTokConfig.time);
                jsonObject.put("API_KEY", OpenTokConfig.API_KEY);
                jsonObject.put("multi", Boolean.toString(isMultiParty));
                jsonObject.put("type", "outgoing");
                jsonObject.put("from_token", FirebaseInstanceId.getInstance().getToken());


            } catch (JSONException j) {
                j.printStackTrace();
            }

          //  String response = DisplayContactsActivity.postObject(requestUrl, jsonObject);
            postJSON(requestUrl, jsonObject, "finish");

          /*  if (response == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(OngoingCallActivity.this, "Internal error occured! Please try again!", Toast.LENGTH_SHORT).show();
                    }
                });

                return null;
            }*/

          //  Log.e("response is", "" + response);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            ((RelativeLayout) findViewById(R.id.multi_party)).postDelayed(new Runnable() {
                @Override
                public void run() {

                    if(numUserConnected == 0)
                    {
                        noResponse = true;
                        endCall();
                    }

                }
            }, 40000);

            super.onPostExecute(aVoid);
        }
    }

    private void postJSON(String requestUrl, final JSONObject js, final String type) {

        final RequestQueue requestQueue = Volley.newRequestQueue(this);
        StringRequest jsonObjReq = new StringRequest(
                Request.Method.POST, requestUrl,
                new Response.Listener<String>() {

                    @Override
                    public void onResponse(String response) {

                        // Log.d(TAG, response.toString());
                        if(type.equals("finish"))
                        {
                            OngoingCallActivity.this.finish();
                        }

                        if (response == null){
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(OngoingCallActivity.this, "Network error occured! Please try again!", Toast.LENGTH_SHORT).show();
                                    OngoingCallActivity.this.finish();
                                    return;
                                }


                            });
                    }
                            Log.d(TAG, response.toString());

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((RelativeLayout) findViewById(R.id.multi_party)).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {

                                            if(numUserConnected == 0)
                                            {
                                                noResponse = true;
                                                endCall();
                                            }

                                        }
                                    }, 40000);
                                }
                            });


                            // return null;
                        }
                        //Log.d("postjson", )

                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Error in onErrorResponse: " + error.getMessage());
                if(type.equals("finish"))
                    OngoingCallActivity.this.finish();
                //hideProgressDialog();
            }
        }) {

            /**
             * Passing some request headers
             */


                @Override
                public byte[] getBody () throws AuthFailureError {
                    Log.d(TAG, js.toString());
                return js.toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json";
            }

        };

        requestQueue.add(jsonObjReq);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");

        super.onStart();
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart");

        super.onRestart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();

        if (mSession == null) {
            return;
        }
        mSession.onResume();

if(mPublisher != null)
        mPublisher.setPublishVideo(true);

    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();

        if (mSession == null) {
            return;
        }
        mSession.onPause();

        if(mPublisher != null)
        mPublisher.setPublishVideo(false);

        if (isFinishing()) {
            disconnectSession();
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onPause");

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("isActive", false).commit();

        if(receiver != null)
unregisterReceiver(receiver);

        disconnectSession();


        super.onDestroy();
    }


    @Override
    public void onConnected(Session session) {
        Log.d(TAG, "onConnected: Connected to session " + session.getSessionId());

       mPublisher = new Publisher.Builder(OngoingCallActivity.this).name("publisher").build();

        mPublisher.setPublisherListener(this);
        mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);


    //    if(isMultiParty)
      //      mPublisherViewContainer.addView(mPublisher.getView());
        //else
            mPublisherViewContainer_FrameLayout.addView(mPublisher.getView());



        if (mPublisher.getView() instanceof GLSurfaceView) {
            ((GLSurfaceView) mPublisher.getView()).setZOrderMediaOverlay(true);
        }

        mSession.publish(mPublisher);
    }

    @Override
    public void onDisconnected(Session session) {
        Log.d(TAG, "onDisconnected: disconnected from session " + session.getSessionId());

        mSession = null;
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.d(TAG, "onError: Error (" + opentokError.getMessage() + ") in session " + session.getSessionId());

        //Toast.makeText(this, "Session error. See the logcat please.", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        ((RelativeLayout) findViewById(R.id.main_layout)).setBackgroundColor(ContextCompat.getColor(this, R.color.call_bg));
        ((LinearLayout) findViewById(R.id.color_layout)).setVisibility(View.INVISIBLE);
        ((ImageView) findViewById(R.id.app_icon_left)).setVisibility(View.INVISIBLE);

        Log.d(TAG, "onStreamReceived: New stream " + stream.getStreamId() + " in session " + session.getSessionId());
timer();
        numUserConnected++;
stream.getConnection().getData();
        if(numUserConnected == 2 && !isMultiParty)
        {
            isMultiParty = true;
            ((RelativeLayout) findViewById(R.id.single_party)).setVisibility(View.GONE);
            ((RelativeLayout) findViewById(R.id.multi_party)).setVisibility(View.VISIBLE);

            mSubscriberViewContainer.removeAllViews();

            mSubscriberStreams.put(mSubscriber.getStream(), mSubscriber);

            int position = mSubscriberStreams.size() - 1;
            int id = getResources().getIdentifier("subscriberview" + (new Integer(position)).toString(), "id", OngoingCallActivity.this.getPackageName());
            RelativeLayout subscriberViewContainer = (RelativeLayout) findViewById(id);

            mSubscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            if (mSubscriber.getView() instanceof GLSurfaceView) {
               // ((GLSurfaceView) mSubscriber.getView()).setZOrderOnTop(false);
            }
            subscriberViewContainer.addView(mSubscriber.getView());

        }

        if(findViewById(R.id.share_image).getVisibility() == View.GONE)
        {
            findViewById(R.id.share_image).setVisibility(View.VISIBLE);
        }
        if( ((RelativeLayout)findViewById(R.id.calling_text_layout)).getVisibility() == View.VISIBLE)
            ((RelativeLayout)findViewById(R.id.calling_text_layout)).setVisibility(View.INVISIBLE);

        if(!isMultiParty)
        {
            if (mSubscriber == null) {
                mSubscriber = new Subscriber.Builder(this, stream).build();
                mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
                mSession.subscribe(mSubscriber);
                if (mSubscriber.getView() instanceof GLSurfaceView) {
                   // ((GLSurfaceView) mSubscriber.getView()).setZOrderMediaOverlay(false);
                }
                mSubscriberViewContainer.addView(mSubscriber.getView());
            }

            return;
        }

        if (mSubscriberStreams.size() + 1 > MAX_NUM_SUBSCRIBERS) {
            Toast.makeText(this, "New subscriber ignored. MAX_NUM_SUBSCRIBERS limit reached.", Toast.LENGTH_LONG).show();
            return;
        }

        final Subscriber subscriber = new Subscriber.Builder(OngoingCallActivity.this, stream).build();
        mSession.subscribe(subscriber);
       // mSubscribers.add(subscriber);
        mSubscriberStreams.put(stream, subscriber);

        int position = mSubscriberStreams.size() - 1;
        int id = getResources().getIdentifier("subscriberview" + (new Integer(position)).toString(), "id", OngoingCallActivity.this.getPackageName());
        RelativeLayout subscriberViewContainer = (RelativeLayout) findViewById(id);

        subscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
        if (subscriber.getView() instanceof GLSurfaceView) {
           // ((GLSurfaceView) subscriber.getView()).setZOrderMediaOverlay(true);
        }
        subscriberViewContainer.addView(subscriber.getView());

    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.d(TAG, "onStreamDropped: Stream " + stream.getStreamId() + " dropped from session " + session.getSessionId());

        numUserConnected--;

        if(!isMultiParty)
        {
            if (mSubscriber != null) {
                mSubscriber = null;
                mSubscriberViewContainer.removeAllViews();
            }

            endCall();
            return;
        }




        Subscriber subscriber = mSubscriberStreams.get(stream);
        if (subscriber == null) {
            return;
        }

       // int position = mSubscribers.indexOf(subscriber);
        //int id = getResources().getIdentifier("subscriberview" + (new Integer(position)).toString(), "id", OngoingCallActivity.this.getPackageName());

       // mSubscribers.remove(subscriber);
        mSubscriberStreams.remove(stream);

        RelativeLayout subscriberViewContainer = (RelativeLayout) subscriber.getView().getParent();

       //old RelativeLayout subscriberViewContainer = (RelativeLayout) findViewById(id);
        subscriberViewContainer.removeView(subscriber.getView());

        if(numUserConnected == 0)
            endCall();

    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        Log.d(TAG, "onStreamCreated: Own stream " + stream.getStreamId() + " created");
    }

    private void endCall()
    {
endCall = true;

        if(isWebOnly)
        {
            ((TextView)findViewById(R.id.textView2)).setVisibility(View.GONE);
        }
        if(!noResponse)
        ((TextView)findViewById(R.id.textView3)).setText("Call Ended...");
        else
            ((TextView)findViewById(R.id.textView3)).setText("No Response.. Call Ended...");

        ((LinearLayout)findViewById(R.id.text_chat_layout)).setVisibility(View.GONE);

        ((RelativeLayout)findViewById(R.id.calling_text_layout)).setVisibility(View.VISIBLE);

        ((TextView)findViewById(R.id.textView3)).postDelayed(new Runnable() {
            @Override
            public void run() {
               disconnectSession();
                OngoingCallActivity.this.finish();

            }
        }, 3000);
    }


    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        Log.d(TAG, "onStreamDestroyed: Own stream " + stream.getStreamId() + " destroyed");
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        Log.d(TAG, "onError: Error (" + opentokError.getMessage() + ") in publisher");

       // Toast.makeText(this, "Session error. See the logcat please.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void disconnectSession() {
        if (mSession == null) {
            return;
        }

        for (HashMap.Entry<Stream, Subscriber> entry : mSubscriberStreams.entrySet())
        {
            System.out.println(entry.getKey() + "/" + entry.getValue());

            Subscriber subscriber = entry.getValue();

            if (subscriber != null) {
                mSession.unsubscribe(subscriber);
                subscriber.destroy();
            }
        }

     /*   if (mSubscribers.size() > 0) {
            for (Subscriber subscriber : mSubscribers) {
                if (subscriber != null) {
                    mSession.unsubscribe(subscriber);
                    subscriber.destroy();
                }
            }
        }*/

        if (mPublisher != null) {
                mPublisherViewContainer_FrameLayout.removeView(mPublisher.getView());
            mSession.unpublish(mPublisher);
            mPublisher.destroy();
            mPublisher = null;
        }
        mSession.disconnect();
    }

    private void add() {
        seconds++;
        if (seconds >= 60) {
            seconds = 0;
            minutes++;
            if (minutes >= 60) {
                minutes = 0;
                hours++;
            }
        }

        if(hours != 0 && endCall) {
            String time = (hours > 9 ? hours : "0" + hours) + ":" + (minutes != 0 ? (minutes > 9 ? minutes : "0" + minutes) : "00") + ":" + (seconds > 9 ? seconds : "0" + seconds);

            ((TextView)findViewById(R.id.textView3)).setText(time);
        }
        else if(endCall)
            ((TextView)findViewById(R.id.textView3)).setText((minutes != 0 ? (minutes > 9 ? minutes : "0" + minutes) : "00") + ":" + (seconds > 9 ? seconds : "0" + seconds));

        if(!endCall)
        timer();
    }

    private void timer() {
        ((TextView)findViewById(R.id.textView3)).postDelayed(new Runnable() {
            @Override
            public void run() {
                add();
            }
        }, 1000);
      //  t = setTimeout(add, 1000);
    }
}
