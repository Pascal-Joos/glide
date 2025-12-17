package com.bumptech.glide.load.engine.bitmap_recycle;

import javax.annotation.Nullable;

/** Adapter for handling primitive byte arrays. */
@SuppressWarnings("PMD.UseVarargs")
public final class ByteArrayAdapter implements ArrayAdapterInterface<byte[]> {
  private static final String TAG = "ByteArrayPool";

  @Override
  public String getTag() {
    return TAG;
  }

  @Override
  public int getArrayLength(@Nullable byte[] array) {
    if (array == null) {
      return 0;
    }
    return array.length;
  }

  @Override
  public byte[] newArray(int length) {
    return new byte[length];
  }

  @Override
  public int getElementSizeInBytes() {
    return 1;
  }
}
