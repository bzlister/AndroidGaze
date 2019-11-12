package fyordan.androidgaze;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

import static android.graphics.Bitmap.createBitmap;
import static android.graphics.Bitmap.createScaledBitmap;

public class MainActivity extends Activity {

    private static boolean DBG = BuildConfig.DEBUG; // provide normal log output only in debug version

    protected static FaceDetector faceDetector = null;
    protected static GazeDetector gazeDetector = null;
    protected Bitmap mBitmap;
    protected static Bitmap mLeftEyeBitmap;
    protected static Bitmap mRightEyeBitmap;
    protected static int[] mDebugArray;
    protected static byte[] mFrameArray;
    protected CameraSource mCameraSource = null;
    protected CameraSourcePreview mPreview;
    protected GraphicOverlay mGraphicOverlay;
    protected int eyeRegionWidth = 160;
    protected int eyeRegionHeight = 120;
    protected int mDownSampleScale = 2;
    protected int mUpSampleScale = 4;
    protected int mDThresh = 10;
    protected double mGradThresh = 25.0;
    protected int l_iris_pixel = 0;
    protected int lx;
    protected int ly;
    protected int r_iris_pixel = 0;
    protected int rx;
    protected int ry;
    protected int mUpThreshold = 8;
    protected int mDownThreshold = -4;
    protected int mLeftThreshold = 6;
    protected int mRightThreshold = -6;

    protected int circle_x = 40;
    protected int circle_y = 40;
    protected Paint circlePaint;
    protected long start = System.currentTimeMillis()/2000;
    protected long currentTime = System.currentTimeMillis()/2000;
    protected int hopsCount = 1;
    protected boolean turned = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // go full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // and hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getPermissions();   // NOTE: can *not* assume we actually have permissions after this call
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        // Face Tracking stuff
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        faceDetector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(true)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();
        gazeDetector = new GazeDetector(faceDetector);
        gazeDetector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

        mCameraSource = new CameraSource.Builder(getApplicationContext(), gazeDetector)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
        circlePaint= new Paint();
        circlePaint.setColor(Color.BLUE);
        circlePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    // which means the CameraDevice has to be (re-)opened when the activity is (re-)started
    // (as long as we have permission to use the camera)

    @Override
    protected void onResume() {
        super.onResume();
        if (bCameraPermissionGranted && (mCameraSource != null) && (mPreview != null)) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch(IOException e) {
                // WHO CARES
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraSource.release();
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

//////////////////////////////////////////////////////////////////////////////////////////////////////

    // For Android 6.0 (API Level 25)  permission requests

    private static final int REQ_PERMISSION_THISAPP = 0; // unique code for permissions request
    private static boolean bUseCameraFlag = true;               // we want to use the camera
    private static boolean bCameraPermissionGranted = false;   // have CAMERA permission

    private void getPermissions() {
        String TAG = "getPermissions";
        if (DBG) Log.v(TAG, "in getPermissions()");
        if (Build.VERSION.SDK_INT >= 23) {            // need to ask at runtime as of Android 6.0
            String sPermissions[] = new String[2];    // space for possible permission strings
            int nPermissions = 0;    // count of permissions to be asked for
            if (bUseCameraFlag) {    // protection level: dangerous
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    bCameraPermissionGranted = true;
                else sPermissions[nPermissions++] = Manifest.permission.CAMERA;
            }
            if (nPermissions > 0) {
                if (DBG) Log.d(TAG, "Need to ask for " + nPermissions + " permissions");
                if (nPermissions < sPermissions.length)
                    sPermissions = Arrays.copyOf(sPermissions, nPermissions);
                if (DBG) {
                    for (String sPermission : sPermissions)
                        Log.w(TAG, sPermission);    // debugging only
                }
                requestPermissions(sPermissions, REQ_PERMISSION_THISAPP);    // start the process
            }
        } else {    // in earlier API, permission is dealt with at install time, not run time
            if (bUseCameraFlag) bCameraPermissionGranted = true;
        }
    }

    //	Note: onRequestPermissionsResult happens *after* user has interacted with the permissions request
    //  So, annoyingly, have to now (re-)do things that didn't happen in onCreate() because permissions were not there yet.

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        String TAG = "onRequestPermitResult";
        if (DBG) Log.w(TAG, "in onRequestPermissionsResult(...) (" + requestCode + ")");
        if (requestCode != REQ_PERMISSION_THISAPP) {    // check that this is a response to our request
            Log.e(TAG, "Unexpected requestCode " + requestCode);    // can this happen?
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        int n = grantResults.length;
        if (DBG) Log.w(TAG, "requestCode=" + requestCode + " for " + n + " permissions");
        for (int i = 0; i < n; i++) {
            if (DBG) Log.w(TAG, "permission " + permissions[i] + " " + grantResults[i]);
            switch (permissions[i]) {
                case Manifest.permission.CAMERA:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        if (DBG) Log.w(TAG, "CAMERA Permission granted (" + i + ")");
                        bCameraPermissionGranted = true;
                        // redo the setup in onResume(...) ?
                    } else {
                        bUseCameraFlag = false;
                        String str = "You must grant CAMERA permission to use the camera!";
                        Log.e(TAG, str);
                    }
                    break;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker (Stolen from google)
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }


        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    /**
     * Graphic instance for rendering face position, orientation, and landmarks within an associated
     * graphic overlay view.
     */
    class FaceGraphic extends GraphicOverlay.Graphic {
        private static final float ID_TEXT_SIZE = 40.0f;
        private static final float BOX_STROKE_WIDTH = 5.0f;

        private final int COLOR_CHOICES[] = {
                Color.BLUE,
                Color.CYAN,
                Color.GREEN,
                Color.MAGENTA,
                Color.RED,
                Color.WHITE,
                Color.YELLOW
        };
        private int mCurrentColorIndex = 0;

        private Paint mFacePositionPaint;
        private Paint mIdPaint;
        private Paint mBoxPaint;

        private volatile Face mFace;

        FaceGraphic(GraphicOverlay overlay) {
            super(overlay);

            mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
            final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

            mFacePositionPaint = new Paint();
            mFacePositionPaint.setColor(selectedColor);

            mIdPaint = new Paint();
            mIdPaint.setColor(selectedColor);
            mIdPaint.setTextSize(ID_TEXT_SIZE);

            mBoxPaint = new Paint();
            mBoxPaint.setColor(selectedColor);
            mBoxPaint.setStyle(Paint.Style.STROKE);
            mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
        }


        /**
         * Updates the face instance from the detection of the most recent frame.  Invalidates the
         * relevant portions of the overlay to trigger a redraw.
         */
        void updateFace(Face face) {
            mFace = face;
            postInvalidate();
        }

        /**
         * Draws the face annotations for position on the supplied canvas.
         */
        @Override
        public void draw(Canvas canvas) {
            Face face = mFace;
            if (face == null) {
                return;
            }
            if (System.currentTimeMillis()/2000 - currentTime > 0)
                turned = true;
            currentTime = System.currentTimeMillis()/2000;
            long deltaTime = currentTime - start;
            if (turned) {
                if (deltaTime % 4 == 0) {
                    circlePaint.setColor(Color.BLUE);
                    Log.i("GazeIntoTheIris", "ImGoingBlue");
                    if (deltaTime > 0) {
                        circle_x = (circle_x + canvas.getWidth()/3)%canvas.getWidth();
                        circle_y = 40+ (hopsCount/3)*canvas.getHeight()/3;
                    }
                    hopsCount++;
                } else {
                    circlePaint.setColor(Color.RED);
                    Log.i("GazeIntoTheIris", "ImGoingRed");
                }
                turned = false;
            }
            if (hopsCount < 10) {
                canvas.drawCircle(circle_x, circle_y, 40, circlePaint);
                Log.i("GazeIntoTheIris", String.format("DOT: %d, %d", circle_x, circle_y));
            }
            else
                Log.i("GazeIntoTheIris", "STOP");
            for (Landmark landmark : face.getLandmarks()) {
                int landmark_type = landmark.getType();
                if (landmark_type == Landmark.LEFT_EYE) {
                    lx = (int) translateX(landmark.getPosition().x);
                    ly = (int) translateY(landmark.getPosition().y);

                    int eye_region_left = (int)landmark.getPosition().x-eyeRegionWidth/2;
                    int eye_region_top = (int)landmark.getPosition().y-eyeRegionHeight/2;
                    mLeftEyeBitmap = createBitmap(mBitmap,Math.min(Math.max(eye_region_left, 0), mBitmap.getWidth()-eyeRegionWidth), Math.min(Math.max(eye_region_top, 0), mBitmap.getHeight()-eyeRegionHeight),eyeRegionWidth, eyeRegionHeight);

                    l_iris_pixel = calculateEyeCenter(mLeftEyeBitmap, mGradThresh, mDThresh);
                    int iris_x = l_iris_pixel%mLeftEyeBitmap.getWidth()*mDownSampleScale*mUpSampleScale;
                    int iris_y = l_iris_pixel/mLeftEyeBitmap.getWidth()*mDownSampleScale*mUpSampleScale;
                    int x_gaze = l_iris_pixel%mLeftEyeBitmap.getWidth() - mLeftEyeBitmap.getWidth()/2;
                    int y_gaze = mLeftEyeBitmap.getHeight()/2 - l_iris_pixel/mLeftEyeBitmap.getWidth();
                    Log.i("GazeIntoTheIris", String.format("LEFT: %d, %d, %d, %d", iris_x, iris_y, x_gaze, y_gaze));
                }
                else if (landmark_type == Landmark.RIGHT_EYE) {
                    rx = (int) translateX(landmark.getPosition().x);
                    ry = (int) translateY(landmark.getPosition().y);

                    int eye_region_left = (int)landmark.getPosition().x-eyeRegionWidth/2;
                    int eye_region_top = (int)landmark.getPosition().y-eyeRegionHeight/2;
                    mRightEyeBitmap = createBitmap(mBitmap, Math.min(Math.max(eye_region_left, 0), mBitmap.getWidth()-eyeRegionWidth), Math.min(Math.max(eye_region_top, 0), mBitmap.getHeight()-eyeRegionHeight), eyeRegionWidth, eyeRegionHeight);
                    r_iris_pixel = calculateEyeCenter(mRightEyeBitmap, mGradThresh, mDThresh);
                    int iris_x = r_iris_pixel%mRightEyeBitmap.getWidth()*mDownSampleScale*mUpSampleScale;
                    int iris_y = r_iris_pixel/mRightEyeBitmap.getWidth()*mDownSampleScale*mUpSampleScale;
                    int x_gaze = r_iris_pixel%mRightEyeBitmap.getWidth() - mRightEyeBitmap.getWidth()/2;
                    int y_gaze = mLeftEyeBitmap.getHeight()/2 - l_iris_pixel/mRightEyeBitmap.getWidth();
                    Log.i("GazeIntoTheIris", String.format("RIGHT: %d, %d, %d, %d", iris_x, iris_y, x_gaze, y_gaze));
                }
            }
        }

        protected int calculateEyeCenter(Bitmap eyeMap, double gradientThreshold, int d_thresh) {
            Log.e("CalculateEyeCenter", "Well it entered");
            int imageWidth = eyeMap.getWidth();
            int imageHeight = eyeMap.getHeight();
            int grayData[] = new int[imageWidth*imageHeight];
            double mags[] = new double[(imageWidth-2)*(imageHeight-2)];
            Log.e("CalculateEyeCenter", "Size is : " + imageWidth*imageHeight);
            eyeMap.getPixels(grayData, 0, imageWidth, 0, 0, imageWidth, imageHeight);
            double[][] gradients = new double[(imageWidth-2)*(imageHeight-2)][2];
            int k = 0;
            int magCount = 0;
            mDebugArray = new int[(imageWidth-2)*(imageHeight-2)];
            for(int j=1; j < imageHeight-1; j++) {
                for (int i=1; i < imageWidth-1; i++) {
                    int n = j*imageWidth + i;
                    gradients[k][0] = (grayData[n+1] & 0xff) - (grayData[n] & 0xff);
                    gradients[k][1] = (grayData[n + imageWidth] & 0xff) - (grayData[n] & 0xff);
                    double mag = Math.sqrt(Math.pow(gradients[k][0],2) + Math.pow(gradients[k][1],2));
                    mags[k] = mag;
                    mDebugArray[k] = grayData[n];
                    if (mag > gradientThreshold) {
                        gradients[k][0] /= mag;
                        gradients[k][1] /= mag;
                        magCount++;
                        mDebugArray[k] = 0xffffffff;
                    } else {
                        gradients[k][0] = 0;
                        gradients[k][1] = 0;
                    }
                    k++;
                }
            }
            Log.e("CalculateEyeCenter", "mags above threshold: " + magCount);
            Log.e("CalculateEyeCenter", "Now we need to iterate through them all again");
            int c_n = gradients.length/2;
            double max_c = 0;
            for (int i=1; i < imageWidth-1; i++) {
                for (int j=1; j < imageHeight-1; j++) {
                    int n = j*imageWidth + i;
                    int k_left = Math.max(0, i - d_thresh - 1);
                    int k_right= Math.min(imageWidth-2, i+d_thresh-1);
                    int k_top = Math.max(0, j - d_thresh-1);
                    int k_bottom = Math.min(imageHeight-2, j+d_thresh-1);
                    double sumC = 0;
                    for (int k_h = k_top; k_h < k_bottom; ++k_h) {
                        for (int k_w = k_left; k_w < k_right; ++k_w) {
                            k = k_w + k_h*(imageWidth-2);
                            if ((gradients[k][0] == 0 && gradients[k][1] == 0)) continue;
                            double d_i = k_w - i;
                            double d_j = k_h - j;
                            if (Math.abs(d_i) > d_thresh || Math.abs(d_j) > d_thresh) continue;
                            double mag = Math.sqrt(Math.pow(d_i, 2) + Math.pow(d_j, 2));
                            if (mag > d_thresh) continue;
                            mag = mag == 0 ? 1 : mag;
                            d_i /= mag;
                            d_j /= mag;
                            sumC += Math.pow(d_i * gradients[k][0] + d_j * gradients[k][1], 2);
                        }
                    }
                    sumC /= (grayData[n] & 0xff);
                    if (sumC > max_c) {
                        c_n = n;
                        max_c = sumC;
                    }
                }
            }
            return c_n;
        }
    }

    protected Bitmap toGrayscale(Bitmap bmp){
        Bitmap grayscale = createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(grayscale);
        Paint paint=new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmp, 0, 0, paint);
        return grayscale;
    }

    class GazeDetector extends Detector<Face> {
        private Detector<Face> mDelegate;

        GazeDetector(Detector<Face> delegate) {
            mDelegate = delegate;
        }

        public SparseArray<Face> detect(Frame frame) {
            int w = frame.getMetadata().getWidth();
            int h = frame.getMetadata().getHeight();
            YuvImage yuvimage=new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(
                    new Rect(0, 0, w, h), 100, baos); // Where 100 is the quality of the generated jpeg
            mFrameArray = baos.toByteArray();
            mBitmap = BitmapFactory.decodeByteArray(mFrameArray, 0, mFrameArray.length);
            return mDelegate.detect(frame);
        }

        public boolean isOperational() {
            return mDelegate.isOperational();
        }

        public boolean setFocus(int id) {
            return mDelegate.setFocus(id);
        }
    }
}
