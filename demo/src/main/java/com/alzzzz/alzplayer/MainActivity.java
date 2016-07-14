package com.alzzzz.alzplayer;

import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.alzzzz.alzplayer_lib.AlzVideoRootFrame;
import com.alzzzz.alzplayer_lib.player.VideoInfo;
import com.google.android.exoplayer.util.Util;

public class MainActivity extends AppCompatActivity {
    AlzVideoRootFrame mAlzPlayer;
    VideoInfo mVideoInfo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mAlzPlayer = (AlzVideoRootFrame) findViewById(R.id.alz_video_layout);
        setupPlayer();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    private void setupPlayer() {
        mVideoInfo = new VideoInfo();
        mVideoInfo.uri = Uri.parse("https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/"
                + "bipbop_4x3_variant.m3u8");
        mVideoInfo.contentId = "";
        mVideoInfo.name = "test HLS";//由于苹果审核大于30分钟的视频都要是HLS的
        mVideoInfo.type = Util.TYPE_HLS;
//        mVideoInfo.provider = "widevine_test";

        mAlzPlayer.setVideo(mVideoInfo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAlzPlayer != null){
            mAlzPlayer.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAlzPlayer != null && mVideoInfo != null){
            mAlzPlayer.setVideo(mVideoInfo);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
