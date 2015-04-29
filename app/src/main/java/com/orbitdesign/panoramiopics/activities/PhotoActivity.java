package com.orbitdesign.panoramiopics.activities;

import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.orbitdesign.panoramiopics.R;
import com.orbitdesign.panoramiopics.models.Photo;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class PhotoActivity extends AppCompatActivity {

    private static final String TAG = "PhotoActivity";
    private static final String APP_DIRECTORY = "/PicLoco";

    private Photo mPhoto;

    // This is a hacky method used to pass the small image from the MainActivity to the PhotoActivity
    // to mask network load times
    public static BitmapDrawable IMAGE_BITMAP_SMALL = null;

    private Bitmap largeBitmap = null;

    private SubsamplingScaleImageView mPhotoImageView;
    private TextView mTextViewDescriptionLine1, mTextViewDescriptionLine2, mTextViewDescriptionLine3, mTextViewDescriptionLine4;
    private CardView mCardView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        Toolbar toolbar = (Toolbar)findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mPhoto = getIntent().getParcelableExtra("photo");

        getSupportActionBar().setTitle(mPhoto.getPhotoTitle());

        initGui();

        // The small bitmap should be set by the main activity before launching this activity
        if(IMAGE_BITMAP_SMALL != null){
            Palette palette = Palette.from(IMAGE_BITMAP_SMALL.getBitmap()).generate();
            setupScreenUsingPalette(palette);
        }

        new AsycBitmapFromURL().execute(mPhoto.getLargePhotoFileUrl());

    }

    private void initGui() {
        mPhotoImageView = (SubsamplingScaleImageView) findViewById(R.id.photoImageView);

        mCardView = (CardView)findViewById(R.id.metaDataCard);
        mTextViewDescriptionLine1 = (TextView)findViewById(R.id.textViewLine1);
        mTextViewDescriptionLine2 = (TextView)findViewById(R.id.textViewLine2);
        mTextViewDescriptionLine3 = (TextView)findViewById(R.id.textViewLine3);
        mTextViewDescriptionLine4 = (TextView)findViewById(R.id.textViewLine4);

        mTextViewDescriptionLine1.setText(mPhoto.getPhotoTitle());
        mTextViewDescriptionLine2.setText(mPhoto.getOwnerName());
        mTextViewDescriptionLine3.setText(mPhoto.getUploadDate());
        mTextViewDescriptionLine4.setText(String.format(Locale.getDefault(),"Location: %.3f, %.3f", mPhoto.getLatitude(), mPhoto.getLongitude()));

        mTextViewDescriptionLine4.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
        mTextViewDescriptionLine4.setMovementMethod(LinkMovementMethod.getInstance());
        mTextViewDescriptionLine4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String uri = String.format(Locale.ENGLISH, "geo:%f,%f", mPhoto.getLatitude(), mPhoto.getLongitude());
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                view.getContext().startActivity(intent);
            }
        });

    }




    private void setupScreenUsingPalette(Palette palette) {

        Palette.Swatch vibrantSwatch = palette.getDarkVibrantSwatch();

        if(vibrantSwatch == null){
            vibrantSwatch = palette.getVibrantSwatch();
        }

        if (vibrantSwatch != null) {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(vibrantSwatch.getRgb()));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = this.getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.setStatusBarColor(vibrantSwatch.getRgb());
            }
        }

        Palette.Swatch darkMutedSwatch = palette.getDarkMutedSwatch();

        if (darkMutedSwatch != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(darkMutedSwatch.getRgb()));
        }


        Palette.Swatch cardSwatch = palette.getLightMutedSwatch();

        if(cardSwatch != null){
            mCardView.setCardBackgroundColor(cardSwatch.getRgb());
            int textColor = cardSwatch.getBodyTextColor();
            mTextViewDescriptionLine1.setTextColor(textColor);
            mTextViewDescriptionLine2.setTextColor(textColor);
            mTextViewDescriptionLine3.setTextColor(textColor);
            mTextViewDescriptionLine4.setTextColor(textColor);
        }




    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_photo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_share) {
            sharePhoto();

            return true;
        }else if (id == R.id.action_set_as_wallpaper) {
            promptAndSetPhotoAsWallpaper();
            return true;
        }else if (id == R.id.action_save_to_phone) {
            Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show();
            savePhoto();

            return true;
        }else if (id == android.R.id.home) {
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Uri getPhotoUri(){
        File path = new File(Environment.getExternalStorageDirectory().toString()+ APP_DIRECTORY);
        File file = new File(path, mPhoto.getPhotoTitle()+".jpg");
        Uri photoLocation;
        if(file.exists()){
            photoLocation = Uri.fromFile(file);
        }else {
            photoLocation = savePhoto();
        }
        return photoLocation;
    }

    private void sharePhoto() {

        final Intent shareIntent = new Intent(     android.content.Intent.ACTION_SEND);
        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.putExtra(Intent.EXTRA_STREAM, getPhotoUri());
        shareIntent.putExtra(Intent.EXTRA_TEXT, "sent from PicLoco app");
        shareIntent.setType("image/jpg");
        startActivity(shareIntent);
    }

    private Uri savePhoto() {

        File path = new File(Environment.getExternalStorageDirectory().toString()+APP_DIRECTORY);

        if(!path.exists()){
           if(!path.mkdir()) Toast.makeText(this, "Oops. Could not create folder. Please send us an email so we can fix this.", Toast.LENGTH_LONG).show();
        }

        File file = new File(path, mPhoto.getPhotoTitle()+".jpg"); // the File to save to

        Log.d(TAG,"File name = "+file.getName());
        Log.d(TAG,"File path = "+file.getAbsolutePath());
        Log.d(TAG,"Path name = "+path);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            getPhotoBitmap().compress(Bitmap.CompressFormat.JPEG, 85, out); // bmp is your Bitmap instance



        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, "Oops. There was an error. Please send us an email so we can fix this.", Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(file.exists()){
            Uri uri = addImageToGallery(this, file.toString(), mPhoto.getPhotoTitle(), "from PicLoco app");

            return uri;
        }else{
            return null;
        }

    }

    public Uri addImageToGallery(Context context, String filepath, String title, String description) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, title);
        values.put(MediaStore.Images.Media.DESCRIPTION, description);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.DATA, filepath);

        return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private void promptAndSetPhotoAsWallpaper() {

        Intent setAs = new Intent(Intent.ACTION_ATTACH_DATA);
        setAs.setDataAndType(getPhotoUri(),"image/jpg");
        setAs.putExtra("mimeType", "image/jpg");
        startActivity(Intent.createChooser(setAs, "Set Image As"));
    }

    private Bitmap getPhotoBitmap(){
        return largeBitmap;
    }

    public void setLoading(Boolean isloading){
        if(isloading) {
            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        }else{
            findViewById(R.id.progressBar).setVisibility(View.GONE);
        }
    }

    private class AsycBitmapFromURL extends AsyncTask<String, Void, Bitmap>{

        OkHttpClient okHttpClient = new OkHttpClient();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setLoading(true);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            Request request = new Request.Builder().url(params[0]).build();
            Bitmap bitmap = null;
            try {
                InputStream is = okHttpClient.newCall(request).execute().body().byteStream();

                bitmap = BitmapFactory.decodeStream(is);

            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }


            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            if (bitmap != null) {
                largeBitmap = bitmap;
                mPhotoImageView.setImage(ImageSource.bitmap(bitmap));
            }else{
                Log.e(TAG, "Bitmap is null");
                Toast.makeText(PhotoActivity.this, "Oops, there was a mistake. Please contact me so I can fix it. Thanks!", Toast.LENGTH_LONG).show();
            }

            setLoading(false);
        }
    }

}
