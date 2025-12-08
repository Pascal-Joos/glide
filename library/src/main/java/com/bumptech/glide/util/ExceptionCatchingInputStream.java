package com.bumptech.glide.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;

/**
 * An {@link java.io.InputStream} that catches {@link java.io.IOException}s during read and skip
 * calls and stores them so they can later be handled or thrown. This class is a workaround for a
 * framework issue where exceptions during reads while decoding bitmaps in {@link
 * android.graphics.BitmapFactory} can return partially decoded bitmaps.
 *
 * <p>See https://github.com/bumptech/glide/issues/126.
 *
 * @deprecated In some cases, callers may not handle getting 0 or -1 return values from methods,
 *     which can lead to infinite loops (see #4438). Use {@link ExceptionPassthroughInputStream}
 *     instead. This class will be deleted in a future version of Glide.
 */
@Deprecated
public class ExceptionCatchingInputStream extends InputStream {

  private static final Queue<ExceptionCatchingInputStream> QUEUE = Util.createQueue(0);

  @Nullable private InputStream wrapped;
  @Nullable private IOException exception;

  @NonNull
  public static ExceptionCatchingInputStream obtain(@NonNull InputStream toWrap) {
    ExceptionCatchingInputStream result;
    synchronized (QUEUE) {
      result = QUEUE.poll();
    }
    if (result == null) {
      result = new ExceptionCatchingInputStream();
    }
    result.setInputStream(toWrap);
    return result;
  }

  // Exposed for testing.
  static void clearQueue() {
    while (!QUEUE.isEmpty()) {
      QUEUE.remove();
    }
  }

  ExceptionCatchingInputStream() {
    // Explicitly initialize wrapped to make NullAway aware this field is set in the constructor.
    wrapped = null;
  }

  void setInputStream(@NonNull InputStream toWrap) {
    wrapped = toWrap;
  }

  @Override
  public int available() throws IOException {
    if (wrapped == null) {
      throw new IllegalStateException("Stream not initialized");
    }
    return wrapped.available();
  }

  @Override
  public void close() throws IOException {
    if (wrapped == null) {
      throw new IllegalStateException("Stream not initialized");
    }
    wrapped.close();
  }

  @Override
  public void mark(int readLimit) {
    if (wrapped == null) {
      throw new IllegalStateException("Stream not initialized");
    }
    wrapped.mark(readLimit);
  }

  @Override
  public boolean markSupported() {
    if (wrapped == null) {
      throw new IllegalStateException("Stream not initialized");
    }
    return wrapped.markSupported();
  }

  @Override
  public int read(byte[] buffer) {
    int read;
    try {
      if (wrapped == null) {
        throw new IllegalStateException("Stream not initialized");
      }
      read = wrapped.read(buffer);
    } catch (IOException e) {
      exception = e;
      read = -1;
    }
    return read;
  }

  @Override
  public int read(byte[] buffer, int byteOffset, int byteCount) {
    int read;
    try {
      if (wrapped == null) {
        throw new IllegalStateException("Stream not initialized");
      }
      read = wrapped.read(buffer, byteOffset, byteCount);
    } catch (IOException e) {
      exception = e;
      read = -1;
    }
    return read;
  }

  @Override
  public synchronized void reset() throws IOException {
    if (wrapped == null) {
      throw new IllegalStateException("Stream not initialized");
    }
    wrapped.reset();
  }

  @Override
  public long skip(long byteCount) {
    long skipped;
    try {
      if (wrapped == null) {
        throw new IllegalStateException("Stream not initialized");
      }
      skipped = wrapped.skip(byteCount);
    } catch (IOException e) {
      exception = e;
      skipped = 0;
    }
    return skipped;
  }

  @Override
  public int read() {
    int result;
    try {
      if (wrapped == null) {
        throw new IllegalStateException("Stream not initialized");
      }
      result = wrapped.read();
    } catch (IOException e) {
      exception = e;
      result = -1;
    }
    return result;
  }

  @Nullable
  public IOException getException() {
    return exception;
  }

  public void release() {
    exception = null;
    wrapped = null;
    synchronized (QUEUE) {
      QUEUE.offer(this);
    }
  }
}
