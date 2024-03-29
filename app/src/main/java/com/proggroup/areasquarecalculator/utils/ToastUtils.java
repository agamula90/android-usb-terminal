package com.proggroup.areasquarecalculator.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.R;

public class ToastUtils {
    private ToastUtils() {
    }

    public static void wrap(Toast toast) {
        View view = toast.getView();

        if (view != null) {
            TextView textView = view.findViewById(android.R.id.message);
            textView.setTextColor(Color.BLACK);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            view.setBackgroundResource(R.drawable.toast_drawable);
        }
        toast.setGravity(Gravity.CENTER, 0, 0);
    }
}
