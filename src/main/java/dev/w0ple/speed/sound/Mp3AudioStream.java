package dev.w0ple.speed.sound;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An mp3 decoded with JLayer into 16-bit native-order (little-endian) PCM, with the sample rate and channel count
 * of the file. The first frame is read in the constructor to learn the format.
 */
public final class Mp3AudioStream implements AudioStream {

    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;
    private short[] pending;
    private int pendingLength;
    private boolean ended;

    public Mp3AudioStream(InputStream in) throws IOException {
        this.bitstream = new Bitstream(in);
        try {
            if (!decodeNext()) throw new IOException("Not an mp3 file or no audio frames");
        } catch (RuntimeException | IOException e) {
            closeQuietly();
            throw e instanceof IOException io ? io : new IOException(e);
        }
        SampleBuffer first = lastBuffer;
        this.format = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
        this.pending = first.getBuffer();
        this.pendingLength = first.getBufferLength();
    }

    private SampleBuffer lastBuffer;

    /** Decodes the next frame into {@link #lastBuffer}; false at the end of the stream. */
    private boolean decodeNext() throws IOException {
        try {
            Header h = bitstream.readFrame();
            if (h == null) return false;
            lastBuffer = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            bitstream.closeFrame();
            return true;
        } catch (BitstreamException | DecoderException e) {
            throw new IOException("Cannot decode mp3", e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        // must be a direct buffer (like the vanilla ones): OpenAL reads its memory address
        ByteBuffer out = BufferUtils.createByteBuffer(Math.max(size, 2));
        int offset = 0;
        while (out.remaining() >= 2) {
            if (offset >= pendingLength) {
                if (ended || !decodeNext()) {
                    ended = true;
                    break;
                }
                pending = lastBuffer.getBuffer();
                pendingLength = lastBuffer.getBufferLength();
                offset = 0;
                if (pendingLength == 0) continue;
            }
            out.putShort(pending[offset++]);
        }
        // keep the rest of a frame that did not fit
        if (offset < pendingLength) {
            System.arraycopy(pending, offset, pending, 0, pendingLength - offset);
            pendingLength -= offset;
        } else {
            pendingLength = 0;
        }
        out.flip();
        return out;
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
    }

    private void closeQuietly() {
        try {
            bitstream.close();
        } catch (BitstreamException ignored) {
            // nothing to do
        }
    }
}
