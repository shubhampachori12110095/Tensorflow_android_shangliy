/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.demo.env.Logger;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraConnectionFragment extends Fragment {
  private static final Logger LOGGER = new Logger();

  /**
   * The camera preview size will be chosen to be the smallest frame by pixel size capable of
   * containing a DESIRED_SIZE x DESIRED_SIZE square.
   */
  private static final int MINIMUM_PREVIEW_SIZE = 320;
  private static final String LOG_TAG = CameraConnectionFragment.class.getSimpleName();


  private RecognitionScoreView scoreView;


  /**
   * Conversion from screen rotation to JPEG orientation.
   */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
  private static final String FRAGMENT_DIALOG = "dialog";
  private Button takePictureButton;

  private Handler myBackgroundHandler;
  private HandlerThread myBackgroundThread;


  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  //ImageLoader imageLoader=new ImageLoader();


  /**
   * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
   * {@link TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
          new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(
                    final SurfaceTexture texture, final int width, final int height) {
              openCamera(width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    final SurfaceTexture texture, final int width, final int height) {
              configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
            }
          };

  /**
   * ID of the current {@link CameraDevice}.
   */
  private String cameraId;

  /**
   * An {@link AutoFitTextureView} for camera preview.
   */
  private AutoFitTextureView textureView;
  //private AutoFitTextureView boxView;

  /**
   * A {@link CameraCaptureSession } for camera preview.
   */
  private CameraCaptureSession captureSession;

  /**
   * A reference to the opened {@link CameraDevice}.
   */
  private CameraDevice cameraDevice;

  /**
   * The rotation in degrees of the camera sensor from the display. 
   */
  private Integer sensorOrientation;

  /**
   * The {@link android.util.Size} of camera preview.
   */
  private Size previewSize;

  /**
   * {@link android.hardware.camera2.CameraDevice.StateCallback}
   * is called when {@link CameraDevice} changes its state.
   */
  private final CameraDevice.StateCallback stateCallback =
          new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice cd) {
              // This method is called when the camera is opened.  We start camera preview here.
              cameraOpenCloseLock.release();
              cameraDevice = cd;
              createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(final CameraDevice cd) {
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
            }

            @Override
            public void onError(final CameraDevice cd, final int error) {
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
              final Activity activity = getActivity();
              if (null != activity) {
                activity.finish();
              }
            }
          };

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread backgroundThread;

  /**
   * A {@link Handler} for running tasks in the background.
   */
  private Handler backgroundHandler;

  /**
   * An additional thread for running inference so as not to block the camera.
   */
  private HandlerThread inferenceThread;

  /**
   * A {@link Handler} for running tasks in the background.
   */
  private Handler inferenceHandler;

  /**
   * An {@link ImageReader} that handles preview frame capture.
   */
  private ImageReader previewReader;

  /**
   * {@link android.hardware.camera2.CaptureRequest.Builder} for the camera preview
   */
  private CaptureRequest.Builder previewRequestBuilder;

  /**
   * {@link CaptureRequest} generated by {@link #previewRequestBuilder}
   */
  private CaptureRequest previewRequest;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private final Semaphore cameraOpenCloseLock = new Semaphore(1);

  /**
   * Shows a {@link Toast} on the UI thread.
   *
   * @param text The message to show
   */
  private void showToast(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
              });
    }
  }


  /**
   * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
   * width and height are at least as large as the respective requested values, and whose aspect
   * ratio matches with the specified value.
   *
   * @param choices     The list of sizes that the camera supports for the intended output class
   * @param width       The minimum desired width
   * @param height      The minimum desired height
   * @param aspectRatio The aspect ratio
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  private static Size chooseOptimalSize(
          final Size[] choices, final int width, final int height, final Size aspectRatio) {
    // Collect the supported resolutions that are at least as big as the preview Surface
    final List<Size> bigEnough = new ArrayList<Size>();
    for (final Size option : choices) {
      if (option.getHeight() >= MINIMUM_PREVIEW_SIZE && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
        LOGGER.i("Adding size: " + option.getWidth() + "x" + option.getHeight());
        bigEnough.add(option);
      } else {
        LOGGER.i("Not adding size: " + option.getWidth() + "x" + option.getHeight());
      }
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
      return chosenSize;
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  public static CameraConnectionFragment newInstance() {
    return new CameraConnectionFragment();
  }

  @Override
  public View onCreateView(
          final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(R.layout.camera_connection_fragment, container, false);
  }


  private SurfaceView boxView;
  private SurfaceView cropImageView;
  WebView[] web = new WebView[4];
  String img_url = "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";
  boolean show_imge = false;
  String show_list = "";

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    scoreView = (RecognitionScoreView) view.findViewById(R.id.results);
    cropImageView = (SurfaceView) view.findViewById(R.id.cropImage);
    boxView = (SurfaceView) view.findViewById(R.id.box);
    web[0] = (WebView) view.findViewById(R.id.similar_img_1);
    web[1] = (WebView) view.findViewById(R.id.similar_img_2);
    web[2] = (WebView) view.findViewById(R.id.similar_img_3);
    web[3] = (WebView) view.findViewById(R.id.similar_img_4);

    boxView.setZOrderOnTop(true);    // necessary
    SurfaceHolder sfhTrackHolder = boxView.getHolder();
    sfhTrackHolder.setFormat(PixelFormat.TRANSPARENT);
    cropImageView.setZOrderOnTop(true);    // necessary
    SurfaceHolder crTrackHolder = cropImageView.getHolder();
    crTrackHolder.setFormat(PixelFormat.TRANSPARENT);

    // Button usage
    takePictureButton = (Button) view.findViewById(R.id.btnUpload);
    assert takePictureButton != null;
    takePictureButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
          Log.e(LOG_TAG, "Uplodad button was clicked");
          takePicturenet feedTask = new takePicturenet();
          feedTask.execute();
      }
    });


  }

  public void webviewimag() {
    if (show_list != null) {

      final String[] tokens = show_list.split(" ");
      show_list = "";
      int width = 200;
      int height = 200;
      String data = "";
      for (int i = 0; i < Math.min(tokens.length, 4); i++)

      {
        web[i].getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        data = "<img src=" + tokens[i] + " style='width:" + width + "px;height:" + height + "px' />";
        web[i].loadData(data, "text/html", "utf-8");
      }


    } else {
      for (int i = 0; i < 4; i++)

      {
        web[i].loadUrl("about:blank");
      }
    }
    onPause();

  }

  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  /**
   * Sets up member variables related to camera.
   *
   * @param width  The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  private void setUpCameraOutputs(final int width, final int height) {
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // For still image captures, we use the largest available size.
        final Size largest =
                Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                        new CompareSizesByArea());

        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        previewSize =
                chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        final int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
        } else {
          textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        }

        CameraConnectionFragment.this.cameraId = cameraId;
        return;
      }
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_error))
              .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  private Uri picUri;
  protected SurfaceHolder crop;
  protected SurfaceHolder box_sh;
  boolean isPlaying = true;
  boolean isUploading = true;
  RectF corped_location;


  private class takePicturenet extends AsyncTask<Void, Void, Void> {

    @Override
    protected void onPostExecute(Void result) {
      //Task you want to do on UIThread after completing Network operation
      //onPostExecute is called after doInBackground finishes its task.
      if (isPlaying) {
        if (tfPreviewListener.flag) {

          isUploading = true;
          webviewimag();


          box_sh = boxView.getHolder();

          Canvas canvas_box = box_sh.lockCanvas();
          canvas_box.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
          box_sh.unlockCanvasAndPost(canvas_box);

          crop = cropImageView.getHolder();
          Canvas canvas = crop.lockCanvas();
          canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
          Bitmap bitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
          Paint paint = new Paint();
          paint.setColor(0xcc000000);

          Paint transparentPaint = new Paint();
          transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
          transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
          canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
          canvas.drawRect(corped_location, transparentPaint);
          Paint p = new Paint();
          canvas.drawBitmap(bitmap, 0, 0, p);
          crop.unlockCanvasAndPost(canvas);
        }
        show_imge = false;
        isPlaying = !isPlaying;


      } else {
        for (int i = 0; i < 4; i++)

        {
          web[i].loadUrl("about:blank");
        }
        crop = cropImageView.getHolder();
        Canvas canvas = crop.lockCanvas();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        crop.unlockCanvasAndPost(canvas);
        isPlaying = !isPlaying;
        onResume();
      }


    }

    @Override
    protected Void doInBackground(Void... params) {
      //Do your network operation here

      if (isUploading) {
        try {
          corped_location = tfPreviewListener.location;
          tfPreviewListener.cropped_cor = new RectF(corped_location.left * 299 / 1440, corped_location.top * 299 / 1920, corped_location.right * 299 / 1440, corped_location.bottom * 299 / 1920);

          tfPreviewListener.usingcropped_flag = true;


          URL url = new URL("http://52.53.177.193:3000/searchapi");
          HttpURLConnection conn = (HttpURLConnection) url.openConnection();
          conn.setDoOutput(true);
          conn.setRequestMethod("POST");
          conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

          if (isPlaying) {
            while (!tfPreviewListener.featureset()) {
            }
          }
         // String feature = tfPreviewListener.getFeatures();
          String class_ids = tfPreviewListener.getclass();
          tfPreviewListener.usingcropped_flag = false;
          closeCamera();


          StringBuilder sb = new StringBuilder();
          sb.append("img64").append("=").append(URLEncoder.encode(img_url, "utf-8"));
          /*sb.append("feature").append("=").append(URLEncoder.encode(feature, "utf-8"));
          sb.append("&");
          sb.append("prediction").append("=").append(URLEncoder.encode(class_ids, "utf-8"));*/

         // System.out.println(feature);
          System.out.println(class_ids);
        //  JSONObject jsonObject = new JSONObject();
          byte[] entity = sb.toString().getBytes();

         /* try {
            jsonObject.put("feature", feature);
            jsonObject.put("prediction", class_ids);
          } catch (JSONException e) {
          }*/
         // String input = jsonObject.toString();
          OutputStream os = conn.getOutputStream();
          //os.write(input.getBytes());
          os.write(entity);
          os.flush();

          //if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
          //  throw new RuntimeException("Failed : HTTP error code : "
          //          + conn.getResponseCode());
          //}

          BufferedReader br = new BufferedReader(new InputStreamReader(
                  (conn.getInputStream())));

          String output = "";
          String results = "";
          show_list = "";

          System.out.println("Output from Server .... \n");


          while ((output = br.readLine()) != null) {

            //System.out.println(output);
            results = results + output;

          }

          Log.e(LOG_TAG, "Json Result :"+results);
          try {

            JSONObject obj = new JSONObject(results);


            try {

              JSONArray jasondata = obj.getJSONArray("data");
              String[] arr = new String[jasondata.length()];

              for (int i = 0; i < jasondata.length(); i++) {
                JSONObject obj_each = jasondata.optJSONObject(i);
                JSONArray image_list = obj_each.getJSONArray("reference_image_links");

                arr[i] = image_list.getString(0);
                show_list = show_list + arr[i] + " ";


                Log.d("My App", arr[i]);
              }


            } catch (JSONException e) {
            }

          } catch (Throwable t) {
            Log.e("My App", "Could not parse malformed JSON: \"" + results + "\"");
          }

          //imageLoader.displayImage(imageUrls[position], holder.image, null);


          conn.disconnect();
          //isUploading = false;
          //isPlaying = !isPlaying;
          tfPreviewListener.usingcropped_flag = false;


        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (ProtocolException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      return null;
    }
  }

  public void takePicture() {


    if (isPlaying) {

      box_sh = boxView.getHolder();

      Canvas canvas_box = box_sh.lockCanvas();
      canvas_box.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      box_sh.unlockCanvasAndPost(canvas_box);

      crop = cropImageView.getHolder();
      Canvas canvas = crop.lockCanvas();
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      Bitmap bitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
      Paint paint = new Paint();
      paint.setColor(0xcc000000);

      Paint transparentPaint = new Paint();
      transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
      transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
      canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
      canvas.drawRect(tfPreviewListener.location, transparentPaint);
      Paint p = new Paint();
      canvas.drawBitmap(bitmap, 0, 0, p);
      crop.unlockCanvasAndPost(canvas);

      try {
        URL url = new URL("http://50.23.125.197:3000/ibmannapi");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        OutputStream os = conn.getOutputStream();
        //os.write(input.getBytes());
        os.flush();

        if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
          throw new RuntimeException("Failed : HTTP error code : "
                  + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getInputStream())));

        String output;
        System.out.println("Output from Server .... \n");
        while ((output = br.readLine()) != null) {
          System.out.println(output);
        }

        conn.disconnect();

      } catch (MalformedURLException e) {
        e.printStackTrace();
      } catch (ProtocolException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      //onPause();


    } else {
      onResume();
      crop = cropImageView.getHolder();
      Canvas canvas = crop.lockCanvas();
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      crop.unlockCanvasAndPost(canvas);
    }


    //
  }
/*
  @Override
 public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode){
      case 1: {
        //Wysie_Soh: After an image is taken and saved to the location of mImageCaptureUri, come here
        //and load the crop editor, with the necessary parameters (96x96, 1:1 ratio)

        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setClassName("org.tensorflow.demo", "org.tensorflow.demo.CameraConnectionFragment");

        intent.setData(picUri);
        intent.putExtra("outputX", 96);
        intent.putExtra("outputY", 96);
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("scale", true);
        intent.putExtra("return-data", true);
        startActivityForResult(intent, 2);

        break;

      }

      case 2:{

        final Bundle extras = data.getExtras();

        if (extras != null) {
          Bitmap photo = extras.getParcelable("data");

          cropImageView.setImageBitmap(photo);
        }

        File f = new File(picUri.getPath());
        if (f.exists()) {
          f.delete();
        }


        break;
      }
    }


  }
*/
/*
    private void performCrop(){

        try {
            //call the standard crop action intent (the user device may not support it)
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            //indicate image type and Uri
            cropIntent.setDataAndType(picUri, "image/*");
            //set crop properties
            cropIntent.putExtra("crop", "true");
            //indicate aspect of desired crop
            cropIntent.putExtra("aspectX", 1);
            cropIntent.putExtra("aspectY", 1);
            //indicate output X and Y
            cropIntent.putExtra("outputX", 256);
            cropIntent.putExtra("outputY", 256);
            //retrieve data on return
            cropIntent.putExtra("return-data", true);
            //start the activity - we handle returning in onActivityResult
            startActivityForResult(cropIntent, PIC_CROP);
        }
        catch(ActivityNotFoundException e){
            //display an error message
            String errorMessage = "Whoops - your device doesn't support the crop action!";
            Toast toast = Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }
    }
    */

  /**
   * Opens the camera specified by {@link CameraConnectionFragment#cameraId}.
   */
  private void openCamera(final int width, final int height) {
    setUpCameraOutputs(width, height);
    configureTransform(width, height);
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      if (ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  /**
   * Closes the current {@link CameraDevice}.
   */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != previewReader) {
        previewReader.close();
        previewReader = null;
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  /**
   * Starts a background thread and its {@link Handler}.
   */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("ImageListener");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());

    inferenceThread = new HandlerThread("InferenceThread");
    inferenceThread.start();
    inferenceHandler = new Handler(inferenceThread.getLooper());
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    inferenceThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;

      inferenceThread.join();
      inferenceThread = null;
      inferenceThread = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  private final TensorFlowImageListener tfPreviewListener = new TensorFlowImageListener();

  private final CameraCaptureSession.CaptureCallback captureCallback =
      new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(
            final CameraCaptureSession session,
            final CaptureRequest request,
            final CaptureResult partialResult) {}

        @Override
        public void onCaptureCompleted(
            final CameraCaptureSession session,
            final CaptureRequest request,
            final TotalCaptureResult result) {}
      };

  /**
   * Creates a new {@link CameraCaptureSession} for camera preview.
   */
  private void createCameraPreviewSession() {
    try {
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      final Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

      // Create the reader for the preview frames.
      previewReader =
          ImageReader.newInstance(
              previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

      previewReader.setOnImageAvailableListener(tfPreviewListener, backgroundHandler);
      previewRequestBuilder.addTarget(previewReader.getSurface());

      // Here, we create a CameraCaptureSession for camera preview.
      cameraDevice.createCaptureSession(
          Arrays.asList(surface, previewReader.getSurface()),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
              // The camera is already closed
              if (null == cameraDevice) {
                return;
              }

              // When the session is ready, we start displaying the preview.
              captureSession = cameraCaptureSession;
              try {
                // Auto focus should be continuous for camera preview.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // Flash is automatically enabled when necessary.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                // Finally, we start displaying the camera preview.
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(
                    previewRequest, captureCallback, backgroundHandler);
              } catch (final CameraAccessException e) {
                LOGGER.e(e, "Exception!");
              }
            }

            @Override
            public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
              showToast("Failed");
            }
          },
          null);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }

    LOGGER.i("Getting assets.");
    tfPreviewListener.initialize(
        getActivity().getAssets(), scoreView,boxView,inferenceHandler, sensorOrientation,textureView.getWidth(), textureView.getHeight());
    LOGGER.i("TensorFlow initialized.");
  }

  /**
   * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
   * This method should be called after the camera preview size is determined in
   * setUpCameraOutputs and also the size of `mTextureView` is fixed.
   *
   * @param viewWidth  The width of `mTextureView`
   * @param viewHeight The height of `mTextureView`
   */
  private void configureTransform(final int viewWidth, final int viewHeight) {
    final Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    final Matrix matrix = new Matrix();
    final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    final float centerX = viewRect.centerX();
    final float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      final float scale =
          Math.max(
              (float) viewHeight / previewSize.getHeight(),
              (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    textureView.setTransform(matrix);
  }



  /**
   * Compares two {@code Size}s based on their areas.
   */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  /**
   * Shows an error message dialog.
   */
  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(final String message) {
      final ErrorDialog dialog = new ErrorDialog();
      final Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, final int i) {
                  activity.finish();
                }
              })
          .create();
    }
  }


}
