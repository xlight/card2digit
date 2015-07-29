package com.example.card2digit;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build.VERSION;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

public class CameraPreview extends FrameLayout implements
    SurfaceHolder.Callback, PreviewCallback {

  static {
    System.loadLibrary("card2digit");
  }

  public static float left = 28f / 85.6f;
  public static float right = 76f / 85.6f;
  public static float top = 44f / 54f;
  public static float bottom = 49f / 54f;

  private SurfaceView mSurfaceView;
  private SurfaceHolder mHolder;
  private Size mPreviewSize;
  private List<Size> mSupportedPreviewSizes;
  private Camera mCamera;

  public CameraPreview(Context context) {
    super(context);
    init();
  }

  public CameraPreview(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    mSurfaceView = new SurfaceView(getContext());
    addView(mSurfaceView);
    BorderView borderView = new BorderView(getContext());
    addView(borderView);

    mHolder = mSurfaceView.getHolder();
    mHolder.addCallback(this);
    mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }

  public void setCamera(Camera camera) {
    mCamera = camera;
    if (mCamera != null) {
      mSupportedPreviewSizes = mCamera.getParameters()
          .getSupportedPreviewSizes();
      requestLayout();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height = resolveSize(getSuggestedMinimumHeight(),
        heightMeasureSpec);
    setMeasuredDimension(width, height);
    if (mSupportedPreviewSizes != null) {
      mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, height,
          width);
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (changed && getChildCount() > 0) {

      final View child = getChildAt(0);

      final int width = r - l;
      final int height = b - t;

      int previewWidth = width;
      int previewHeight = height;

      getChildAt(1).layout(0, 0, width, height);

      if (mPreviewSize != null) {
        previewWidth = mPreviewSize.height;
        previewHeight = mPreviewSize.width;
      }

      if (width * previewHeight < height * previewWidth) {
        final int scaledChildWidth = previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0,
            (width + scaledChildWidth) / 2, height);
      } else {
        final int scaledChildHeight = previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2, width,
            (height + scaledChildHeight) / 2);
      }
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    try {
      if (mCamera != null) {
        mCamera.setPreviewDisplay(holder);
        mCamera.setPreviewCallback(this);
      }
    } catch (IOException exception) {
      Log.e("xxx", "IOException caused by setPreviewDisplay()", exception);
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    if (mCamera != null) {
      mCamera.stopPreview();
    }
  }

  private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) w / h;
    if (sizes == null) {
      return null;
    }

    Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;

    int targetHeight = h;

    // Try to find an size match aspect ratio and size
    for (Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
        continue;
      }
      if (Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    // Cannot find the one match the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for (Size size : sizes) {
        if (Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }
    return optimalSize;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    Camera.Parameters parameters = mCamera.getParameters();
    parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
    parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    // parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
    requestLayout();
    if (VERSION.SDK_INT >= 8) {
      mCamera.setDisplayOrientation(90);
    } else {
      parameters.set("rotation", 90);
    }
    mCamera.setParameters(parameters);
    mCamera.startPreview();
  }

  @Override
  public void onPreviewFrame(byte[] data, Camera camera) {
    int width = mPreviewSize.width;
    int height = mPreviewSize.height;

    byte[] rotated = MatrixUtils.rotate(data, width, height);

    width = height;
    height = mPreviewSize.width;

    int l = Math.round((left * BorderView.WIDTH * 2 + mSurfaceView.getWidth()
        / 2 - BorderView.WIDTH)
        / mSurfaceView.getWidth() * width);
    int r = Math.round((right * BorderView.WIDTH * 2 + mSurfaceView.getWidth()
        / 2 - BorderView.WIDTH)
        / mSurfaceView.getWidth() * width);
    int t = Math.round(top * BorderView.HEIGHT * 2) + 72;
    int b = Math.round(bottom * BorderView.HEIGHT * 2) + 72;

    int[] pixels = MatrixUtils.crop(rotated, width, l, r, t, b);

    Bitmap bm = Bitmap.createBitmap(pixels, r - l, b - t,
        Bitmap.Config.ARGB_8888);
    ImageView iv = new ImageView(getContext());
    iv.setImageBitmap(bm);
    Toast toast = new Toast(getContext());
    toast.setView(iv);
    toast.setDuration(Toast.LENGTH_LONG);
    // toast.show();

    String result = ocr(rotated, width, height, l, r, t, b);
    if (result != null && result.length() == 18) {
      if (result.equals(candidate)) {
        Intent intent = new Intent(getContext(), ResultActivity.class);
        intent.putExtra("text", result);

        l = Math.round((mSurfaceView.getWidth() / 2 - BorderView.WIDTH)
            / mSurfaceView.getWidth() * width);
        r = Math
            .round((BorderView.WIDTH * 2 + mSurfaceView.getWidth() / 2 - BorderView.WIDTH)
                / mSurfaceView.getWidth() * width);
        t = 72;
        b = Math.round(BorderView.HEIGHT * 2) + 72;
        intent.putExtra("bitmap", Bitmap.createBitmap(
            MatrixUtils.crop(rotated, width, l, r, t, b), r - l, b - t,
            Bitmap.Config.ARGB_8888));
        getContext().startActivity(intent);
      } else {
        candidate = result;
      }
    } else {
      candidate = null;
    }
  }

  private String candidate;

  private native String ocr(byte[] data, int width, int height, int l, int r,
      int t, int b);

}
