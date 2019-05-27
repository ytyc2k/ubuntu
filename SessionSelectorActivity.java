package fm.icelink.chat.websync4;


import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.*;
import android.content.*;
import android.text.*;
import android.util.Log;
import android.view.*;
import android.widget.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;


public class SessionSelectorActivity extends AppCompatActivity {

    private static String[] Names = {
            "Aurora",
            "Argus",
            "Baker",
            "Blackrock",
            "Caledonia",
            "Coquitlam",
            "Doom",
            "Dieppe",
            "Eon",
            "Elkhorn",
            "Fairweather",
            "Finlayson",
            "Garibaldi",
            "Grouse",
            "Hoodoo",
            "Helmer",
            "Isabelle",
            "Itcha",
            "Jackpass",
            "Josephine",
            "Klinkit",
            "King Albert",
            "Lilliput",
            "Lyall",
            "Mallard",
            "Douglas",
            "Nahlin",
            "Normandy",
            "Omega",
            "One Eye",
            "Pukeashun",
            "Plinth",
            "Quadra",
            "Quartz",
            "Razerback",
            "Raleigh",
            "Sky Pilot",
            "Swannell",
            "Tatlow",
            "Thomlinson",
            "Unnecessary",
            "Upright",
            "Vista",
            "Vedder",
            "Whistler",
            "Washington",
            "Yeda",
            "Yellowhead",
            "Zoa"
    };

    private Button joinButton;
    private Switch audioSendCheckBox;
    private Switch audioReceiveCheckBox;
    private Switch videoSendCheckBox;
    private Switch videoReceiveCheckBox;
    private Switch screenShareCheckBox;
    private EditText sessionText;
    private EditText nameText;
    private App app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        setContentView(R.layout.activity_session_selector);

        nameText = (EditText)findViewById(R.id.nameText);
        sessionText = (EditText)findViewById(R.id.sessionText);
        joinButton = (Button)findViewById(R.id.joinButton);
        audioSendCheckBox = (Switch)findViewById(R.id.audioSendSwitch);
        audioReceiveCheckBox = (Switch)findViewById(R.id.audioReceiveSwitch);
        videoSendCheckBox = (Switch)findViewById(R.id.videoSendSwitch);
        videoReceiveCheckBox = (Switch)findViewById(R.id.videoReceiveSwitch);
        screenShareCheckBox = (Switch)findViewById(R.id.screenShareSwitch);

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            screenShareCheckBox.setEnabled(false);
        }

        try
        {
            try {
                InputStream is = getResources().openRawResource(R.raw.icelink);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder total = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    total.append(line).append('\n');
                }
                fm.icelink.License.setKey(total.toString());
                is.close();
            } catch (Exception ex) {
                alert("Invalid icelink key.");
            }

            app = App.getInstance(this);

            // Create a random 6 digit number for the new session ID.
            sessionText.setText(String.valueOf(new fm.icelink.Randomizer().next(100000, 999999)));
            sessionText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(6) });

            nameText.setText(Names[new fm.icelink.Randomizer().next(Names.length)]);
            nameText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(20) });

            joinButton.setOnClickListener(new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    switchToVideoChat(sessionText.getText().toString(), nameText.getText().toString());
                }
            });

            audioSendCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableAudioSend(b);
                }
            });

            audioReceiveCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableAudioReceive(b);
                }
            });

            videoReceiveCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableVideoReceive(b);
                }
            });

            videoSendCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableVideoSend(b);
                }
            });

            screenShareCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableScreenShare(b);
                }
            });

            ((TextView)findViewById(R.id.phoneText)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent tel = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:18883796686"));
                    startActivity(tel);
                }
            });

            ((TextView)findViewById(R.id.emailText)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("mailto:info@frozenmountain.com");
                    intent.setData(data);
                    startActivity(intent);
                }
            });

            ((ImageView)findViewById(R.id.facebookIcon)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("https://www.facebook.com/frozenmountain");
                    intent.setData(data);
                    startActivity(intent);
                }
            });

            ((ImageView)findViewById(R.id.twitterIcon)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("https://twitter.com/frozenmountain");
                    intent.setData(data);
                    startActivity(intent);
                }
            });

            ((ImageView)findViewById(R.id.linkedinIcon)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("https://www.linkedin.com/company/frozen-mountain-software");
                    intent.setData(data);
                    startActivity(intent);
                }
            });
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (data == null) {
             alert("Must allow screen sharing before the chat can start.");
            } else {
                MediaProjectionManager manager = (MediaProjectionManager) this.getSystemService(MEDIA_PROJECTION_SERVICE);
                app.setMediaProjection(manager.getMediaProjection(resultCode, data));
                startActivity(new Intent(getApplicationContext(), ChatActivity.class));
            }
        }
    }

    private void switchToVideoChat(String sessionId, String name)
    {
        if (sessionId.length() == 6)
        {
            if (name.length() > 0)
            {
                app.setSessionId(sessionId);
                app.setName(name);
                app.setEnableScreenShare(screenShareCheckBox.isChecked());

                if (screenShareCheckBox.isChecked() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        MediaProjectionManager manager = (MediaProjectionManager)this.getSystemService(MEDIA_PROJECTION_SERVICE);
                        Intent screenCaptureIntent = manager.createScreenCaptureIntent();

                        this.startActivityForResult(screenCaptureIntent, 42);
                } else {
                    // Show the video chat.
                    startActivity(new Intent(getApplicationContext(), ChatActivity.class));
                }
            }
            else
            {
                alert("Must have a name.");
            }
        }
        else
        {
            alert("Session ID must be 6 digits long.");
        }
    }

    public void alert(String format, Object... args)
    {
        final String text = String.format(format, args);
        final Activity self = this;
        self.runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (!isFinishing())
                {
                    AlertDialog.Builder alert = new AlertDialog.Builder(self);
                    alert.setMessage(text);
                    alert.setPositiveButton("OK", new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        { }
                    });
                    alert.show();
                }
            }
        });
    }
}
