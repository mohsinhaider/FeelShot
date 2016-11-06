package com.mohsinhaider.feelshotapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.gson.Gson;
import com.microsoft.projectoxford.emotion.EmotionServiceClient;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.emotion.contract.FaceRectangle;
import com.microsoft.projectoxford.emotion.contract.RecognizeResult;
import com.microsoft.projectoxford.emotion.rest.EmotionServiceException;
import com.mohsinhaider.feelshotapplication.helper.ImageHelper;

import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class RecognizeActivity extends ActionBarActivity {

    private static final int REQUEST_SELECT_IMAGE = 0;
    private Button mButtonSelectImage;
    private Uri mImageUri;
    private Bitmap mBitmap;
    private EmotionServiceClient client;

    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        

        if (client == null) {
            client = new EmotionServiceRestClient(getString(R.string.subscription_key));
        }

        mButtonSelectImage = (Button) findViewById(R.id.buttonSelectImage);
        //mEditText = (EditText) findViewById(R.id.editTextResult);
    }


    public void doRecognize() {
        mButtonSelectImage.setEnabled(false);

        // Do emotion detection using auto-detected faces.
        try {
            new doRequest(false).execute();
        } catch (Exception e) {
            //mEditText.append("Error encountered. Exception is: " + e.toString());
        }

        String faceSubscriptionKey = getString(R.string.faceSubscription_key);
        if (faceSubscriptionKey.equalsIgnoreCase("Please_add_the_face_subscription_key_here")) {
            //mEditText.append("\n\nThere is no face subscription key in res/values/strings.xml. Skip the sample for detecting emotions using face rectangles\n");
        } else {
            // Do emotion detection using face rectangles provided by Face API.
            try {
                new doRequest(true).execute();
            } catch (Exception e) {
                //mEditText.append("Error encountered. Exception is: " + e.toString());
            }
        }
    }

    // Called when the "Select Image" button is clicked.
    public void selectImage(View view) {
        //mEditText.setText("");

        Intent intent;
        intent = new Intent(RecognizeActivity.this, com.mohsinhaider.feelshotapplication.helper.SelectImageActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    // Called when image selection is done.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("RecognizeActivity", "onActivityResult");
        switch (requestCode) {
            case REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    // If image is selected successfully, set the image URI and bitmap.
                    mImageUri = data.getData();

                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null) {
                        // Show the image on screen.
                        ImageView imageView = (ImageView) findViewById(R.id.selectedImage);
                        imageView.setImageBitmap(mBitmap);

                        // Add detection log.
                        Log.d("RecognizeActivity", "Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        doRecognize();
                    }
                }
                break;
            default:
                break;
        }
    }


    private List<RecognizeResult> processWithAutoFaceDetection() throws EmotionServiceException, IOException {
        Log.d("emotion", "Start emotion detection with auto-face detection");

        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        long startTime = System.currentTimeMillis();
        // -----------------------------------------------------------------------
        // KEY SAMPLE CODE STARTS HERE
        // -----------------------------------------------------------------------

        List<RecognizeResult> result = null;
        //
        // Detect emotion by auto-detecting faces in the image.
        //
        result = this.client.recognizeImage(inputStream);

        String json = gson.toJson(result);
        Log.d("result", json);

        Log.d("emotion", String.format("Detection done. Elapsed time: %d ms", (System.currentTimeMillis() - startTime)));
        // -----------------------------------------------------------------------
        // KEY SAMPLE CODE ENDS HERE
        // -----------------------------------------------------------------------
        return result;
    }

    private List<RecognizeResult> processWithFaceRectangles() throws EmotionServiceException, com.microsoft.projectoxford.face.rest.ClientException, IOException {
        Log.d("emotion", "Do emotion detection with known face rectangles");
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        long timeMark = System.currentTimeMillis();
        Log.d("emotion", "Start face detection using Face API");
        FaceRectangle[] faceRectangles = null;
        String faceSubscriptionKey = getString(R.string.faceSubscription_key);
        FaceServiceRestClient faceClient = new FaceServiceRestClient(faceSubscriptionKey);
        Face faces[] = faceClient.detect(inputStream, false, false, null);
        Log.d("emotion", String.format("Face detection is done. Elapsed time: %d ms", (System.currentTimeMillis() - timeMark)));

        if (faces != null) {
            faceRectangles = new FaceRectangle[faces.length];

            for (int i = 0; i < faceRectangles.length; i++) {
                // Face API and Emotion API have different FaceRectangle definition. Do the conversion.
                com.microsoft.projectoxford.face.contract.FaceRectangle rect = faces[i].faceRectangle;
                faceRectangles[i] = new com.microsoft.projectoxford.emotion.contract.FaceRectangle(rect.left, rect.top, rect.width, rect.height);
            }
        }

        List<RecognizeResult> result = null;
        if (faceRectangles != null) {
            inputStream.reset();

            timeMark = System.currentTimeMillis();
            Log.d("emotion", "Start emotion detection using Emotion API");
            // -----------------------------------------------------------------------
            // KEY SAMPLE CODE STARTS HERE
            // -----------------------------------------------------------------------
            result = this.client.recognizeImage(inputStream, faceRectangles);

            String json = gson.toJson(result);
            Log.d("result", json);
            // -----------------------------------------------------------------------
            // KEY SAMPLE CODE ENDS HERE
            // -----------------------------------------------------------------------
            Log.d("emotion", String.format("Emotion detection is done. Elapsed time: %d ms", (System.currentTimeMillis() - timeMark)));
        }
        return result;
    }

    private class doRequest extends AsyncTask<String, String, List<RecognizeResult>> {
        // Store error message
        private Exception e = null;
        private boolean useFaceRectangles = false;

        public doRequest(boolean useFaceRectangles) {
            this.useFaceRectangles = useFaceRectangles;
        }

        @Override
        protected List<RecognizeResult> doInBackground(String... args) {
            if (this.useFaceRectangles == false) {
                try {
                    return processWithAutoFaceDetection();
                } catch (Exception e) {
                    this.e = e;    // Store error
                }
            } else {
                try {
                    return processWithFaceRectangles();
                } catch (Exception e) {
                    this.e = e;    // Store error
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<RecognizeResult> result) {
            super.onPostExecute(result);
            // Display based on error existence

            if (this.useFaceRectangles == false) {
                //mEditText.append("\n\nRecognizing emotions with auto-detected face rectangles...\n");
            } else {
                //mEditText.append("\n\nRecognizing emotions with existing face rectangles from Face API...\n");
            }
            if (e != null) {
                //mEditText.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                if (result.size() == 0) {
                    //mEditText.append("No emotion detected :(");
                } else {
                    Integer count = 0;
                    // Covert bitmap to a mutable bitmap by copying it
                    Bitmap bitmapCopy = mBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas faceCanvas = new Canvas(bitmapCopy);
                    faceCanvas.drawBitmap(mBitmap, 0, 0, null);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5);
                    paint.setColor(Color.RED);

                    for (RecognizeResult r : result) {
                        Log.d("Anger", String.format("\t anger: %1$.5f\n", r.scores.anger));
                        Log.d("Contempt", String.format("\t contempt: %1$.5f\n", r.scores.contempt));

                        TextView angerPercentView = (TextView) findViewById(R.id.textView15);
                        TextView contemptPercentView = (TextView) findViewById(R.id.textView16);
                        TextView disgustPercentView = (TextView) findViewById(R.id.textView17);
                        TextView fearPercentView = (TextView) findViewById(R.id.textView18);
                        TextView happinessPercentView = (TextView) findViewById(R.id.textView19);
                        TextView neutralPercentView = (TextView) findViewById(R.id.textView20);
                        TextView sadnessPercentView = (TextView) findViewById(R.id.textView21);
                        TextView surprisePercentView = (TextView) findViewById(R.id.textView22);

                        angerPercentView.setText(String.format("%1$.0f", r.scores.anger * 100) + "%");
                        contemptPercentView.setText(String.format("%1$.0f", r.scores.contempt * 100) + "%");
                        disgustPercentView.setText(String.format("%1$.0f", r.scores.disgust * 100) + "%");
                        fearPercentView.setText(String.format("%1$.0f", r.scores.fear * 100) + "%");
                        happinessPercentView.setText(String.format("%1$.0f", r.scores.happiness * 100) + "%");
                        neutralPercentView.setText(String.format("%1$.0f", r.scores.neutral * 100) + "%");
                        sadnessPercentView.setText(String.format("%1$.0f", r.scores.sadness * 100) + "%");
                        surprisePercentView.setText(String.format("%1$.0f", r.scores.surprise * 100) + "%");

                        ProgressBar angerBar = (ProgressBar) findViewById(R.id.anger_bar);
                        ProgressBar contemptBar = (ProgressBar) findViewById(R.id.contempt_bar);
                        ProgressBar disgustBar = (ProgressBar) findViewById(R.id.disgust_bar);
                        ProgressBar fearBar = (ProgressBar) findViewById(R.id.fear_bar);
                        ProgressBar happinessBar = (ProgressBar) findViewById(R.id.happiness_bar);
                        ProgressBar neutralBar = (ProgressBar) findViewById(R.id.neutral_bar);
                        ProgressBar sadnessBar = (ProgressBar) findViewById(R.id.sadness_bar);
                        ProgressBar surpriseBar = (ProgressBar) findViewById(R.id.surprise_bar);

                        angerBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.anger * 100)));
                        contemptBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.contempt * 100)));
                        disgustBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.disgust * 100)));
                        fearBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.fear * 100)));
                        happinessBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.happiness * 100)));
                        neutralBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.neutral * 100)));
                        sadnessBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.sadness * 100)));
                        surpriseBar.setProgress(Integer.parseInt(String.format("%1$.0f", r.scores.surprise * 100)));

//                        mEditText.append(String.format("\nFace #%1$d \n", count));
//                        mEditText.append(String.format("\t face rectangle: %d, %d, %d, %d", r.faceRectangle.left, r.faceRectangle.top, r.faceRectangle.width, r.faceRectangle.height));
//                        faceCanvas.drawRect(r.faceRectangle.left,
//                                r.faceRectangle.top,
//                                r.faceRectangle.left + r.faceRectangle.width,
//                                r.faceRectangle.top + r.faceRectangle.height,
//                                paint);
//                        count++;
                    }

                    ImageView imageView = (ImageView) findViewById(R.id.selectedImage);
                    imageView.setImageDrawable(new BitmapDrawable(getResources(), mBitmap));
                }
                //mEditText.setSelection(0);
            }

            mButtonSelectImage.setEnabled(true);
        }
    }
}

