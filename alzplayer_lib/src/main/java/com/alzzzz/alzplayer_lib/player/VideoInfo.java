package com.alzzzz.alzplayer_lib.player;

import android.net.Uri;

import java.util.Locale;

/**
 * Discription:
 * Created by sz on 16/7/6.
 */
public class VideoInfo {
    public String name;
    public String contentId;
    public String provider;
    public Uri uri;
    public int type;

    public VideoInfo(){
        name = "";
        provider = "";
    }

    public VideoInfo(String name, String uri, int type) {
        this(name, name.toLowerCase(Locale.US).replaceAll("\\s", ""), "", Uri.parse(uri), type);
    }

    public VideoInfo(String name, Uri uri, int type) {
        this(name, name.toLowerCase(Locale.US).replaceAll("\\s", ""), "", uri, type);
    }

    public VideoInfo(String name, String contentId, String provider, Uri uri, int type) {
        this.name = name;
        this.contentId = contentId;
        this.provider = provider;
        this.uri = uri;
        this.type = type;
    }
}
