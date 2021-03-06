/*
 * Copyright (C) 2021 xLexip
 *
 * This program is free and open source software:
 * You can redistribute it and/or modify it under the terms of the
 * 'Mozilla Public License 2.0' as published by the Mozilla Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the mentioned license for more details.
 *
 * You should have received a copy of the Mozilla Public License 2.0
 * along with this program. If not, see <http://mozilla.org/MPL/2.0/>.
 *
 */

package dev.lexip.logcat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.res.ResourcesCompat;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private FirebaseAnalytics mFirebaseAnalytics;
    private ArrayList<String> rawlog;
    private ArrayList<Object[]> log;
    private String filterString;
    private ArrayList<ConstraintLayout> verboseEntries;
    private ArrayList<ConstraintLayout> debugEntries;
    private ArrayList<ConstraintLayout> infoEntries;
    private ArrayList<ConstraintLayout> warnEntries;
    private ArrayList<ConstraintLayout> errorEntries;
    private ArrayList<ConstraintLayout> fatalEntries;
    private ArrayList<ConstraintLayout> otherEntries;
    private boolean autoscroll;
    private FloatingActionButton floatingAutoscrollBtn;
    private LinearLayout ll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        log = new ArrayList<Object[]>();
        verboseEntries = new ArrayList<ConstraintLayout>();
        debugEntries = new ArrayList<ConstraintLayout>();
        infoEntries = new ArrayList<ConstraintLayout>();
        warnEntries = new ArrayList<ConstraintLayout>();
        errorEntries = new ArrayList<ConstraintLayout>();
        fatalEntries = new ArrayList<ConstraintLayout>();
        otherEntries = new ArrayList<ConstraintLayout>();
        filterString = "";

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Initialize autoscroll
        autoscroll = true;
        ScrollView sv = findViewById(R.id.scrollView);
        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {

                // Enable autoscroll when the scrollview is at the very bottom and show the floating activation button otherwise
                if ((((View) sv.getChildAt(sv.getChildCount() - 1)).getBottom() - (sv.getHeight() + sv.getScrollY())) < 300) {
                    autoscroll = true;
                    findViewById(R.id.floatingLayout).setVisibility(View.GONE);
                }
                else
                    findViewById(R.id.floatingLayout).setVisibility(View.VISIBLE);

                // Disable autoscroll as soon as the user scrolls up
                if(oldScrollY>scrollY) {
                    autoscroll= false;
                }
            }
        });

        // Start a separate thread that continuously reads the "logcat"
        new Thread() {
            public void run() {
                try {
                    rawlog = new ArrayList<String>();
                    Process process = Runtime.getRuntime().exec("logcat");
                    BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    while (true) {
                        String line = br.readLine();
                        if (line == null) {
                            Thread.sleep(500);
                        } else
                            rawlog.add(line);
                    }
                }
                catch (IOException|InterruptedException e) {
                    Log.e(this.getName(),"IOExeption while reading logcat");
                    e.printStackTrace();
                }
            }
        }.start();

        // Start a separate thread that processes all the logcat reports read by the previous thread with a slight cooldown
        new Thread() {
            public void run() {
                while (true) {
                    try { Thread.sleep(11); }
                    catch (InterruptedException ignored) {}
                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            if(!rawlog.isEmpty()) {
                                String line = rawlog.get(0);
                                addEntry(getEntryCategory(line), polishContent(line));
                                rawlog.remove(0);
                            }
                            else if(autoscroll)
                                ((ScrollView)findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        }.start();

        // Initialize floating button for autoscroll
        floatingAutoscrollBtn = findViewById(R.id.floatingAutoscrollBtn);
        floatingAutoscrollBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ScrollView)findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
                autoscroll = true;
                findViewById(R.id.floatingLayout).setVisibility(View.GONE);
            }
        });

        // Initialize filter stuff
        ((ImageView) findViewById(R.id.filterIcon)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if((findViewById(R.id.filterLayout).getVisibility() == View.VISIBLE)) {
                    applyFilter();
                }
                else
                    findViewById(R.id.filterLayout).setVisibility(View.VISIBLE);
            }
        });
        ((Button)findViewById(R.id.filterApplyBtn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFilter();
            }
        });
        ((EditText)findViewById(R.id.searchBar)).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    /**
     * Applies the filters in separate thread
     */
    private void applyFilter(){
        new Thread() {
            public void run() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        // Process Checkboxes
                        if (((CheckBox) findViewById(R.id.verboseCheck)).isChecked())
                            setTypeVisibility('V', View.VISIBLE);
                        else
                            setTypeVisibility('V', View.GONE);

                        if (((CheckBox) findViewById(R.id.debugCheck)).isChecked())
                            setTypeVisibility('D', View.VISIBLE);
                        else
                            setTypeVisibility('D', View.GONE);

                        if (((CheckBox) findViewById(R.id.infoCheck)).isChecked())
                            setTypeVisibility('I', View.VISIBLE);
                        else
                            setTypeVisibility('I', View.GONE);

                        if (((CheckBox) findViewById(R.id.warnCheck)).isChecked())
                            setTypeVisibility('W', View.VISIBLE);
                        else
                            setTypeVisibility('W', View.GONE);

                        if (((CheckBox) findViewById(R.id.errorCheck)).isChecked())
                            setTypeVisibility('E', View.VISIBLE);
                        else
                            setTypeVisibility('E', View.GONE);

                        if (((CheckBox) findViewById(R.id.fatalCheck)).isChecked())
                            setTypeVisibility('F', View.VISIBLE);
                        else
                            setTypeVisibility('F', View.GONE);
                        findViewById(R.id.filterLayout).setVisibility(View.GONE);

                        filterString = ((EditText)findViewById(R.id.searchBar)).getText().toString();
                        // Show/Hide the search bar
                        ConstraintSet cs = new ConstraintSet();
                        cs.clone((ConstraintLayout)findViewById(R.id.constraintLayout));
                        if(((Switch)findViewById(R.id.searchbarSwitch)).isChecked()) {
                            findViewById(R.id.searchBar).setVisibility(View.VISIBLE);
                            cs.connect(R.id.scrollView,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP,dpToPixel(82));
                        } else {
                            findViewById(R.id.searchBar).setVisibility(View.GONE);
                            cs.connect(R.id.scrollView,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP,dpToPixel(48));
                            filterString = "";
                        }
                        cs.applyTo((ConstraintLayout)findViewById(R.id.constraintLayout));

                        if(filterString.isEmpty())
                            return;

                        // Process search input
                        for(int i=0; i<log.size();i++){
                            if(log.get(i)[1].toString().replaceAll(" ","").contains(filterString))
                                ((ConstraintLayout)log.get(i)[0]).setVisibility(View.VISIBLE);
                            else
                                ((ConstraintLayout)log.get(i)[0]).setVisibility(View.GONE);
                        }
                    }
                });
            }
        }.start();
    }

    /**
     * Cleans up a logcat string by replacing annoying spaces and linebreaks
     * @param input String of one or more logcat report(s)
     * @return Returns the same but polished Logcat String
     */
    private String polishContent(String input){
        input = input.replace(": ",": \n");
        if(input.contains(": \n\tat"))
            input = input.replace(": \tat ",": at\n");
        while(input.contains("  "))
            input = input.replace("  "," ");
        input = input.replace(" :",":");
        input = input.replace("\n\t","\n");

        return input;
    }

    /**
     * Specifies the category of a string that contains one logcat report
     * @param input Logcat String
     * @return Returns the category of the logcat report like "Debug", "Error", etc.
     */
    private char getEntryCategory(String input) {
        // Filter out special entries like "--- beginning of main" etc.
        if (input.startsWith("-"))
            return 'X';

        input = input.substring(31);
        if(!input.isEmpty())
            return input.charAt(0);
        return 'X';
    }

    /**
     * Adds a passed logcat report to the UI by generating and adding all required components
     * @param type Category of the report (like "Debug" or "Error")
     * @param content One logcat string/report
     */
    private void addEntry(char type, String content) {
        LinearLayout ll = (LinearLayout) findViewById(R.id.linearLayout);

        // Create ConstraintLayout
        ConstraintLayout cl = new ConstraintLayout(this);
        cl.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT));
        cl.setId(ConstraintLayout.generateViewId());
        cl.setClickable(true);
        cl.setDefaultFocusHighlightEnabled(true);
        cl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cl.setBackgroundColor(ResourcesCompat.getColor(getResources(),R.color.selection,getTheme()));
            }
        });

        // Highlight special entries starting with "-" (e.g. "------- beginning of crash")
        if(content.startsWith("-"))
            cl.setBackgroundColor(ResourcesCompat.getColor(getResources(),R.color.selection,getTheme()));

        // Generate "Stroke"
        View stroke = new View(this);
        GradientDrawable shape =  new GradientDrawable();

            // Visually connect related entries by only rounding the corners at the very start and the very end of the report group (e.g. "*Exception.. ..at.. ..at.. ..at..") and removing redundant parts of the following reports
            try {
                if (content.contains("Exception") && rawlog.get(1).contains("\tat")) {
                    shape.setCornerRadii(new float[]{100, 100, 100, 100, 0, 0, 0, 0});
                }
                else if (content.contains(": \nat") && !rawlog.get(1).contains("\tat")) {
                    shape.setCornerRadii(new float[]{0, 0, 0, 0, 100, 100, 100, 100});
                    content = content.substring(content.indexOf("at"));
                }
                else if (content.contains(": \nat"))
                    content = content.substring(content.indexOf("at"));

                else if (!content.contains(": \nat"))
                    shape.setCornerRadii(new float[]{100, 100, 100, 100, 100, 100, 100, 100});
            } catch(IndexOutOfBoundsException e){
                if(content.contains(": \nat")) {
                    shape.setCornerRadii(new float[]{0, 0, 0, 0, 100, 100, 100, 100});
                    content = content.substring(content.indexOf("at"));
                }
                else
                    shape.setCornerRadii(new float[]{100, 100, 100, 100, 100, 100, 100, 100});
            }

        stroke.setBackground(shape);
        stroke.setId(View.generateViewId());
        stroke.setLayoutParams(new ViewGroup.MarginLayoutParams(dpToPixel(8), 0));
        cl.addView(stroke);
        switch (type) {  // Set the type-based stroke color, maintain the the corresponding arraylist and apply the filters
            case 'V': // Verbose
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.verbose, getTheme()));
                verboseEntries.add(cl);
                if(!((CheckBox)findViewById(R.id.verboseCheck)).isChecked())
                    cl.setVisibility(View.GONE);
                break;
            case 'D': // Debug
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.debug, getTheme()));
                debugEntries.add(cl);
                if(!((CheckBox)findViewById(R.id.debugCheck)).isChecked())
                    cl.setVisibility(View.GONE);
                break;
            case 'I': // Info
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.info, getTheme()));
                infoEntries.add(cl);
                if(!((CheckBox)findViewById(R.id.infoCheck)).isChecked())
                    cl.setVisibility(View.GONE);
                break;
            case 'W': // Warning
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.warning, getTheme()));
                warnEntries.add(cl);
                if(!((CheckBox)findViewById(R.id.warnCheck)).isChecked())
                    cl.setVisibility(View.GONE);
                break;
            case 'E': // Error
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.error, getTheme()));
                errorEntries.add(cl);
                if(!((CheckBox)findViewById(R.id.errorCheck)).isChecked())
                    cl.setVisibility(View.GONE);
                break;
            case 'F': // Fatal
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.fatal, getTheme()));
                fatalEntries.add(cl);
                if(!((CheckBox)findViewById(R.id.fatalCheck)).isChecked())
                    cl.setVisibility(View.GONE);
                break;
            default: // Others (e.g. "--- beginning of crash" reports)
                shape.setColor(ResourcesCompat.getColor(getResources(), R.color.fg, getTheme()));
                otherEntries.add(cl);
                break;
        }
        // Do not show new entries that do not match the filter string
        if(!filterString.isEmpty() && !content.contains(filterString))
            cl.setVisibility(View.GONE);

        // Generate TextView
        TextView tv = new TextView(this);
        tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setId(TextView.generateViewId());
        Typeface tf = ResourcesCompat.getFont(this, R.font.robotomono_regular);
        tv.setTextSize(12);
        tv.setTypeface(tf);
        tv.setText(content);
        tv.setLongClickable(true);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cl.setBackgroundColor(ResourcesCompat.getColor(getResources(),R.color.selection,getTheme()));
            }
        });
        cl.addView(tv);

        // Set constraint connections
        ConstraintSet cs = new ConstraintSet();
        cs.clone(cl);
        cs.connect(stroke.getId(),ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,dpToPixel(5));
        cs.connect(stroke.getId(),ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP,dpToPixel(3));
        cs.connect(stroke.getId(),ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM,dpToPixel(3));
        cs.connect(tv.getId(),ConstraintSet.RIGHT,stroke.getId(),ConstraintSet.RIGHT,dpToPixel(5));
        cs.connect(tv.getId(),ConstraintSet.LEFT,stroke.getId(),ConstraintSet.LEFT,dpToPixel(20));
        cs.connect(tv.getId(),ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP,dpToPixel(5));
        cs.connect(tv.getId(),ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM,dpToPixel(5));

        try {
            if (content.contains("Exception") && rawlog.get(1).contains("\tat"))
                cs.connect(stroke.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
            else if(content.startsWith("at") && rawlog.get(1).contains("\tat")) {
                cs.connect(stroke.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0);
                cs.connect(stroke.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
            }
            else if(content.startsWith("at"))
                cs.connect(stroke.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0);
        } catch(IndexOutOfBoundsException ignored) {
            if(content.startsWith("at"))
                cs.connect(stroke.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0);
        }

        cs.applyTo(cl);
        ll.addView(cl);

        log.add(new Object[]{cl,content});

        // Scroll to the (new) bottom if enabled
        if(autoscroll)
            ((ScrollView)findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
    }

    private void setTypeVisibility(char type, int visibility){
        ArrayList targetArrayList;
        switch (type) {
            case 'V': // Verbose
                targetArrayList = verboseEntries;
                break;
            case 'D': // Debug
                targetArrayList = debugEntries;
                break;
            case 'I': // Info
                targetArrayList = infoEntries;
                break;
            case 'W': // Warning
                targetArrayList = warnEntries;
                break;
            case 'E': // Error
                targetArrayList = errorEntries;
                break;
            case 'F': // Fatal
                targetArrayList = fatalEntries;
                break;
            default: // Others (e.g. "--- beginning of crash" reports)
                targetArrayList = otherEntries;
                break;
        }

        for (int i=0; i<targetArrayList.size();i++)
            ((ConstraintLayout)targetArrayList.get(i)).setVisibility(visibility);
    }

    /**
     * Converts a DP value to a rounded pixel value
     * @param dp DP value
     * @return Rounded pixel value
     */
    private int dpToPixel(int dp){
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}