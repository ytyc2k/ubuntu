package fm.icelink.chat.websync4;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Camera;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import fm.icelink.*;
import fm.icelink.android.*;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

public class CameraLocalMedia extends LocalMedia<View> {
    private CameraPreview viewSink;
    private VideoConfig videoConfig = new VideoConfig(640, 480, 30);

    @Override
    protected ViewSink<View> createViewSink() {
        return null;
    }

    @Override
    protected VideoSource createVideoSource() {
        return new CameraSource(viewSink, videoConfig);
    }

    public CameraLocalMedia(Context context, boolean enableSoftwareH264, boolean disableAudio, boolean disableVideo, AecContext aecContext) {
        super(context, enableSoftwareH264, disableAudio, disableVideo, aecContext);
        this.context = context;

        viewSink = new CameraPreview(context, LayoutScale.Contain);

        super.initialize();
    }

    public View getView()
    {
        return viewSink.getView();
    }
}