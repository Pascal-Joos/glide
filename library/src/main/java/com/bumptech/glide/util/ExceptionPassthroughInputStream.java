package com.bumptech.glide.util;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import edu.ucr.cs.riple.annotator.util.Nullability;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;

/**
 * An {@link java.io.InputStream} that catches, stores and rethrows {@link java.io.IOException}s
 * during read and skip calls. This allows users of this API to handle the exception at a higher
 * level if the exception is swallowed by some intermediate library. This class is a workaround for
 * a framework issue where exceptions during reads while decoding bitmaps in {@link
 * android.graphics.BitmapFactory} can return partially decoded bitmaps.
 *
 * <p>Unlike the deprecated {@link ExceptionCatchingInputStream}, this class will both store and
 * re-throw any IOExceptions. Rethrowing works around bugs in wrapping streams that may not fully
 * obey the stream contract. This is really only useful if some middle layer is going to catch the
 * exception (like BitmapFactory) but we want to propagate the exception instead.
 *
 * <p>See https://github.com/bumptech/glide/issues/126 and #4438.
 */
public final class ExceptionPassthroughInputStream extends InputStream {

  @GuardedBy("POOL")
  private static final Queue<ExceptionPassthroughInputStream> POOL = Util.createQueue(0);

  @Nullable private InputStream wrapped;
  @Nullable private IOException exception;

  @NonNull
  public static ExceptionPassthroughInputStream obtain(@NonNull InputStream toWrap) {
    ExceptionPassthroughInputStream result;
    synchronized (POOL) {
      result = POOL.poll();
    }
    if (result == null) {
      result = new ExceptionPassthroughInputStream();
    }
    result.setInputStream(toWrap);
    return result;
  }

  // Exposed for testing.
  static void clearQueue() {
    synchronized (POOL) {
      while (!POOL.isEmpty()) {
        POOL.remove();
      }
    }
  }

  ExceptionPassthroughInputStream() {
    // Do nothing.
  }

  void setInputStream(@NonNull InputStream toWrap) {
    wrapped = toWrap;
  }

  @Override
  public int available() throws IOException {
    return wrapped != null ? Nullability.castToNonnull(wrapped).available() : 0;
  }

  @Override
  public void close() throws IOException {
    if (wrapped != null) {
      Nullability.castToNonnull(wrapped).close();
    }
  }

  @Override
  public void mark(int readLimit) {
    if (wrapped != null) {
      Nullability.castToNonnull(wrapped).mark(readLimit);
    }
  }

  @Override
  public boolean markSupported() {
    return Nullability.castToNonnull(wrapped).markSupported();
  }

  @Override
  public int read() throws IOException {
    InputStream localWrapped = wrapped;
    if (localWrapped == null) {
      throw new IOException("Stream closed");
    }
    try {
      return localWrapped.read();
    } catch (IOException e) {
      exception = e;
      throw e;
    }
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    if (wrapped == null) {
      throw new IOException("Stream is closed");
    }
    try {
      return wrapped.read(buffer);
    } catch (IOException e) {
      exception = e;
      throw e;
    }
  }

  @Override
  public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
    try {
      return getWrapped().read(buffer, byteOffset, byteCount);
    } catch (IOException e) {
      exception = e;
      throw e;
    }
  }

  @Override
  public synchronized void reset() throws IOException {
    if (wrapped != null) {
      wrapped.reset();
    }
  }

  @Override
  public long skip(long byteCount) throws IOException {
    if (wrapped == null) {
      throw new IOException("Stream is closed");
    }
    try {
      return wrapped.skip(byteCount);
    } catch (IOException e) {
      exception = e;
      throw e;
    }
  }

  @Nullable
  public IOException getException() {
    return exception;
  }

  public void release() {
    exception = null;
    wrapped = null;
    synchronized (POOL) {
      POOL.offer(this);
    }
  }
}
