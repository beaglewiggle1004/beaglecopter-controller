package app.beaglecopter.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class ControllerActivity extends Activity implements View.OnSystemUiVisibilityChangeListener {

    private static final String TAG = ControllerActivity.class.getCanonicalName();

    public static final String RECEIVER_ADDRESS = "http://192.168.42.1:5050";
    public static final String RTSP_ADDRESS = "rtsp://192.168.42.1:883";
    public static final String RTSP_TEST_ADDRESS = "rtsp://192.168.25.60:8554/";

    /* Socket events */
    public static final int DISCONNECTED = -1;
    public static final int CONNECTED = 1;
    public static final int NEW_MESSAGE = 2;

    /* Joystick lever range 0 - 1023
     * If you want to raise reponsiveness, decrease the maximum value.
     * But the control will be more difficult
     * */
    public static final int JOYSTICK_MIN_VAL = 0;
    public static final int JOYSTICK_MAX_VAL = 1023;

    public static final int RESULT_SETTINGS = 1;

    private RelativeLayout mLayoutLeftStick;
    private RelativeLayout mLayoutRightStick;
    private ImageView mHealthIndicatorGreen;
    private ImageView mHealthIndicatorRed;
    private TextView textView1, textView2, textView3, textView4, textView5;
    private TextView mTextViewBatteryVoltage;
    private VideoView mVideoView;
    private Button mButtonSettings;

    private JoyStick mLeftStick;
    private JoyStick mRightStick;

    private Vibrator mVibrator;

    private CircularProgressBar mThrottleBar;
    private int mThrottleGauge = 0;

    private Socket mSocket;
    private Handler mSocketEventHandler;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mHealthIndicatorGreen = (ImageView) findViewById(R.id.health_indicator_green);
        mHealthIndicatorRed = (ImageView) findViewById(R.id.health_indicator_red);

        textView1 = (TextView) findViewById(R.id.textView1);
        textView2 = (TextView) findViewById(R.id.textView2);
        textView3 = (TextView) findViewById(R.id.textView3);
        textView4 = (TextView) findViewById(R.id.textView4);
        textView5 = (TextView) findViewById(R.id.textView5);
        mTextViewBatteryVoltage = (TextView) findViewById(R.id.text_view_battery_voltage);

        mVideoView = (VideoView) findViewById(R.id.videoView);
        mVideoView.setMediaController(new MediaController(this));
        mVideoView.setVideoURI(Uri.parse(RTSP_TEST_ADDRESS));
        mVideoView.start();

        mButtonSettings = (Button) findViewById(R.id.button_settings);
        mButtonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ControllerActivity.this, SettingsActivity.class);
                startActivityForResult(i, RESULT_SETTINGS);
            }
        });

        mThrottleBar = (CircularProgressBar) findViewById(R.id.throttle_bar);
        mLayoutLeftStick = (RelativeLayout) findViewById(R.id.layout_left_stick);

        mLeftStick = new JoyStick(getApplicationContext()
                , mLayoutLeftStick, R.drawable.image_button,
                JOYSTICK_MIN_VAL, JOYSTICK_MAX_VAL, JOYSTICK_MIN_VAL, JOYSTICK_MAX_VAL);
        mLeftStick.setThrottle(true);
        mLeftStick.setStickSize(120, 120);
        mLeftStick.setLayoutSize(300, 300);
        mLeftStick.setLayoutAlpha(60);
        mLeftStick.setStickAlpha(100);
        mLeftStick.setOffset(0);
        mLeftStick.setMinimumDistance(5);

        mLayoutLeftStick.setTag(mLeftStick);
        mLayoutLeftStick.setOnTouchListener(touchListener);

        mLayoutRightStick = (RelativeLayout) findViewById(R.id.layout_right_stick);

        mRightStick = new JoyStick(getApplicationContext()
                , mLayoutRightStick, R.drawable.image_button,
                JOYSTICK_MIN_VAL, JOYSTICK_MAX_VAL, JOYSTICK_MIN_VAL, JOYSTICK_MAX_VAL);
        mRightStick.setStickSize(120, 120);
        mRightStick.setLayoutSize(300, 300);
        mRightStick.setLayoutAlpha(60);
        mRightStick.setStickAlpha(100);
        mRightStick.setOffset(0);
        mRightStick.setMinimumDistance(5);

        mLayoutRightStick.setTag(mRightStick);
        mLayoutRightStick.setOnTouchListener(touchListener);

        // Set window flag to keep screen on
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        createSocket();

        mSocketEventHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECTED: {
                        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.tween);
                        mHealthIndicatorGreen.startAnimation(animation);
                        mHealthIndicatorGreen.setAlpha(1.0f);
                        mHealthIndicatorRed.setAlpha(0.2f);
                        mHealthIndicatorRed.clearAnimation();
                    }
                    break;
                    case DISCONNECTED: {
                        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.tween);
                        mHealthIndicatorRed.startAnimation(animation);
                        mHealthIndicatorRed.setAlpha(1.0f);
                        mHealthIndicatorGreen.setAlpha(0.2f);
                        mHealthIndicatorGreen.clearAnimation();
                    }
                    break;
                    case NEW_MESSAGE: {
                        try {
                            JSONObject jsonObject = new JSONObject(msg.obj.toString());
                            Double batteryVoltage = jsonObject.getDouble("battery_voltage");
                            mTextViewBatteryVoltage.setText(String.format("%.2fv", batteryVoltage));
                        } catch (JSONException e) {
                            Log.w(TAG, e.toString());
                        }
                    }
                    break;
                    default:

                }
            }
        };

        mThrottleBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);
        setSystemUiVisibility();

        connect();
        if (mSocket != null) {
            mSocketEventHandler.sendEmptyMessage(mSocket.connected() ? CONNECTED : DISCONNECTED);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSocket != null && mSocket.connected()) {
            mSocket.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SETTINGS:
                break;
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        setSystemUiVisibility();
    }

    private void setSystemUiVisibility() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    OnTouchListener touchListener = new OnTouchListener() {
        public boolean onTouch(View view, MotionEvent event) {
            JoyStick js = (JoyStick) view.getTag();

            if (js == null) return true;

            js.drawStick(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE
                    || event.getAction() == MotionEvent.ACTION_UP) {
                textView1.setText("X : " + String.valueOf(js.getX()));
                textView2.setText("Y : " + String.valueOf(js.getY()));
                textView3.setText("Angle : " + String.valueOf(js.getAngle()));
                textView4.setText("Distance : " + String.valueOf(js.getDistance()));

                int direction = js.get8Direction();
                if (direction == JoyStick.STICK_UP) {
                    textView5.setText("Direction : Up");
                } else if (direction == JoyStick.STICK_UPRIGHT) {
                    textView5.setText("Direction : Up Right");
                } else if (direction == JoyStick.STICK_RIGHT) {
                    textView5.setText("Direction : Right");
                } else if (direction == JoyStick.STICK_DOWNRIGHT) {
                    textView5.setText("Direction : Down Right");
                } else if (direction == JoyStick.STICK_DOWN) {
                    textView5.setText("Direction : Down");
                } else if (direction == JoyStick.STICK_DOWNLEFT) {
                    textView5.setText("Direction : Down Left");
                } else if (direction == JoyStick.STICK_LEFT) {
                    textView5.setText("Direction : Left");
                } else if (direction == JoyStick.STICK_UPLEFT) {
                    textView5.setText("Direction : Up Left");
                } else if (direction == JoyStick.STICK_NONE) {
                    textView5.setText("Direction : Center");
                }

                if ((direction == JoyStick.STICK_UP || direction == JoyStick.STICK_UPLEFT || direction == JoyStick.STICK_UPRIGHT
                        || direction == JoyStick.STICK_DOWN || direction == JoyStick.STICK_DOWNLEFT || direction == JoyStick.STICK_DOWNRIGHT)
                        && js.hasThrottle()) {
                    updateThrottleBar(js);
                }

                mSocket.emit("new packet", generatePacket());
            }
            return true;
        }
    };

    private void updateThrottleBar(JoyStick js) {
        mThrottleGauge = (int) (js.getY() / (float) (js.getMaxY() - js.getMinY()) * 100);
        mThrottleBar.setProgress(mThrottleGauge);
        mThrottleBar.setTitle(mThrottleGauge + "%");
    }

    private String generatePacket() {
        int throttle = mLeftStick.getY();
        int rudder = mLeftStick.getX(); // Yaw
        int elevator = mRightStick.getY(); // Pitch
        int aileron = mRightStick.getX(); // Roll

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("throttle", throttle);
            jsonObject.put("rudder", rudder);
            jsonObject.put("elevator", elevator);
            jsonObject.put("aileron", aileron);
        } catch (JSONException e) {
            Log.w(TAG, e.toString());
        }

        return jsonObject.toString();
    }

    private void createSocket() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String hostAddress = "http://" + sharedPrefs.getString("host_address", RECEIVER_ADDRESS);
        try {
            if (mSocket != null) {
                mSocket.close();
            }
            mSocket = IO.socket(hostAddress);
        } catch (URISyntaxException e) {
            Toast.makeText(this, R.string.socket_creation_failed, Toast.LENGTH_LONG).show();
            Log.w(TAG, e.toString());
        }
    }

    private void connect() {
        // Connect to server on BBB
        createSocket();

        if (mSocket != null) {
            mSocket.connect();
            mSocket.on("connect", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    mSocketEventHandler.sendEmptyMessage(CONNECTED);
                    mVibrator.vibrate(new long[]{0, 200, 1000}, -1);
                }
            });
            mSocket.on("disconnect", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    mSocketEventHandler.sendEmptyMessage(DISCONNECTED);
                    mVibrator.vibrate(new long[]{0, 200, 200, 300, 500}, -1);
                }
            });
            mSocket.on("new_message", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Message message = new Message();
                    message.what = NEW_MESSAGE;
                    message.obj = args[0];
                    mSocketEventHandler.sendMessage(message);
                }
            });
        }
    }
}
