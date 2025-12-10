package com.bumptech.glide.load.data;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import edu.ucr.cs.riple.annotator.util.Nullability;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.Nullable;

/**
 * An {@link OutputStream} implementation that recycles and re-uses {@code byte[]}s using the
 * provided {@link ArrayPool}.
 */
public final class BufferedOutputStream extends OutputStream {
  @NonNull private final OutputStream out;
  @Nullable private byte[] buffer;
  private ArrayPool arrayPool;
  private int index;

  public BufferedOutputStream(@NonNull OutputStream out, @NonNull ArrayPool arrayPool) {
    this(out, arrayPool, ArrayPool.STANDARD_BUFFER_SIZE_BYTES);
  }

  @VisibleForTesting
  BufferedOutputStream(@NonNull OutputStream out, ArrayPool arrayPool, int bufferSize) {
    this.out = out;
    this.arrayPool = arrayPool;
    buffer = arrayPool.get(bufferSize, byte[].class);
  }

  @Override
  public void write(int b) throws IOException {
    if (buffer == null) {
      throw new IOException("BufferedOutputStream is closed");
    }
    Nullability.castToNonnull(buffer)[index++] = (byte) b;
    maybeFlushBuffer();
  }

  @Override
  public void write(@NonNull byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(@NonNull byte[] b, int initialOffset, int length) throws IOException {
    int writtenSoFar = 0;
    do {
      int remainingToWrite = length - writtenSoFar;
      int currentOffset = initialOffset + writtenSoFar;
      // If we still need to write at least the buffer size worth of bytes, we might as well do so
      // directly and avoid the overhead of copying to the buffer first.
      if (buffer == null) {
        out.write(b, currentOffset, remainingToWrite);
        return;
      }
      if (index == 0 && remainingToWrite >= Nullability.castToNonnull(buffer).length) {
        out.write(b, currentOffset, remainingToWrite);
        return;
      }

      byte[] localBuffer = buffer;
      int remainingSpaceInBuffer = Nullability.castToNonnull(localBuffer).length - index;
      int totalBytesToWriteToBuffer = Math.min(remainingToWrite, remainingSpaceInBuffer);

      System.arraycopy(b, currentOffset, localBuffer, index, totalBytesToWriteToBuffer);

      index += totalBytesToWriteToBuffer;
      writtenSoFar += totalBytesToWriteToBuffer;

      maybeFlushBuffer();
    } while (writtenSoFar < length);
  }

  @Override
  public void flush() throws IOException {
    flushBuffer();
    out.flush();
  }

  private void flushBuffer() throws IOException {
    if (index > 0) {
      out.write(buffer, 0, index);
      index = 0;
    }
  }

  private void maybeFlushBuffer() throws IOException {
    if (buffer != null && index == buffer.length) {
      flushBuffer();
    }
  }

  @Override
  public void close() throws IOException {
    try {
      flush();
    } finally {
      out.close();
    }
    release();
  }

  private void release() {
    if (buffer != null) {
      arrayPool.put(buffer);
      buffer = null;
    }
  }
}
