package com.kdt.mcgui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;


import git.artdeell.mojo.R;

import fr.spse.extended_view.ExtendedButton;

public class LauncherMenuButton extends ExtendedButton {

    public LauncherMenuButton(@NonNull Context context) {
        super(context);
        setSettings();
    }
    public LauncherMenuButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setSettings();
    }


    /** Set style stuff */
    private void setSettings(){
        Resources resources = getContext().getResources();

        int padding = resources.getDimensionPixelSize(R.dimen.launcher_menu_button_padding);
        setCompoundDrawablePadding(padding);

        int iconSize = resources.getDimensionPixelSize(R.dimen._30sdp);
        int[] sizes = getExtendedViewData().getSizeCompounds();
        boolean stackedVertically = getCompoundDrawables()[1] != null;
        if (stackedVertically) {
            sizes[1] = iconSize;
            setPadding(padding, padding, padding, padding);
            setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        } else {
            sizes[0] = iconSize;
            setPaddingRelative(padding, 0, 0, 0);
            setGravity(Gravity.CENTER_VERTICAL);
        }
        getExtendedViewData().setSizeCompounds(sizes);

        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._12ssp));

        postProcessDrawables();
    }
}
