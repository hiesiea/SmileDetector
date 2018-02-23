package jp.hiesiea.app.smiledetector;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jp.hiesiea.app.smiledetecrot.R;

public class CallCloudVisionAsyncTask extends AsyncTask<Void, Void, String> {
    private static final String TAG = CallCloudVisionAsyncTask.class.getSimpleName();

    private static final String SOUND_FILE_NAME = "deden.mp3";

    private static final String TYPE_FACE_DETECTION = "FACE_DETECTION";

    private static final String RESULT_VERY_LIKELY = "VERY_LIKELY";
    private static final String RESULT_LIKELY = "LIKELY";

    private VisionRequestInitializer mVisionRequestInitializer;
    private Bitmap mBitmap;
    private Context mContext;

    private TextView mTextView;
    private ProgressDialog mProgressDialog;
    private Animation mAnimation;
    private MediaPlayer mMediaPlayer;

    private MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
        }
    };

    private Animation.AnimationListener mAnimationListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
            mTextView.setText("");
            animation.reset();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    };

    /**
     * CloudVisionAPI用の非同期処理呼び出し
     * @param visionRequestInitializer
     * @param bitmap
     * @param textView
     * @param context
     */
    public CallCloudVisionAsyncTask(
            VisionRequestInitializer visionRequestInitializer,
            Bitmap bitmap,
            TextView textView,
            Context context) {
        mVisionRequestInitializer = visionRequestInitializer;
        mBitmap = bitmap;
        mTextView = textView;
        mContext = context;
        mProgressDialog = new ProgressDialog(context);
        mAnimation = AnimationUtils.loadAnimation(context, R.anim.translate_animation);
        mAnimation.setAnimationListener(mAnimationListener);
        setUpMediaPlayer();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        setUpProgressDialog();
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            Vision.Builder builder =
                    new Vision.Builder(httpTransport, jsonFactory, null);
            builder.setVisionRequestInitializer(mVisionRequestInitializer);

            BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                    new BatchAnnotateImagesRequest();
            batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                // Add the image
                Image base64EncodedImage = new Image();
                // Convert the bitmap to a JPEG
                // Just in case it's a format that Android understands but Cloud Vision
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                byte[] imageBytes = byteArrayOutputStream.toByteArray();

                // Base64 encode the JPEG
                base64EncodedImage.encodeContent(imageBytes);
                annotateImageRequest.setImage(base64EncodedImage);

                // add the features we want
                annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                    Feature labelDetection = new Feature();
                    labelDetection.setType(TYPE_FACE_DETECTION);
                    add(labelDetection);
                }});

                // Add the list of one thing to the request
                add(annotateImageRequest);
            }});

            Vision vision = builder.build();
            Vision.Images.Annotate annotateRequest =
                    vision.images().annotate(batchAnnotateImagesRequest);
            // Due to a bug: requests to Vision API containing large images fail when GZipped.
            annotateRequest.setDisableGZipContent(true);
            Log.d(TAG, "created Cloud Vision request object, sending request");

            BatchAnnotateImagesResponse response = annotateRequest.execute();
            if (checkSmile(response)) {
                mMediaPlayer.start();
                return mContext.getResources().getString(R.string.image_smile_out);
            }
            return mContext.getResources().getString(R.string.image_smile_safe);
        } catch (GoogleJsonResponseException e) {
            Log.d(TAG, "failed to make API request because " + e.getContent());
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
        return "Cloud Vision API request failed. Check logs for details.";
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        mTextView.setText(result);
        mTextView.startAnimation(mAnimation);
        mProgressDialog.dismiss();
    }

    /**
     * 笑顔判定
     * @param response
     * @return
     */
    private boolean checkSmile(BatchAnnotateImagesResponse response) {
        List<FaceAnnotation> faceAnnotations = response.getResponses().get(0).getFaceAnnotations();
        if (faceAnnotations == null) {
            return false;
        }

        for (FaceAnnotation faceAnnotation : faceAnnotations) {
            printLog(faceAnnotation);
            if (faceAnnotation.getJoyLikelihood().equals(RESULT_VERY_LIKELY)
                    || faceAnnotation.getJoyLikelihood().equals(RESULT_LIKELY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * プログレスダイアログの設定および表示
     */
    private void setUpProgressDialog() {
        mProgressDialog.setMessage(mContext.getResources().getString(R.string.loading_message));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    /**
     * 音楽ファイルを再生するための設定
     */
    private void setUpMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        try (AssetFileDescriptor assetFileDescriptor = mContext.getAssets().openFd(SOUND_FILE_NAME)) {
            mMediaPlayer.setDataSource(assetFileDescriptor.getFileDescriptor(),
                    assetFileDescriptor.getStartOffset(),
                    assetFileDescriptor.getLength());
            mMediaPlayer.prepare();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * 各パラメータのログ出力
     * @param faceAnnotation
     */
    private void printLog(FaceAnnotation faceAnnotation) {
        Log.d(TAG, "getAngerLikelihood : " + faceAnnotation.getAngerLikelihood());
        Log.d(TAG, "getBlurredLikelihood : " + faceAnnotation.getBlurredLikelihood());
        Log.d(TAG, "getHeadwearLikelihood : " + faceAnnotation.getHeadwearLikelihood());
        Log.d(TAG, "getJoyLikelihood : " + faceAnnotation.getJoyLikelihood());
        Log.d(TAG, "getSorrowLikelihood : " + faceAnnotation.getSorrowLikelihood());
        Log.d(TAG, "getSurpriseLikelihood : " + faceAnnotation.getSurpriseLikelihood());
        Log.d(TAG, "getUnderExposedLikelihood : " + faceAnnotation.getUnderExposedLikelihood());
    }
}
