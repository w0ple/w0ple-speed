package dev.w0ple.speed.sound;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The vanilla engine cannot seek, so this wraps the decoded (ogg or mp3) stream and throws the PCM before
 * the offset away on the first reads (done lazily, on the sound thread).
 */
public final class SeekingStream implements AudioStream {

    private final AudioStream in;
    private long skipBytes;

    public SeekingStream(AudioStream in, double offsetSeconds) {
        this.in = in;
        AudioFormat f = in.getFormat();
        int frame = Math.max(1, f.getFrameSize());
        long frames = (long) (Math.max(0, offsetSeconds) * f.getFrameRate());
        this.skipBytes = frames * frame;
    }

    @Override
    public AudioFormat getFormat() {
        return in.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        while (skipBytes > 0) {
            ByteBuffer b = in.read((int) Math.min(skipBytes, 1 << 16));
            if (b == null) return ByteBuffer.allocate(0);
            if (!b.hasRemaining()) return b;
            if (b.remaining() <= skipBytes) {
                skipBytes -= b.remaining();
                continue;
            }
            b.position(b.position() + (int) skipBytes);
            skipBytes = 0;
            return b;
        }
        return in.read(size);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
