package fm.icelink.chat.websync4;

import fm.icelink.AecPipe;
import fm.icelink.AudioConfig;
import fm.icelink.AudioSink;
import fm.icelink.android.AudioRecordSource;
import fm.icelink.android.AudioTrackSink;
import fm.icelink.audioprocessing.AecProcessor;

public class AecContext extends fm.icelink.AecContext {
    @Override
    protected AecPipe createProcessor() {
        AudioConfig config = new AudioConfig(48000, 2);
        return new AecProcessor(config, AudioTrackSink.getBufferDelay(config) + AudioRecordSource.getBufferDelay(config));
    }

    @Override
    protected AudioSink createOutputMixerSink(AudioConfig audioConfig) {
        return new AudioTrackSink(audioConfig);
    }
}
