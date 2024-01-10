package com.example.photofilter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.SplashScreen;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    ImageView imageView;
    HorizontalScrollView toolsLayout;
    ConstraintLayout brightnessSeekBarLayout,contrastSeekBarLayout,filterBtnsLayout;
    TextView brightnessBtn,brightnessSeekBarOkView,contrastBtn,contrastSeekBarOkView;
    SeekBar brightnessSeekerBar,contrastSeekerBar;
    BitmapDrawable ogBmp;
    TextView filterBtn,filterBackBtn;
    String filtered;
    Bitmap filteredBmp;

    ActivityResultLauncher<String> storagePermissionLauncher;
    final String storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    TextView openPhotoStoreBtn;
    TextView savePhotoBtn;

    TextView camBtn;

    private static final int pic_id = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.photoView);
        ogBmp = (BitmapDrawable) imageView.getDrawable();


        initializeViews();

        // permission launcher
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
           if (result) {
               getPhotos(); // permission granted
           } else {
               // respond to user's permission asking
               respondToUserOnPermissionActions();
           }
        });

        // photo btns
        photoStoreBtns();

        camBtn = findViewById(R.id.camBtn);
        camBtn.setOnClickListener(view -> {
            Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(camera_intent,pic_id);
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Match the request 'pic id with requestCode
        if (requestCode == pic_id) {
            // BitMap is data structure of image file which store the image in memory
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            // Set the image in imageview for display
            imageView.setImageBitmap(photo);
            ogBmp=(BitmapDrawable)imageView.getDrawable();
        }
    }

    private void photoStoreBtns() {
        openPhotoStoreBtn = findViewById(R.id.addBtn);
        // request btn on btn click
        openPhotoStoreBtn.setOnClickListener(v -> storagePermissionLauncher.launch(storagePermission));

        savePhotoBtn = findViewById(R.id.saveBtn);
        savePhotoBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Are you sure ?")
                    .setMessage("Want to save the photo to the gallery")
                    .setPositiveButton("Save",((dialog, which) -> {
                        if (filteredBmp != null) {
                            savePhoto(filteredBmp);
                        }
                    }))
                    .setNegativeButton("Cancel",((dialog, which) -> {
                        dialog.dismiss();
                    }))
                    .show();
        });
    }

    private void savePhoto(Bitmap bitmap) {
        if (ContextCompat.checkSelfPermission(this,storagePermission) == PackageManager.PERMISSION_GRANTED) {

            ContentResolver contentResolver =  getContentResolver();
            if (isExternalStorageWritable()) {
                Uri photoCollectionUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    photoCollectionUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                } else {
                    photoCollectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                }

                @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String photoName = getResources().getString(R.string.app_name)+ "_" + timeStamp + ".jpg";

                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME,photoName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,"DCIM/"+getResources().getString(R.string.app_name));
                }

                Uri photoUri = contentResolver.insert(photoCollectionUri,contentValues);
                try {
                    OutputStream fos = contentResolver.openOutputStream(photoUri);
                    boolean isSaved = bitmap.compress(Bitmap.CompressFormat.JPEG,100,fos);
                    if (isSaved) {
                        Toast.makeText(this,"Successfully saved",Toast.LENGTH_SHORT).show();
                    }
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            storagePermissionLauncher.launch(storagePermission);
        }
    }

    private boolean isExternalStorageWritable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    private void getPhotos() {
        // photos
        List<Photo> photos = new ArrayList<>();
        // photo store collection uri
        Uri libraryUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            libraryUri= MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            libraryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        // projection
        String[] projection  = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        // sort order
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        // querying
        try(Cursor cursor = getContentResolver().query(libraryUri,projection,null,null,sortOrder)) {
            // cache the cursor indices
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
            int bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

            // get the values
            while (cursor.moveToNext()) {
                // get values of columns of a give photo
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                String date = cursor.getString(dateColumn);
                int size = cursor.getInt(sizeColumn);
                long bucketId = cursor.getLong(bucketIdColumn);
                String bucketName = cursor.getString(bucketNameColumn);

                // photo uri
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id);

                // remove a .png extension on photo name
                name = name.substring(0,name.lastIndexOf("."));

                // photo item
                Photo photo = new Photo(id,String.valueOf(uri),name,date,size,bucketId,bucketName);

                photos.add(photo);
            }
        }

        showPhotos(photos);
    }

    private void showPhotos(List<Photo> photos) {
        if (photos.size()>0) {
            PhotoBottomSheetFragment photoBottomSheetFragment = new PhotoBottomSheetFragment(photos);
            photoBottomSheetFragment.show(getSupportFragmentManager(), photoBottomSheetFragment.getTag());
        } else {
            Toast.makeText(this,"No photos available. Add some",Toast.LENGTH_SHORT).show();
        }
    }

    private void respondToUserOnPermissionActions() {
        // user response
        if (ContextCompat.checkSelfPermission(this,storagePermission) == PackageManager.PERMISSION_GRANTED) {
            // permission granted
            getPhotos();
        }  else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(storagePermission)) {
                // show ui to user, use alert dialog
                new AlertDialog.Builder(this)
                        .setTitle("Requesting Permission")
                        .setMessage("Allow us to show & save photos")
                        .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // request permission again
                                storagePermissionLauncher.launch(storagePermission);
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(MainActivity.this,"You denied to show & save photos",Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }
                        }).show();
            }
        }
    }

    private void initializeViews() {
        toolsLayout = findViewById(R.id.horizontalScrollView);
        // filter layout
        filterBtn = findViewById(R.id.filterBtn);
        filterBtnsLayout = findViewById(R.id.filterBtnsLayout);
        filterBackBtn = findViewById(R.id.filterBackBtn);
        // brightness view
        brightnessBtn = findViewById(R.id.brightnessBtn);
        brightnessSeekerBar = findViewById(R.id.brightnessSeekBar);
        brightnessSeekBarLayout = findViewById(R.id.brightnessSeekBarLayout);
        brightnessSeekBarOkView = findViewById(R.id.brightnessSeekBarOkView);
        // contrast view
        contrastBtn = findViewById(R.id.contrastBtn);
        contrastSeekBarLayout = findViewById(R.id.contrastSeekBarLayout);
        contrastSeekerBar = findViewById(R.id.contrastSeekBar);
        contrastSeekBarOkView = findViewById(R.id.contrastSeekBarOkView);


        // view visibility
        filterBtn.setOnClickListener(view -> {
            toolsLayout.setVisibility(View.GONE);
            filterBtnsLayout.setVisibility(View.VISIBLE);
        });

        filterBackBtn.setOnClickListener(view -> {
            toolsLayout.setVisibility(View.VISIBLE);
            filterBtnsLayout.setVisibility(View.GONE);
        });

        brightnessBtn.setOnClickListener(view -> {
            brightnessSeekBarLayout.setVisibility(View.VISIBLE);
            toolsLayout.setVisibility(View.GONE);
        });
        contrastBtn.setOnClickListener(view -> {
            contrastSeekBarLayout.setVisibility(View.VISIBLE);
            toolsLayout.setVisibility(View.GONE);
        });

        brightnessSeekBarOkView.setOnClickListener(view -> {
            brightnessSeekBarLayout.setVisibility(View.GONE);
            toolsLayout.setVisibility(View.VISIBLE);
        });
        contrastSeekBarOkView.setOnClickListener(view -> {
            contrastSeekBarLayout.setVisibility(View.GONE);
            toolsLayout.setVisibility(View.VISIBLE);
        });

        // filters
        filters();
        // seek bar listener ( brightness and contrast)
        seekBarListener();
    }

    private void seekBarListener() {
        // brightness seek bar
        brightnessSeekerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                adjustBrightness(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // contrast seek bar
        contrastSeekerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                adjustContrast(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

        private void adjustContrast(int value) {
            // bitmap from original bitmap drawable
            Bitmap bmp = ogBmp.getBitmap();
            if (filteredBmp != null) {
                bmp = filteredBmp;
            }

            // define and add
            String initialHex = Tool.hexScale()[value];
            String initialMul = "0X" + initialHex + initialHex + initialHex;
            int add = 0X000000;
            int mul = Integer.decode(initialMul);


            // generate an output bitmap from the above bitmap
            Bitmap outputBitmap = Bitmap.createScaledBitmap(bmp,bmp.getWidth(),bmp.getHeight(),false).copy(Bitmap.Config.ARGB_8888,true);
            // paint
            Paint paint = new Paint();
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // canvas
            Canvas  canvas = new Canvas(outputBitmap);
            canvas.drawBitmap(outputBitmap,0,0,paint);

            // set the output bitmap to image view
            imageView.setImageBitmap(outputBitmap);
        }

    private void adjustBrightness(int value) {
        // bitmap from original bitmap drawable
        Bitmap bmp = ogBmp.getBitmap();
        if (filteredBmp != null) {
            bmp = filteredBmp;
        }

        // define a mul
        final int mul = 0XFFFFFF; // mul value must be 255

        // define and add
        String initialHex = Tool.hexScale()[value];
        String initialAdd = "0X" + initialHex + initialHex + initialHex;
        int add = Integer.decode(initialAdd);

        // generate an output bitmap from the above bitmap
        Bitmap outputBitmap = Bitmap.createScaledBitmap(bmp,bmp.getWidth(),bmp.getHeight(),false).copy(Bitmap.Config.ARGB_8888,true);
        // paint
        Paint paint = new Paint();
        ColorFilter colorFilter = new LightingColorFilter(mul,add);
        paint.setColorFilter(colorFilter);

        // canvas
        Canvas  canvas = new Canvas(outputBitmap);
        canvas.drawBitmap(outputBitmap,0,0,paint);

        // set the output bitmap to image view
        imageView.setImageBitmap(outputBitmap);
    }

    private void filters() {
        // grey btn
        ImageView greyBtn = findViewById(R.id.greyBtn);
        // filter btn
        filterBtn(greyBtn,Filter.grey);
        // on click listener
        greyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter(Filter.grey);
            }
        });

        // reset  btn/ no filter btn
        ImageView ogPhotoBtn = findViewById(R.id.filterOgBtn);
        ogPhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetFilter();
            }
        });

        // red btn
        ImageView redBtn = findViewById(R.id.redBtn);
        filterBtn(redBtn,Filter.red);
        redBtn.setOnClickListener(v -> filter(Filter.red));

        // green btn
        ImageView greenBtn = findViewById(R.id.greenBtn);
        filterBtn(greenBtn,Filter.green);
        greenBtn.setOnClickListener(v -> filter(Filter.green));

        // blue btn
        ImageView blueBtn = findViewById(R.id.blueBtn);
        filterBtn(blueBtn,Filter.blue);
        blueBtn.setOnClickListener(v -> filter(Filter.blue));

        // red green btn
        ImageView redGreenBtn = findViewById(R.id.redGreenBtn);
        filterBtn(redGreenBtn,Filter.redGreen);
        redGreenBtn.setOnClickListener(v -> filter(Filter.redGreen));

        // red blue btn
        ImageView redBlueBtn = findViewById(R.id.redBlueBtn);
        filterBtn(redBlueBtn,Filter.redBlue);
        redBlueBtn.setOnClickListener(v -> filter(Filter.redBlue));

        // green blue btn
        ImageView greenBlueBtn = findViewById(R.id.greenBlueBtn);
        filterBtn(greenBlueBtn,Filter.greenBlue);
        greenBlueBtn.setOnClickListener(v -> filter(Filter.greenBlue));

        // sepia btn
        ImageView sepiaBtn = findViewById(R.id.sepiaBtn);
        filterBtn(sepiaBtn,Filter.sepia);
        sepiaBtn.setOnClickListener(v -> filter(Filter.sepia));

        // binary btn
        ImageView binaryBtn = findViewById(R.id.binaryBtn);
        filterBtn(binaryBtn,Filter.binary);
        binaryBtn.setOnClickListener(v -> filter(Filter.binary));

        // invert btn
        ImageView invertBtn = findViewById(R.id.invertBtn);
        filterBtn(invertBtn,Filter.invert);
        invertBtn.setOnClickListener(v -> filter(Filter.invert));
    }

    private void resetFilter() {
        // no filter
        filteredBmp = null;
        filtered = null;

        // set original photo to image view
        imageView.setImageDrawable(ogBmp);

        // reset seek bars
        brightnessSeekerBar.setProgress(0);
        contrastSeekerBar.setProgress(255);
    }

    private void filter(String filter) {
        // create a bitmap from our original bitmap drawable
        Bitmap bmp = ogBmp.getBitmap();
        // generate an output bitmap from the above bitmap
        Bitmap outputBitmap = Bitmap.createScaledBitmap(bmp,bmp.getWidth(),bmp.getHeight(),false).copy(Bitmap.Config.ARGB_8888,true);
        // define a paint for styling and coloring bitmaps
        Paint paint = new Paint();
        // canvas to draw our bitmap
        Canvas canvas = new Canvas(outputBitmap);

        // filtering the photo to grey scale
        if (filter.equalsIgnoreCase(Filter.grey)) {
            // color matrix to filter to grey scale
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            // draw our bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }

        //filter photo to red filter
        if (filter.equalsIgnoreCase(Filter.red)) {
            final int mul = 0XFF0000; // RGB max red and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to green filter
        if (filter.equalsIgnoreCase(Filter.green)) {
            final int mul = 0X00FF00; // RGB max green and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to blue filter
        if (filter.equalsIgnoreCase(Filter.blue)) {
            final int mul = 0X0000FF; // RGB max blue and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to red green filter
        if (filter.equalsIgnoreCase(Filter.redGreen)) {
            final int mul = 0XFFFF00; // RGB max red green and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to red blue filter
        if (filter.equalsIgnoreCase(Filter.redBlue)) {
            final int mul = 0XFF00FF; // RGB max red blue and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        //filter photo to green blue filter
        if (filter.equalsIgnoreCase(Filter.greenBlue)) {
            final int mul = 0X00FFFF; // RGB max green blue and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to sepia filter
        if (filter.equalsIgnoreCase(Filter.sepia)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);

            // color scale
            ColorMatrix colorScale = new ColorMatrix();
            colorScale.setScale(1,1,0.8f,1);
            // convert color matrix to gray scale them apply the brown color
            colorMatrix.postConcat(colorScale);
            // color matrix filter
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to binary
        if (filter.equalsIgnoreCase(Filter.binary)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);

            // binary threshold
            float m = 255f;
            float t = -255*128f;
            ColorMatrix threshold = new ColorMatrix(new float[] {
                    m,0,0,1,t,
                    0,m,0,1,t,
                    0,0,m,1,t,
                    0,0,0,1,0
            });
            // convert the color matrix to grey scale, scale and clamp
            colorMatrix.postConcat(threshold);
            // color filter
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter photo to invert
        if (filter.equalsIgnoreCase(Filter.invert)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            colorMatrix.set(new float[] {
                    -1,0,0,0,255,
                    0,-1,0,0,255,
                    0,0,-1,0,255,
                    0,0,0,1,0
            });
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            canvas.drawBitmap(outputBitmap,0,0,paint);
        }

        // set the output bitmap to image view
        imageView.setImageBitmap(outputBitmap);

        // save filtered resources
        filteredBmp = outputBitmap;
        filtered = filter;
    }

    private void filterBtn(ImageView btn, String filter) {
        // get bitmap drawable from the image view btn
        BitmapDrawable dBmp= (BitmapDrawable) btn.getDrawable();

        // get bit map from above drawable bitmap
        Bitmap bmp = dBmp.getBitmap();
        // generate an output bitmap from the above bitmap
        Bitmap outputBitmap = Bitmap.createScaledBitmap(bmp,bmp.getWidth(),bmp.getHeight(),false).copy(Bitmap.Config.ARGB_8888,true);
        // define a paint for styling and coloring bitmaps
        Paint paint = new Paint();
        // canvas to draw our bitmap
        Canvas canvas = new Canvas(outputBitmap);

        // filtering the photo to grey scale
        if (filter.equalsIgnoreCase(Filter.grey)) {
            // color matrix to filter to grey scale
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            // draw our bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter red btn image to red filter
        if (filter.equalsIgnoreCase(Filter.red)) {
            final int mul = 0XFF0000; // RGB max red and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);
            // draw our bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter green btn image to green filter
        if (filter.equalsIgnoreCase(Filter.green)) {
            final int mul = 0X00FF00; // RGB max green and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        //filter blue btn image to blue filter
        if (filter.equalsIgnoreCase(Filter.blue)) {
            final int mul = 0X0000FF; // RGB max blue and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter red green btn image to red green filter
        if (filter.equalsIgnoreCase(Filter.redGreen)) {
            final int mul = 0XFFFF00; // RGB max red green and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter red blue btn image to red blue filter
        if (filter.equalsIgnoreCase(Filter.redBlue)) {
            final int mul = 0XFF00FF; // RGB max red blue and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter green blue btn image to green blue filter
        if (filter.equalsIgnoreCase(Filter.greenBlue)) {
            final int mul = 0X00FFFF; // RGB max green blue and other 0
            final int add = 0X000000;
            ColorFilter colorFilter = new LightingColorFilter(mul,add);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter sepia btn image to sepia filter
        if (filter.equalsIgnoreCase(Filter.sepia)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);

            // color scale
            ColorMatrix colorScale = new ColorMatrix();
            colorScale.setScale(1,1,0.8f,1);
            // convert color matrix to gray scale them apply the brown color
            colorMatrix.postConcat(colorScale);
            // color matrix filter
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            // draw the bitmap
            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter binary btn image to binary filter
        if (filter.equalsIgnoreCase(Filter.binary)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);

            // binary threshold
            float m = 255f;
            float t = -255*128f;
            ColorMatrix threshold = new ColorMatrix(new float[] {
                    m,0,0,1,t,
                    0,m,0,1,t,
                    0,0,m,1,t,
                    0,0,0,1,0
            });
            // convert the color matrix to grey scale, scale and clamp
            colorMatrix.postConcat(threshold);
            // color filter
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            canvas.drawBitmap(outputBitmap,0,0,paint);
        }
        // filter invert btn image to invert filter
        if (filter.equalsIgnoreCase(Filter.invert)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            colorMatrix.set(new float[] {
                    -1,0,0,0,255,
                    0,-1,0,0,255,
                    0,0,-1,0,255,
                    0,0,0,1,0
            });
            ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorFilter);

            canvas.drawBitmap(outputBitmap,0,0,paint);
        }

        // set the output bitmap to image view btn
        btn.setImageBitmap(outputBitmap);
    }
}