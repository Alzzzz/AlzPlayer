package com.alzzzz.alzplayer_lib;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;

import com.alzzzz.alzplayer_lib.player.AlzzzzPlayer;
import com.alzzzz.alzplayer_lib.player.DashRendererBuilder;
import com.alzzzz.alzplayer_lib.player.ExtractorRendererBuilder;
import com.alzzzz.alzplayer_lib.player.HlsRendererBuilder;
import com.alzzzz.alzplayer_lib.player.SmoothStreamingRendererBuilder;
import com.alzzzz.alzplayer_lib.player.VideoInfo;
import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.metadata.id3.GeobFrame;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.PrivFrame;
import com.google.android.exoplayer.metadata.id3.TxxxFrame;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.Util;

import java.util.List;

/**
 * Discription:视频播放布局
 * Created by sz on 16/7/6.
 */
public class AlzVideoRootFrame extends FrameLayout implements SurfaceHolder.Callback, View.OnClickListener,
        AlzzzzPlayer.Listener, AlzzzzPlayer.CaptionListener, AlzzzzPlayer.Id3MetadataListener,
        AudioCapabilitiesReceiver.Listener{
    private static final String TAG = "AlzVideoRootFrame";
    // For use within demo app code.
    public static final String CONTENT_ID_EXTRA = "content_id";
    public static final String CONTENT_TYPE_EXTRA = "content_type";
    public static final String PROVIDER_EXTRA = "provider";

    // For use when launching the demo app using adb.
    private static final String CONTENT_EXT_EXTRA = "type";

    private Context context;
    private View rootView;
    private AspectRatioFrameLayout videoFrame;
    private SurfaceView surfaceView;
    private View shutterView;//黑幕
    private SubtitleLayout subtitleLayout;//字幕
    private MediaController controller;

    private AlzzzzPlayer player;//播放器核心
    private EventLogger eventLogger;
    private boolean playerNeedsPrepare;

    //外部传递
    private int contentType;//内容类型
    private Uri contentUri;//视频信息 intent.getData
    private String contentId;
    private String provider;

    private PlayerListener playerListener;
    private long playerPosition;
    private boolean enableBackgroundAudio;
    private boolean fullScreen;
    private UiChangeInterface uiChangeInterface;

    public AlzVideoRootFrame(Context context) {
        super(context);
        this.init(context);
    }

    public AlzVideoRootFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.init(context);
    }

    public AlzVideoRootFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.init(context);
    }

    public AlzVideoRootFrame(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.init(context);
    }

    private void init(Context context){
        this.context = context;
        LayoutInflater.from(context).inflate(R.layout.alz_video_root_view, this);
        rootView = this.findViewById(R.id.video_root);
        rootView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    toggleControlsVisibility();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            }
        });

        rootView.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                        || keyCode == KeyEvent.KEYCODE_MENU) {
                    return false;
                }
                return controller.dispatchKeyEvent(event);
            }
        });

        videoFrame = (AspectRatioFrameLayout) this.findViewById(R.id.video_frame);
        surfaceView = (SurfaceView) this.findViewById(R.id.surface_view);
        shutterView = this.findViewById(R.id.shutter);
        subtitleLayout = (SubtitleLayout) this.findViewById(R.id.subtitles);

        controller = new KeyCompatibleMediaController(context);
        controller.setAnchorView(this);
    }

    private void toggleControlsVisibility() {
        if (controller.isShowing()) {
            controller.hide();
        } else {
            showControls();
        }
    }

    private void showControls() {
        //controller订制，不显示
        controller.show(0);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (player != null) {
            player.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) {
            player.blockingClearSurface();
        }
    }

    @Override
    public void onCues(List<Cue> cues) {
        subtitleLayout.setCues(cues);
    }

    @Override
    public void onId3Metadata(List<Id3Frame> id3Frames) {
        for (Id3Frame id3Frame : id3Frames) {
            if (id3Frame instanceof TxxxFrame) {
                TxxxFrame txxxFrame = (TxxxFrame) id3Frame;
                Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s", txxxFrame.id,
                        txxxFrame.description, txxxFrame.value));
            } else if (id3Frame instanceof PrivFrame) {
                PrivFrame privFrame = (PrivFrame) id3Frame;
                Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s", privFrame.id, privFrame.owner));
            } else if (id3Frame instanceof GeobFrame) {
                GeobFrame geobFrame = (GeobFrame) id3Frame;
                Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, description=%s",
                        geobFrame.id, geobFrame.mimeType, geobFrame.filename, geobFrame.description));
            }
//      else if (id3Frame instanceof ApicFrame) {
//        ApicFrame apicFrame = (ApicFrame) id3Frame;
//        Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, description=%s",
//                apicFrame.id, apicFrame.mimeType, apicFrame.description));
//      } else if (id3Frame instanceof TextInformationFrame) {
//        TextInformationFrame textInformationFrame = (TextInformationFrame) id3Frame;
//        Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s", textInformationFrame.id,
//            textInformationFrame.description));
//      }
            else {
                Log.i(TAG, String.format("ID3 TimedMetadata %s", id3Frame.id));
            }
        }
    }

    public int getCurrentStatus() {
        if(this.player == null) {
            return 1;
        } else {
            int state = this.player.getPlaybackState();
            switch(state) {
                case 1:
                case 2:
                case 3:
                    return state;
                case 4:
                    if(this.player.getPlayerControl().isPlaying()) {
                        return 5;
                    }

                    return 4;
                case 5:
                    return 6;
                default:
                    return 1;
            }
        }
    }

    /**
     * 状态
     *
     * @param playWhenReady
     * @param playbackState
     */
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch(playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
                break;
            case ExoPlayer.STATE_PREPARING:
                text += "preparing";
                break;
            case ExoPlayer.STATE_READY:
                text += "ready";
                break;
            default:
                text += "unknown";
                break;
        }
        Log.d(TAG, text);
        if(this.playerListener != null) {
            if(playbackState < 4) {
                this.playerListener.onStateChanged(playbackState);
            } else if(playbackState == 4 && !playWhenReady) {
                this.playerListener.onStateChanged(playbackState);
            } else {
                this.playerListener.onStateChanged(playbackState + 1);
            }
        }
//        playerStateTextView.setText(text);
//        updateButtonVisibilities();
    }

    @Override
    public void onError(Exception e) {
        String errorString = null;
        if (e instanceof UnsupportedDrmException) {
            // Special case DRM failures.
            UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
            errorString = getContext().getString(Util.SDK_INT < 18 ? R.string.error_drm_not_supported
                    : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                    ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
        } else if (e instanceof ExoPlaybackException
                && e.getCause() instanceof MediaCodecTrackRenderer.DecoderInitializationException) {
            // Special case for decoder initialization failures.
            MediaCodecTrackRenderer.DecoderInitializationException decoderInitializationException =
                    (MediaCodecTrackRenderer.DecoderInitializationException) e.getCause();
            if (decoderInitializationException.decoderName == null) {
                if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                    errorString = getContext().getString(R.string.error_querying_decoders);
                } else if (decoderInitializationException.secureDecoderRequired) {
                    errorString = getContext().getString(R.string.error_no_secure_decoder,
                            decoderInitializationException.mimeType);
                } else {
                    errorString = getContext().getString(R.string.error_no_decoder,
                            decoderInitializationException.mimeType);
                }
            } else {
                errorString = getContext().getString(R.string.error_instantiating_decoder,
                        decoderInitializationException.decoderName);
            }
        }
        if (errorString != null) {
            Toast.makeText(getContext(), errorString, Toast.LENGTH_LONG).show();
        }
        if(this.playerListener != null) {
            this.playerListener.onError(e);
        }
        playerNeedsPrepare = true;
//        updateButtonVisibilities();
        showControls();
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        shutterView.setVisibility(View.GONE);
        videoFrame.setAspectRatio(height == 0 ? 1.0F : (float) width * pixelWidthHeightRatio / (float) height);
    }

    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        if (player == null) {
            return;
        }
        boolean backgrounded = player.getBackgrounded();
        boolean playWhenReady = player.getPlayWhenReady();
        releasePlayer();
        preparePlayer(playWhenReady);
        player.setBackgrounded(backgrounded);
    }

    @TargetApi(23)
    private boolean maybeRequestPermission() {
        if (requiresPermission(contentUri)) {
            ((Activity) context).requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            return true;
        } else {
            return false;
        }
    }

    @TargetApi(23)
    private boolean requiresPermission(Uri uri) {
        return Util.SDK_INT >= 23
                && Util.isLocalFileUri(uri)
                && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;
    }


    // Internal methods

    private AlzzzzPlayer.RendererBuilder getRendererBuilder() {
        String userAgent = Util.getUserAgent(getContext(), "ExoPlayerDemo");
        switch (contentType) {
            case Util.TYPE_SS:
                return new SmoothStreamingRendererBuilder(getContext(), userAgent, contentUri.toString(),
                        new SmoothStreamingTestMediaDrmCallback());
            case Util.TYPE_DASH:
                return new DashRendererBuilder(getContext(), userAgent, contentUri.toString(),
                        new WidevineTestMediaDrmCallback(contentId, provider));
            case Util.TYPE_HLS:
                return new HlsRendererBuilder(getContext(), userAgent, contentUri.toString());
            case Util.TYPE_OTHER:
                return new ExtractorRendererBuilder(getContext(), userAgent, contentUri);
            default:
                throw new IllegalStateException("Unsupported type: " + contentType);
        }
    }

    private void preparePlayer(boolean playWhenReady) {
        if (player == null) {
            player = new AlzzzzPlayer(getRendererBuilder());
            player.addListener(this);
            player.setCaptionListener(this);
            player.setMetadataListener(this);
            player.seekTo(playerPosition);
            playerNeedsPrepare = true;
            controller.setMediaPlayer(player.getPlayerControl());
            controller.setEnabled(true);
            eventLogger = new EventLogger();
            eventLogger.startSession();
            player.addListener(eventLogger);
            player.setInfoListener(eventLogger);
            player.setInternalErrorListener(eventLogger);
//            debugViewHelper = new DebugTextViewHelper(player, debugTextView);
//            debugViewHelper.start();
        }
        if (playerNeedsPrepare) {
            player.prepare();
            playerNeedsPrepare = false;
//            updateButtonVisibilities();
        }
        player.setSurface(surfaceView.getHolder().getSurface());
        player.setPlayWhenReady(playWhenReady);
    }

    private void releasePlayer() {
        if (player != null) {
//            debugViewHelper.stop();
//            debugViewHelper = null;
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
            eventLogger.endSession();
            eventLogger = null;
        }
    }

    /**
     * 位置
     * @param playerPosition 毫秒
     */
    public void seekTo(long playerPosition){
        if (player != null){
            player.seekTo(playerPosition);
        }
    }

    /**
     * 总时长 精确到秒
     * @return
     */
    public int getDuration() {
        return this.player == null?0:this.player.getPlayerControl().getDuration() / 1000;
    }

    public int getCurrentTime() {
        return this.player == null?0:this.player.getPlayerControl().getCurrentPosition() / 1000;
    }


    @Override
    public void onClick(View v) {

    }

    public void play() {
        if(this.player != null && !this.player.getPlayerControl().isPlaying()) {
            this.player.setPlayWhenReady(true);
        }

    }

    public void pause() {
        if(this.player != null && this.player.getPlayerControl().isPlaying()) {
            this.player.getPlayerControl().pause();
        }

    }

    public void setVideo(VideoInfo videoInfo){
        contentUri = videoInfo.uri;
        contentType = videoInfo.type;
        contentId = videoInfo.contentId;
        provider = videoInfo.provider;
        configureSubtitleView();
        if (player == null) {
            if (!maybeRequestPermission()) {
                preparePlayer(true);
            }
        } else {
            player.setBackgrounded(false);
        }
    }


    public void release() {
        if (!enableBackgroundAudio) {
            releasePlayer();
        } else {
            player.setBackgrounded(true);
        }
        shutterView.setVisibility(View.VISIBLE);
    }

    private void configureSubtitleView() {
        CaptionStyleCompat style;
        float fontScale;
        if (Util.SDK_INT >= 19) {
            style = getUserCaptionStyleV19();
            fontScale = getUserCaptionFontScaleV19();
        } else {
            style = CaptionStyleCompat.DEFAULT;
            fontScale = 1.0f;
        }
        subtitleLayout.setStyle(style);
        subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
    }

    @TargetApi(19)
    private float getUserCaptionFontScaleV19() {
        CaptioningManager captioningManager =
                (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
        return captioningManager.getFontScale();
    }

    @TargetApi(19)
    private CaptionStyleCompat getUserCaptionStyleV19() {
        CaptioningManager captioningManager =
                (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
        return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    public void setToggleFullScreenHandler(UiChangeInterface handler) {
        if(this.player != null) {
            this.uiChangeInterface = handler;
        }
    }

    public void toggleFullScreen(){
        if(this.player != null) {
            if(uiChangeInterface != null){
                uiChangeInterface.OnChange();
                fullScreen = !fullScreen;
            }
        }
    }

    private static final class KeyCompatibleMediaController extends MediaController {

        private MediaPlayerControl playerControl;

        public KeyCompatibleMediaController(Context context) {
            super(context);
        }

        @Override
        public void setMediaPlayer(MediaPlayerControl playerControl) {
            super.setMediaPlayer(playerControl);
            this.playerControl = playerControl;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            int keyCode = event.getKeyCode();
            if (playerControl.canSeekForward() && (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    playerControl.seekTo(playerControl.getCurrentPosition() + 15000); // milliseconds
                    show();
                }
                return true;
            } else if (playerControl.canSeekBackward() && (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    playerControl.seekTo(playerControl.getCurrentPosition() - 5000); // milliseconds
                    show();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }

    /**
     * Makes a best guess to infer the type from a media {@link Uri} and an optional overriding file
     * extension.
     *
     * @param uri The {@link Uri} of the media.
     * @param fileExtension An overriding file extension.
     * @return The inferred type.
     */
    private static int inferContentType(Uri uri, String fileExtension) {
        String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension
                : uri.getLastPathSegment();
        return Util.inferContentType(lastPathSegment);
    }

    public void setListener(PlayerListener listener){
        playerListener = listener;
    }

}
