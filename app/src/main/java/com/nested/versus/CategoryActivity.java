package com.nested.versus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class CategoryActivity extends AppCompatActivity implements HorizontalScroll.ScrollViewListener, VerticalScroll.ScrollViewListener {

    DatabaseHelper mDatabaseHelper;

    static final int REQUEST_TAKE_PHOTO = 1;
    static final int GALLERY_REQUEST_CODE = 123;
    static final int CAMERA_PERM_CODE = 101;
    static final int CAMERA_REQUEST_CODE = 102;

    private boolean zoomOut = false;
    private String category_Name,
                   currentPhotoPath,
                   currentPhotoID;
    private int image_Taken_ID;
    private Button btn_AddItem,
                btn_AddCategory;
    private ImageView image_Taken;
    private TableLayout table_Categories,
                        table_Items;
    private TableRow table_ItemNames,
                     table_ItemImages;
    private ArrayList<VersusItem> itemList;

    private HorizontalScroll itemName_Horizontal,
                             itemValue_Horizontal,
                             itemImage_Horizontal;

    private VerticalScroll itemCategory_Vertical,
                           itemValue_Vertical;

    private android.widget.TableRow.LayoutParams layoutParams_ForItems;



    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        // SQLite Database
        mDatabaseHelper = new DatabaseHelper(this);

        // Set up current category name
        Intent intent = getIntent();
        category_Name = intent.getStringExtra("category_Name");
        TextView categoryNameTextView = findViewById(R.id.categoryNameTextView);
        categoryNameTextView.setText(category_Name);

        // Setup double scrollViews
        setupScrolling();
        itemImage_Horizontal.setScrollViewListener(this);
        itemImage_Horizontal.setHorizontalScrollBarEnabled(false);
        itemName_Horizontal.setScrollViewListener(this);
        itemValue_Horizontal.setScrollViewListener(this);
        itemValue_Horizontal.setHorizontalScrollBarEnabled(false);
        itemValue_Vertical.setScrollViewListener(this);
        itemValue_Vertical.setVerticalScrollBarEnabled(false);
        itemCategory_Vertical.setScrollViewListener(this);

        // Set Tables up
        setTables();

        // Set up add buttons
        setButtons();

        // Set up imageView for itemImage will be taken
        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString("Current_Photo_Path");
            currentPhotoID = savedInstanceState.getString("Current_Photo_ID");
        } else {
            image_Taken = null;
            currentPhotoID = "1";
            image_Taken_ID = -1;
        }

        // Load or initialize itemList and Place all items in the UI
        layoutParams_ForItems = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        layoutParams_ForItems.setMargins(10,10,10,10);
        init_ItemList(intent.getStringExtra("category_Name"));
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        currentPhotoPath = savedInstanceState.getString("Current_Photo_Path");
        currentPhotoID = savedInstanceState.getString("Current_Photo_ID");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        savedInstanceState.putString("Current_Photo_Path", currentPhotoPath);
        savedInstanceState.putString("Current_Photo_ID", currentPhotoID);
        super.onSaveInstanceState(savedInstanceState);
    }

    /*
                Method for saving image data into DB and set the UI
                 */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Choosing "Take Photo"
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                File f = new File(currentPhotoPath);

                // Store the picture's file path into DB
                InputStream iStream = null;
                byte[] imageData = null;
                try {
                    iStream = getContentResolver().openInputStream(Uri.fromFile(f));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                try {
                    imageData = getBytes(iStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Store image data into DB
                if (imageData != null) {
                    mDatabaseHelper.updateImage(category_Name + "_Image", currentPhotoID, Uri.fromFile(f).toString());
                }

                // Upload the pic on UI
                Bitmap bitmap = null;
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(CategoryActivity.this.getContentResolver(), Uri.fromFile(f));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                bitmap = rotateBitmap(bitmap, f);
                image_Taken.setImageBitmap(bitmap);
//                image_Taken.setRotation(90);
            }
        }

        // Choosing "Choose From Gallery"
        if (requestCode == GALLERY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                // Update image data in DB
                mDatabaseHelper.updateImage(category_Name + "_Image", currentPhotoID, data.getData().toString());

                // Update image in UI
                Bitmap bitmap = null;
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(CategoryActivity.this.getContentResolver(), data.getData());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                image_Taken.setImageBitmap(bitmap);
            }
        }
    }

    /*
    Method for requesting permission for taking picture
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERM_CODE) {
            if (grantResults.length < 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(CategoryActivity.this, "Camera Permission is Required to Use Camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onScrollChanged(HorizontalScroll scrollView, int x, int y, int oldx, int oldy) {
        if (scrollView == itemName_Horizontal) {
            itemImage_Horizontal.scrollTo(x, y);
            itemValue_Horizontal.scrollTo(x, y);
        } else if (scrollView == itemValue_Horizontal) {
            itemImage_Horizontal.scrollTo(x, y);
            itemName_Horizontal.scrollTo(x, y);
        } else if (scrollView == itemImage_Horizontal) {
            itemName_Horizontal.scrollTo(x, y);
            itemValue_Horizontal.scrollTo(x, y);
        }
    }

    @Override
    public void onScrollChanged(VerticalScroll scrollView, int x, int y, int oldx, int oldy) {
        if (scrollView == itemCategory_Vertical) {
            itemValue_Vertical.scrollTo(x, y);
        } else if (scrollView == itemValue_Vertical) {
            itemCategory_Vertical.scrollTo(x, y);
        }
    }

    /*
    Function for initializing item list and placing them onto UI
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void init_ItemList(final String category_Name) {

        itemList = new ArrayList<>();

        // Load itemList
        Cursor data = mDatabaseHelper.getData(category_Name);

        // If no data exists
        if (data.getCount() == 0) {

            // Add new item into the database
            mDatabaseHelper.addData(category_Name, "New Item");
        }

        // Set up itemList and Place all item names onto UI
        data = mDatabaseHelper.getData(category_Name);
        while (data.moveToNext()) {
            if (data.getString(1) != null) {

                String data_ID = data.getString(0);
                String data_Name = data.getString(1);

                // Place the item name onto UI
                TextView nameView = null;
                Cursor imageData = mDatabaseHelper.getData(category_Name + "_Image", data_ID);
                imageData.moveToNext();
                nameView = createTextViewForItemName(data_ID, data_Name, imageData.getString(1), table_ItemNames);
                table_ItemNames.addView(nameView, layoutParams_ForItems);

                // Instantiate each VersusItem
                VersusItem savedItem = new VersusItem(data_ID, data_Name);
                for (int i = 2; i < data.getColumnCount(); i++) {
                    String data_Category = data.getColumnName(i);
                    String data_Value = data.getString(i);

                    // Place item categories first if it's first time to add
                    if (data.getPosition() == 0) {
                        TableRow categoryView = createTableRowForItemCategory(data_Category);
                        table_Categories.addView(categoryView);
                    }

                    if (data.getPosition() == 0) {
                        // For ItemValues
                        // Create and add new TableRow into table_ItemValues, and Place each itemValue into the tableRow
                        TableRow newTableRow = new TableRow(CategoryActivity.this);
                        TextView valueTextView = createTextViewForItemValue(data_ID, data_Value, newTableRow);
                        newTableRow.addView(valueTextView, layoutParams_ForItems);
                        table_Items.addView(newTableRow);
                    } else {
                        // For ItemValues (Not first items)
                        TableRow currentRow = (TableRow) table_Items.getChildAt(i - 2);
                        TextView newValueTextView = createTextViewForItemValue(data_ID, data_Value, currentRow);

                        currentRow.addView(newValueTextView, layoutParams_ForItems);
                    }

                    // Update the VersusItem
                    savedItem.getItemValues().add(data.getString(i));
                }

                // Push the item to the item list
                itemList.add(savedItem);

                imageData.close();
            }
        }

        data.close();
    }

    /*
    Method for double ScrollViews
     */
    private void setupScrolling() {

        itemImage_Horizontal = findViewById(R.id.itemImage_Horizontal);
        itemName_Horizontal = findViewById(R.id.itemName_Horizontal);
        itemValue_Horizontal = findViewById(R.id.itemValue_Horizontal);
        itemCategory_Vertical = findViewById(R.id.itemCategory_Vertical);
        itemValue_Vertical = findViewById(R.id.itemValue_Vertical);

    }

    /*
    Function for setting up tables
     */
    private void setTables() {
        table_Categories = findViewById(R.id.table_row);
        table_Items = findViewById(R.id.table_items);
        table_ItemNames = findViewById(R.id.table_col);
        table_ItemImages = findViewById(R.id.table_images);
    }
    
    /*
    Function for setting up buttons' on click listeners
     */
    private void setButtons() {

        btn_AddItem = findViewById(R.id.image_Add_Col);
        btn_AddItem.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onClick(View v) {
                // 1. into DB (itemName and itemImg)
                Cursor data = mDatabaseHelper.getData(category_Name);
                mDatabaseHelper.addItemData(category_Name, "New Item", "New Value");
                mDatabaseHelper.insertImage(category_Name + "_Image", null);

                // 2. into UI (itemName and itemImg)
                data.moveToLast();
                String newId = data.getString(0);
                TextView newItemTextView = createTextViewForItemName(newId, "New Item", null, table_ItemNames);
                table_ItemNames.addView(newItemTextView, layoutParams_ForItems);
                for (int i = 2; i < data.getColumnCount(); i++) {

                    // Add a cell on each row
                    TableRow currTableRow = (TableRow) table_Items.getChildAt(i - 2);
                    TextView newValueTextView = createTextViewForItemValue(newId, "New Value", currTableRow);
                    currTableRow.addView(newValueTextView, layoutParams_ForItems);
                }

                // 3. into itemList
                VersusItem newItem = new VersusItem(String.valueOf(Integer.parseInt(newId)), "New Item");
                for (int i = 2; i < data.getColumnCount(); i++) {
                    newItem.getItemValues().add("New Value");
                }
                itemList.add(newItem);

                data.close();
            }
        });

        btn_AddCategory = findViewById(R.id.image_Add_Row);
        btn_AddCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 1. into DB
                Cursor count_Data = mDatabaseHelper.getData(category_Name + "_Count");
                count_Data.moveToNext();
                String newCategoryName = "Category" + (Integer.parseInt(count_Data.getString(1)) + 1);
                mDatabaseHelper.updateData(category_Name + "_Count", "1", "Count", String.valueOf(Integer.parseInt(count_Data.getString(1)) + 1));
                if (mDatabaseHelper.addColumn(category_Name, newCategoryName)) {

                    // 2. into UI
                    // New Category
                    Cursor data = mDatabaseHelper.getData(category_Name);
                    data.moveToFirst();
                    TableRow newTableRow = createTableRowForItemCategory(newCategoryName);
                    table_Categories.addView(newTableRow, layoutParams_ForItems);

                    // New Values
                    TableRow newValueTableRow = new TableRow(CategoryActivity.this);
                    newValueTableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
                    for (int i = 0; i < itemList.size(); i++) {
                        TextView newValueTextView = createTextViewForItemValue(itemList.get(i).getItemID(), "New Value", newValueTableRow);
                        newValueTableRow.addView(newValueTextView, layoutParams_ForItems);
                    }
                    table_Items.addView(newValueTableRow);

                    // 3. into itemList
                    for (int i = 0; i < itemList.size(); i++) {
                        itemList.get(i).getItemValues().add("New Value");
                    }

                    data.close();
                } else {

                    // If newCategoryName is duplicate, decrement count again
                    mDatabaseHelper.updateData(category_Name + "_Count", "1", "Count", String.valueOf(Integer.parseInt(count_Data.getString(1)) - 1));

                    // Show fail message
                    AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                    newDialog.setTitle("The category ALREADY EXISTS!");

                    newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

                    newDialog.show();
                }

                count_Data.close();
            }
        });
    }

    /*
    Function for creating TextView for item name
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private TextView createTextViewForItemName(final String id, String itemName, final String imageURI, final TableRow tableRow) {

        // ItemImage ImageView
        final TextView newItemTextView = new TextView(CategoryActivity.this);
        final float radius = getResources().getDimension(R.dimen.five_dp);
        final ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, radius).build();
        final MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeAppearanceModel);
        final ImageView newItemImageView = new ImageView(CategoryActivity.this);
        newItemImageView.setForegroundGravity(Gravity.CENTER);

        if (imageURI == null) {
            newItemImageView.setImageResource(R.drawable.ic_item_image);
        } else {

            // Set imageView with filePath
            Uri imageUri = Uri.parse(imageURI);
            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(CategoryActivity.this.getContentResolver(), imageUri);
                bitmap = rotateBitmap(bitmap, new File(imageUri.getPath()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            newItemImageView.setImageBitmap(bitmap);
        }
        newItemImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Taking a picture and store & place the photo onto UI and DB
                image_Taken = newItemImageView;
                currentPhotoID = id;

                AlertDialog.Builder builder = new AlertDialog.Builder(CategoryActivity.this);
                builder.setTitle(newItemTextView.getText().toString() + " \n");
                String[] menu = {"View Photo", "Take Photo", "Choose From Gallery", "Cancel"};
                builder.setItems(menu, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: // View Photo
                                Intent intent = new Intent(CategoryActivity.this, ItemImageActivity.class);

                                Cursor imageData = mDatabaseHelper.getData(category_Name + "_Image", id);
                                imageData.moveToNext();
                                intent.putExtra("imageURI", imageData.getString(1));

                                imageData.close();

                                startActivity(intent);
                                break;

                            case 1: // Take Photo

                                // Ask camera permission
                                if (ContextCompat.checkSelfPermission(CategoryActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(CategoryActivity.this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERM_CODE);
                                    if (ContextCompat.checkSelfPermission(CategoryActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        dispatchTakePictureIntent();
                                    }
                                } else {
                                    dispatchTakePictureIntent();
                                }
                                break;

                            case 2: // Choose From Gallery
                                Intent gallery = new Intent(Intent.ACTION_OPEN_DOCUMENT, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                startActivityForResult(gallery, GALLERY_REQUEST_CODE);
                                break;

                            case 3: // Cancel
                                dialog.dismiss();
                                break;
                        }
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
        table_ItemImages.addView(newItemImageView, layoutParams_ForItems);
        newItemImageView.getLayoutParams().width = 300;
        newItemImageView.getLayoutParams().height = 150;

        // ItemName TextView
        newItemTextView.setText(itemName);
        newItemTextView.setGravity(Gravity.CENTER);
        newItemTextView.setTextColor(getResources().getColor(R.color.colorBlack));
        newItemTextView.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
        newItemTextView.setWidth(300);
        newItemTextView.setHeight(100);

        shapeDrawable.setFillColor(ContextCompat.getColorStateList(this, R.color.colorGray));
        shapeDrawable.setStroke(1.5f, ContextCompat.getColor(this, R.color.colorGray));
        ViewCompat.setBackground(newItemTextView, shapeDrawable);

        //ItemName Editing Event
        newItemTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(CategoryActivity.this).create();
                final EditText itemNameEditText = new EditText(CategoryActivity.this);
                itemNameEditText.setText(newItemTextView.getText().toString());

                dialog.setTitle("Edit Item Name: \n");
                dialog.setView(itemNameEditText);

                dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String newItemName = itemNameEditText.getText().toString();

                        if (newItemName.equals("")) {
                            // Show fail message
                            AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                            newDialog.setTitle("Item name must be at least one character.");

                            newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                            newDialog.show();
                        } else {
                            // Change on database
                            mDatabaseHelper.updateData(category_Name, id, "name", newItemName);

                            // Change on itemList
                            for (int i = 0; i < itemList.size(); i++) {
                                if (itemList.get(i).getItemID().equals(id)) {
                                    itemList.get(i).setItemName(newItemName);
                                    break;
                                }
                            }

                            // Change on UI
                            newItemTextView.setText(newItemName);
                        }
                    }
                });

                dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        // Delete data only if there are more than one data
                        Cursor database = mDatabaseHelper.getData(category_Name);
                        if (database.getCount() > 1) {

                            // Delete itemName & itemImage from the DB
                            mDatabaseHelper.deleteData(category_Name, id);
                            mDatabaseHelper.deleteData(category_Name + "_Image", id);

                            // Delete item from UI
                            int indexOfItemName = tableRow.indexOfChild(newItemTextView);
                            tableRow.removeView(newItemTextView);
                            for (int i = 0; i < itemList.get(0).getItemValueSize(); i++) {
                                TableRow curr_Row = (TableRow) table_Items.getChildAt(i);
                                curr_Row.removeView(curr_Row.getChildAt(indexOfItemName));
                            }
                            table_ItemImages.removeView(newItemImageView);

                            // Delete item from itemList
                            itemList.remove(indexOfItemName);

                        } else if (database.getCount() <= 1) {

                            // Show fail message
                            AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                            newDialog.setTitle("Can't delete all items!");

                            newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                            newDialog.show();
                        }

                        database.close();
                    }
                });

                // Disable Delete button when there is only one item name
                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        Cursor database = mDatabaseHelper.getData(category_Name);
                        if (database.getCount() <= 1) {
                            ((AlertDialog)dialog).getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                        }
                    }
                });

                dialog.show();
            }
        });

        return newItemTextView;
    }

    /*
    Function for creating TableRow for item category
     */
    private TableRow createTableRowForItemCategory(final String categoryName) {

        final TableRow newTableRow = new TableRow(CategoryActivity.this);
        newTableRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        final TextView newTextView = new TextView(CategoryActivity.this);
        newTextView.setGravity(Gravity.CENTER);
        newTextView.setLayoutParams(new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        newTextView.setText(categoryName);
        newTextView.setHeight(100);
        newTextView.setWidth(150);
        newTextView.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));

        final float radius = getResources().getDimension(R.dimen.five_dp);
        final ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, radius).build();
        final MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeAppearanceModel);
        shapeDrawable.setFillColor(ContextCompat.getColorStateList(this, R.color.colorGray));
        shapeDrawable.setStroke(1.5f, ContextCompat.getColor(this, R.color.colorGray));
        ViewCompat.setBackground(newTextView, shapeDrawable);

        newTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(CategoryActivity.this).create();
                final EditText itemCategoryEditText = new EditText(CategoryActivity.this);
                itemCategoryEditText.setText(newTextView.getText().toString());

                dialog.setTitle("Edit Item Category: \n");
                dialog.setView(itemCategoryEditText);

                dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newItemCategory = itemCategoryEditText.getText().toString();

                        if (newItemCategory.equals("")) {
                            // Show fail message
                            AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                            newDialog.setTitle("Item category must be at least one character.");

                            newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                            newDialog.show();
                        } else {
                            // Change on DB
                            String oldItemCategory = newTextView.getText().toString();
                            if (mDatabaseHelper.renameColumn(category_Name, oldItemCategory, newItemCategory)) {

                                // Change on UI
                                newTextView.setGravity(Gravity.CENTER);
                                newTextView.setText(newItemCategory);

                                // Updating the database
                                System.out.println("============= Database ===============");
                                Cursor test_Data2 = mDatabaseHelper.getData(category_Name);
                                while(test_Data2.moveToNext()) {
                                    System.out.print(test_Data2.getString(0) + ". " + test_Data2.getString(1) + ", ");
                                    for (int i = 2; i < test_Data2.getColumnCount(); i++) {
                                        System.out.print(test_Data2.getColumnName(i) + ": " + test_Data2.getString(i) + ", ");
                                    }
                                    System.out.println();
                                }
                                System.out.println("======================================");
                                test_Data2.close();
                            } else {

                                // Show fail message
                                AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                                newDialog.setTitle("The name already exists, OR can't contain special characters.");

                                newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });

                                newDialog.show();
                            }
                        }
                    }
                });

                dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        // Delete category only if there are more than one category
                        Cursor database = mDatabaseHelper.getData(category_Name);
                        if (database.getColumnCount() > 3) {

                            // Delete itemCategory from the DB
                            String oldItemCategory = newTextView.getText().toString();
                            mDatabaseHelper.deleteColumn(category_Name, oldItemCategory);

                            // Delete itemName from UI
                            int indexOfCategory = table_Categories.indexOfChild(newTableRow);
                            table_Categories.removeView(newTableRow);
                            table_Items.removeView(table_Items.getChildAt(indexOfCategory));
                            for (int i = 0; i < itemList.size(); i++) {
                                itemList.get(i).getItemValues().remove(indexOfCategory);
                            }
                        } else if (database.getColumnCount() <= 3) {

                            // Show fail message
                            AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                            newDialog.setTitle("Can't delete all categories!");

                            newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                            newDialog.show();
                        }

                        // Updating the database
                        System.out.println("============= Database ===============");
                        Cursor test_Data2 = mDatabaseHelper.getData(category_Name);
                        while(test_Data2.moveToNext()) {
                            System.out.print(test_Data2.getString(0) + ". " + test_Data2.getString(1) + ", ");
                            for (int i = 2; i < test_Data2.getColumnCount(); i++) {
                                System.out.print(test_Data2.getColumnName(i) + ": " + test_Data2.getString(i) + ", ");
                            }
                            System.out.println();
                        }
                        System.out.println("======================================");
                        test_Data2.close();

                        database.close();
                    }
                });

                // Disable Delete button when there is only one category
                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        Cursor database = mDatabaseHelper.getData(category_Name);
                        if (database.getColumnCount() <= 3) {
                            ((AlertDialog)dialog).getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                        }

                        database.close();
                    }
                });

                dialog.show();
            }
        });

        newTableRow.addView(newTextView, layoutParams_ForItems);

        return newTableRow;
    }

    /*
    Method for creating TextView for item Values
     */
    private TextView createTextViewForItemValue(final String id, final String itemValue, final TableRow tableRow) {
        final TextView newItemValueTextView = new TextView(CategoryActivity.this);
        newItemValueTextView.setGravity(Gravity.CENTER);
        newItemValueTextView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        newItemValueTextView.setText(itemValue);
        newItemValueTextView.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
        newItemValueTextView.setWidth(1000);
        newItemValueTextView.setHeight(100);
        newItemValueTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(CategoryActivity.this).create();
                final EditText itemValueEditText = new EditText(CategoryActivity.this);
                itemValueEditText.setText(newItemValueTextView.getText().toString());

                dialog.setTitle("Edit Item Value: \n");
                dialog.setView(itemValueEditText);

                dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newItemValue = itemValueEditText.getText().toString();

                        if (newItemValue.equals("")) {
                            // Show fail message
                            AlertDialog newDialog = new AlertDialog.Builder(CategoryActivity.this).create();

                            newDialog.setTitle("Item value must be at least one character.");

                            newDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Confirm", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                            newDialog.show();
                        } else {
                            // Change on DB
                            int indexOfCategory = table_Items.indexOfChild(tableRow);
                            Cursor data = mDatabaseHelper.getData(category_Name);
                            mDatabaseHelper.updateData(category_Name, id, data.getColumnName(indexOfCategory + 2), newItemValue);

                            // Change on itemList
                            for (int i = 0; i < itemList.size(); i++) {
                                if (itemList.get(i).getItemID() == id) {
                                    itemList.get(i).setItemValue(indexOfCategory, newItemValue);
                                    break;
                                }
                            }

                            // Change on UI
                            newItemValueTextView.setText(newItemValue);

                            data.close();
                        }
                    }
                });

                dialog.show();
            }
        });

        return newItemValueTextView;
    }

    /*
    Method for creating image file name
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = imageFile.getAbsolutePath();
        return imageFile;
    }

    /*
    Method for set up for camera intent
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.nested.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    /*
    Method for converting uri to byte array
     */
    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        return byteBuffer.toByteArray();
    }

    /*
    Method for rotating a bitmap image with correct orientation
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Bitmap rotateBitmap(Bitmap imageBitmap, File imageFile) {

        // Get correct image's orientation
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(new FileInputStream(imageFile));
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            // Rotate Bitmap depending orientation
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    return imageBitmap;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(-90);
                    break;
                default:
                    return imageBitmap;
            }
            try {
                Bitmap bitmap_Rotated = Bitmap.createBitmap(imageBitmap, 0, 0, imageBitmap.getWidth(), imageBitmap.getHeight(), matrix, true);
                imageBitmap.recycle();
                return bitmap_Rotated;
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return imageBitmap;
        }
    }
}

/* TO CHECK DB AND ITEMLIST

        System.out.println("============= item List ==============");
        for (int i = 0; i < itemList.size(); i++) {
            System.out.print(itemList.get(i).getItemID() + ". " + itemList.get(i).getItemName() + ", ");
            for (int j = 0; j < itemList.get(i).getItemValues().size(); j++) {
                System.out.print(itemList.get(i).getItemValues().get(j) + ", ");
            }
            System.out.println();
        }
        System.out.println("======================================");

        System.out.println("============= Database ===============");
        Cursor test_Data2 = mDatabaseHelper.getData(category_Name);
        while(test_Data2.moveToNext()) {
            System.out.print(test_Data2.getString(0) + ". " + test_Data2.getString(1) + ", ");
            for (int i = 2; i < test_Data2.getColumnCount(); i++) {
                System.out.print(test_Data2.getColumnName(i) + ": " + test_Data2.getString(i) + ", ");
            }
            System.out.println();
        }
        System.out.println("======================================");
        test_Data2.close();

        System.out.println("============= Count Table ===============");
        Cursor test_Data3 = mDatabaseHelper.getData(category_Name + "_Count");
        test_Data3.moveToNext();
        System.out.println("Count is " + test_Data3.getString(1));
        System.out.println("======================================");
        test_Data3.close();

        System.out.println("============= Image Table ===============");
        Cursor test_Data4 = mDatabaseHelper.getData(category_Name + "_Image");
        while(test_Data4.moveToNext()) {
            System.out.print(test_Data4.getString(0) + ". " + test_Data4.getBlob(1) + ", ");
            System.out.println();
        }
        System.out.println("======================================");
        test_Data4.close();
 */