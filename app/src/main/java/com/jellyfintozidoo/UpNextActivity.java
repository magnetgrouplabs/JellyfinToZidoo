package com.jellyfintozidoo;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import jp.wasabeef.glide.transformations.BlurTransformation;

/**
 * Up Next countdown screen for episode auto-advance.
 * Shows blurred series backdrop with episode info card and 10-second countdown.
 * D-pad navigable: Play Now (default focus) and Cancel buttons.
 *
 * Launch with intent extras:
 *   seriesName, episodeName, seasonNumber, episodeNumber,
 *   nextItemId, serverPath, seriesId, backdropUrl, serverUrl, accessToken
 *
 * Returns RESULT_OK with extras (nextItemId, serverPath, seriesId, episodeName,
 * seasonNumber, episodeNumber) when countdown finishes or Play Now is pressed.
 * Returns RESULT_CANCELED when Cancel is pressed.
 */
public class UpNextActivity extends AppCompatActivity {

    private CountDownTimer countDownTimer;
    private boolean cancelled = false;

    // Intent extra keys
    private String nextItemId;
    private String serverPath;
    private String seriesId;
    private String episodeName;
    private int seasonNumber;
    private int episodeNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_up_next);

        // Read intent extras
        Intent intent = getIntent();
        String seriesName = intent.getStringExtra("seriesName");
        episodeName = intent.getStringExtra("episodeName");
        seasonNumber = intent.getIntExtra("seasonNumber", 0);
        episodeNumber = intent.getIntExtra("episodeNumber", 0);
        nextItemId = intent.getStringExtra("nextItemId");
        serverPath = intent.getStringExtra("serverPath");
        seriesId = intent.getStringExtra("seriesId");
        String backdropUrl = intent.getStringExtra("backdropUrl");
        String serverUrl = intent.getStringExtra("serverUrl");
        String accessToken = intent.getStringExtra("accessToken");

        // Bind views
        ImageView backdropImageView = findViewById(R.id.backdrop);
        ImageView episodeThumbnail = findViewById(R.id.episodeThumbnail);
        TextView seriesNameView = findViewById(R.id.seriesName);
        TextView episodeInfoView = findViewById(R.id.episodeInfo);
        TextView countdownView = findViewById(R.id.countdown);
        Button btnPlayNow = findViewById(R.id.btnPlayNow);
        Button btnCancel = findViewById(R.id.btnCancel);

        // Set text
        seriesNameView.setText(seriesName != null ? seriesName : "");
        String episodeInfoText = String.format("S%02dE%02d - %s",
                seasonNumber, episodeNumber, episodeName != null ? episodeName : "");
        episodeInfoView.setText(episodeInfoText);

        // Load blurred backdrop (memory-constrained for Zidoo Z9X Pro)
        if (backdropUrl != null && !backdropUrl.isEmpty()) {
            String url = backdropUrl + "?maxWidth=1280";
            Glide.with(this)
                    .load(url)
                    .apply(new RequestOptions()
                            .override(1280, 720)
                            .transform(new BlurTransformation(25, 3))
                            .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(backdropImageView);
        }

        // Load episode thumbnail (primary image)
        if (serverUrl != null && nextItemId != null) {
            String thumbnailUrl = serverUrl + "/Items/" + nextItemId + "/Images/Primary?maxWidth=640";
            Glide.with(this)
                    .load(thumbnailUrl)
                    .apply(new RequestOptions()
                            .override(640, 360)
                            .transform(new RoundedCorners(16))
                            .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(episodeThumbnail);
        }

        // Start 10-second countdown
        countdownView.setText("Playing in 10");
        countDownTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownView.setText("Playing in " + (millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                if (!cancelled) {
                    launchNextEpisode();
                }
            }
        }.start();

        // Play Now — skip countdown
        btnPlayNow.setOnClickListener(v -> {
            cancelled = true;
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            launchNextEpisode();
        });

        // Cancel — return to calling activity
        btnCancel.setOnClickListener(v -> {
            cancelled = true;
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            setResult(RESULT_CANCELED);
            finish();
        });

        // Default focus on Play Now for D-pad navigation
        btnPlayNow.requestFocus();
        Log.d("UpNext", "Screen displayed: " + seriesName + " S" + seasonNumber + "E" + episodeNumber);
    }

    /**
     * Sets result with next episode metadata and finishes.
     */
    private void launchNextEpisode() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("nextItemId", nextItemId);
        resultIntent.putExtra("serverPath", serverPath);
        resultIntent.putExtra("seriesId", seriesId);
        resultIntent.putExtra("episodeName", episodeName);
        resultIntent.putExtra("seasonNumber", seasonNumber);
        resultIntent.putExtra("episodeNumber", episodeNumber);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Prevent zombie countdown launches
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}
